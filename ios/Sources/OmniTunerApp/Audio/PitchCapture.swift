import AVFoundation
import Foundation
import OmniTunerCore

public struct PitchFrame: Sendable {
    public let frequency: Double?
    public let inputLevel: Double
    public let tracking: PitchTrackingState

    public static let silent = PitchFrame(frequency: nil, inputLevel: 0.0, tracking: .idle)
}

public final class PitchCapture: @unchecked Sendable {
    private static let windowSize = 8192
    private static let tapBufferSize: AVAudioFrameCount = 2048
    private static let analysisIntervalS: TimeInterval = 0.045
    private static let highpassQA = 0.5412
    private static let highpassQB = 1.3066
    private static let lowpassHz = 1800.0
    private static let lowpassQ = 0.7

    private let lock = NSLock()
    private let sessionQueue = DispatchQueue(label: "com.omnituner.pitchcapture.session", qos: .userInitiated)
    private let engine = PitchEngine()
    private let highpassA = BiquadFilter()
    private let highpassB = BiquadFilter()
    private let lowpass = BiquadFilter()

    private var audioEngine: AVAudioEngine?
    private var lowestFrequency: Double?
    private var appliedLowest: Double?
    private var appliedSampleRate: Double = 48000.0
    private var running = false
    private var startGeneration: UInt64 = 0

    private var ring = [Float](repeating: 0.0, count: windowSize)
    private var ringWrite = 0
    private var ringFilled = 0
    private var lastAnalysisTime: TimeInterval = 0

    public init() {}

    public static func hasPermission() -> Bool {
        AVAudioApplication.shared.recordPermission == .granted
    }

    public static func requestPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    public func setLowestFrequency(_ hz: Double?) {
        lock.withLock {
            lowestFrequency = hz
        }
    }

    public func startAsync(
        onFrame: @escaping @Sendable (PitchFrame) -> Void,
        onStarted: @escaping @Sendable (String?) -> Void
    ) {
        let gen: UInt64 = lock.withLock {
            startGeneration &+= 1
            return startGeneration
        }
        sessionQueue.async { [weak self] in
            guard let self else { return }
            let error = self.startOnSessionQueue(generation: gen, onFrame: onFrame)
            onStarted(error)
        }
    }

    public func stopAsync() {
        let avEngine: AVAudioEngine? = lock.withLock {
            startGeneration &+= 1
            guard running else { return nil }
            running = false
            engine.stop()
            let active = audioEngine
            audioEngine = nil
            return active
        }
        if let avEngine {
            sessionQueue.async {
                avEngine.inputNode.removeTap(onBus: 0)
                avEngine.stop()
            }
        }
    }

    public func start(onFrame: @escaping @Sendable (PitchFrame) -> Void) -> String? {
        let gen: UInt64 = lock.withLock {
            startGeneration &+= 1
            return startGeneration
        }
        return startOnSessionQueue(generation: gen, onFrame: onFrame)
    }

    private func startOnSessionQueue(
        generation: UInt64,
        onFrame: @escaping @Sendable (PitchFrame) -> Void
    ) -> String? {
        let shouldProceed: Bool = lock.withLock {
            startGeneration == generation && !running
        }
        guard shouldProceed else { return nil }

        guard Self.hasPermission() else {
            return "Microphone permission is required."
        }

        AudioSessionCoordinator.configureForTunerCapture()

        let stillCurrentAfterSession: Bool = lock.withLock {
            startGeneration == generation && !running
        }
        guard stillCurrentAfterSession else { return nil }

        let avEngine = AVAudioEngine()
        let inputNode = avEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0, format.channelCount > 0 else {
            return "Microphone is unavailable."
        }

        let startedState: Bool = lock.withLock {
            guard startGeneration == generation, !running else { return false }
            appliedSampleRate = format.sampleRate
            appliedLowest = lowestFrequency
            applyFiltersLocked(sampleRate: format.sampleRate, lowest: lowestFrequency)
            highpassA.reset()
            highpassB.reset()
            lowpass.reset()
            ring = [Float](repeating: 0.0, count: Self.windowSize)
            ringWrite = 0
            ringFilled = 0
            lastAnalysisTime = 0
            engine.start()
            running = true
            audioEngine = avEngine
            return true
        }
        guard startedState else { return nil }

        Self.installAudioTap(
            on: inputNode,
            bufferSize: Self.tapBufferSize,
            format: format,
            capture: self,
            sampleRate: format.sampleRate,
            onFrame: onFrame
        )

        do {
            try avEngine.start()
        } catch {
            inputNode.removeTap(onBus: 0)
            lock.withLock {
                if audioEngine === avEngine {
                    running = false
                    audioEngine = nil
                    engine.stop()
                }
            }
            return "Microphone initialization failed."
        }

        let cancelledDuringStart: Bool = lock.withLock {
            guard startGeneration != generation else { return false }
            if audioEngine === avEngine {
                running = false
                audioEngine = nil
                engine.stop()
            }
            return true
        }
        if cancelledDuringStart {
            inputNode.removeTap(onBus: 0)
            avEngine.stop()
        }
        return nil
    }

    // Keep ObjC AVAudioNodeTapBlock construction in a strictly nonisolated helper
    // so Swift 6 never injects a MainActor queue check onto AVFAudio's tap queue.
    private nonisolated static func installAudioTap(
        on inputNode: AVAudioInputNode,
        bufferSize: AVAudioFrameCount,
        format: AVAudioFormat,
        capture: PitchCapture,
        sampleRate: Double,
        onFrame: @escaping @Sendable (PitchFrame) -> Void
    ) {
        inputNode.installTap(onBus: 0, bufferSize: bufferSize, format: format) { [weak capture] buffer, _ in
            capture?.processTapBuffer(buffer, sampleRate: sampleRate, onFrame: onFrame)
        }
    }

    public func stop() {
        let avEngine: AVAudioEngine? = lock.withLock {
            startGeneration &+= 1
            guard running else { return nil }
            running = false
            engine.stop()
            let active = audioEngine
            audioEngine = nil
            return active
        }
        avEngine?.inputNode.removeTap(onBus: 0)
        avEngine?.stop()
    }

    private func applyFiltersLocked(sampleRate: Double, lowest: Double?) {
        let cutoff = PitchDetector.highpassHzFor(lowestHz: lowest)
        highpassA.setHighpassLinear(sampleRate: sampleRate, frequency: cutoff, q: Self.highpassQA)
        highpassB.setHighpassLinear(sampleRate: sampleRate, frequency: cutoff, q: Self.highpassQB)
        lowpass.setLowpassLinear(sampleRate: sampleRate, frequency: Self.lowpassHz, q: Self.lowpassQ)
    }

    private func processTapBuffer(
        _ buffer: AVAudioPCMBuffer,
        sampleRate: Double,
        onFrame: @escaping @Sendable (PitchFrame) -> Void
    ) {
        let frameLength = Int(buffer.frameLength)
        guard frameLength > 0, let channel = buffer.floatChannelData?[0] else { return }

        var chunk = Array(UnsafeBufferPointer(start: channel, count: frameLength))
        var filtered = [Float](repeating: 0.0, count: frameLength)

        var emittedFrame: PitchFrame?

        lock.lock()
        guard running else {
            lock.unlock()
            return
        }

        if lowestFrequency != appliedLowest || sampleRate != appliedSampleRate {
            appliedLowest = lowestFrequency
            appliedSampleRate = sampleRate
            applyFiltersLocked(sampleRate: sampleRate, lowest: lowestFrequency)
        }

        highpassA.process(chunk, output: &filtered, count: frameLength)
        highpassB.process(filtered, output: &chunk, count: frameLength)
        lowpass.process(chunk, output: &filtered, count: frameLength)

        for i in 0..<frameLength {
            ring[ringWrite] = filtered[i]
            ringWrite = (ringWrite + 1) % Self.windowSize
        }
        ringFilled = min(Self.windowSize, ringFilled + frameLength)

        let now = ProcessInfo.processInfo.systemUptime
        if ringFilled == Self.windowSize && now - lastAnalysisTime >= Self.analysisIntervalS {
            var window = [Float](repeating: 0.0, count: Self.windowSize)
            for i in 0..<Self.windowSize {
                window[i] = ring[(ringWrite + i) % Self.windowSize]
            }
            let estimate = engine.process(
                window: &window,
                sampleRate: sampleRate,
                minFrequency: PitchDetector.minFrequencyFor(lowestHz: lowestFrequency)
            )
            emittedFrame = PitchFrame(
                frequency: engine.frequency,
                inputLevel: estimate.inputLevel,
                tracking: engine.trackingState
            )
            lastAnalysisTime = now
        }
        lock.unlock()

        if let emittedFrame {
            onFrame(emittedFrame)
        }
    }
}
