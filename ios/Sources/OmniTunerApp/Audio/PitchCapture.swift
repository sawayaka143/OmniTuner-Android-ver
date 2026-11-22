import AVFoundation
import Foundation
import OmniTunerCore
import Synchronization

public struct PitchFrame: Sendable {
    public let frequency: Double?
    public let inputLevel: Double
    public let tracking: PitchTrackingState

    public static let silent = PitchFrame(frequency: nil, inputLevel: 0.0, tracking: .idle)
}

private final class SpscFloatRing: @unchecked Sendable {
    static let capacity = 32768
    private static let mask = capacity - 1

    private let buffer: UnsafeMutablePointer<Float>
    private let readIndex = Atomic<Int>(0)
    private let writeIndex = Atomic<Int>(0)

    init() {
        buffer = .allocate(capacity: Self.capacity)
        buffer.initialize(repeating: 0, count: Self.capacity)
    }

    deinit {
        buffer.deinitialize(count: Self.capacity)
        buffer.deallocate()
    }

    func reset() {
        readIndex.store(0, ordering: .relaxed)
        writeIndex.store(0, ordering: .relaxed)
    }

    func write(from source: UnsafePointer<Float>, count: Int, stride: Int = 1) {
        guard count > 0 else { return }
        let read = readIndex.load(ordering: .acquiring)
        let write = writeIndex.load(ordering: .relaxed)
        let available = Self.capacity - (write &- read)
        let toWrite = min(count, available)
        guard toWrite > 0 else { return }

        let start = write & Self.mask
        let firstPart = min(toWrite, Self.capacity - start)
        if stride == 1 {
            (buffer + start).update(from: source, count: firstPart)
            let secondPart = toWrite - firstPart
            if secondPart > 0 {
                buffer.update(from: source + firstPart, count: secondPart)
            }
        } else {
            for i in 0..<firstPart {
                buffer[start + i] = source[i * stride]
            }
            let secondPart = toWrite - firstPart
            for i in 0..<secondPart {
                buffer[i] = source[(firstPart + i) * stride]
            }
        }

        writeIndex.store(write &+ toWrite, ordering: .releasing)
    }

    func drain(into destination: inout [Float], maxCount: Int) -> Int {
        guard maxCount > 0 else { return 0 }
        let write = writeIndex.load(ordering: .acquiring)
        let read = readIndex.load(ordering: .relaxed)
        let available = write &- read
        let toRead = min(min(maxCount, destination.count), available)
        guard toRead > 0 else { return 0 }

        let start = read & Self.mask
        let firstPart = min(toRead, Self.capacity - start)
        destination.withUnsafeMutableBufferPointer { dst in
            guard let base = dst.baseAddress else { return }
            base.update(from: buffer + start, count: firstPart)
            let secondPart = toRead - firstPart
            if secondPart > 0 {
                (base + firstPart).update(from: buffer, count: secondPart)
            }
        }

        readIndex.store(read &+ toRead, ordering: .releasing)
        return toRead
    }
}

public final class PitchCapture: @unchecked Sendable {
    private static let windowSize = 8192
    private static let chunkCapacity = 4096
    private static let analysisIntervalS: TimeInterval = 0.040
    private static let minHopSamples = 512
    private static let highpassQA = 0.5412
    private static let highpassQB = 1.3066
    private static let lowpassHz = 1800.0
    private static let lowpassQ = 0.7

    private let lock = NSLock()
    private let sessionQueue = DispatchQueue(label: "com.omnituner.pitchcapture.session", qos: .userInitiated)
    private let analysisQueue = DispatchQueue(label: "com.omnituner.pitchcapture.analysis", qos: .userInteractive)
    private let ring = SpscFloatRing()
    private let wakeSource: DispatchSourceUserDataAdd

    // Protected by `lock`
    private var audioEngine: AVAudioEngine?
    private var sinkNode: AVAudioSinkNode?
    private var lowestFrequency: Double?
    private var desiredRunning = false
    private var running = false
    private var startGeneration: UInt64 = 0
    private var storedOnFrame: (@Sendable (PitchFrame) -> Void)?
    private var lastSessionConfigureUptime: TimeInterval = 0
    private var sessionObservers: [NSObjectProtocol] = []
    private var engineConfigObserver: NSObjectProtocol?

    // Accessed strictly on `analysisQueue`
    private let engine = PitchEngine()
    private let highpassA = BiquadFilter()
    private let highpassB = BiquadFilter()
    private let lowpass = BiquadFilter()
    private var appliedLowest: Double?
    private var appliedSampleRate: Double = 48000.0
    private var analysisSampleRate: Double = 48000.0
    private var analysisHopSamples: Int = 1920
    private var activeAnalysisGeneration: UInt64 = 0
    private var onFrameHandler: (@Sendable (PitchFrame) -> Void)?
    private var analysisRing = [Float](repeating: 0.0, count: windowSize)
    private var analysisRingWrite = 0
    private var analysisRingFilled = 0
    private var samplesSinceAnalysis = 0
    private var analysisChunk = [Float](repeating: 0.0, count: chunkCapacity)
    private var analysisFiltered = [Float](repeating: 0.0, count: chunkCapacity)
    private var analysisWindow = [Float](repeating: 0.0, count: windowSize)

    public init() {
        let source = DispatchSource.makeUserDataAddSource(queue: analysisQueue)
        self.wakeSource = source
        source.setEventHandler { [weak self] in
            self?.drainAndAnalyse()
        }
        source.resume()
    }

    deinit {
        removeAllObserversLocked()
        wakeSource.cancel()
    }

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
            desiredRunning = true
            storedOnFrame = onFrame
            startGeneration &+= 1
            return startGeneration
        }
        registerSessionObserversIfNeeded()
        sessionQueue.async { [weak self] in
            guard let self else { return }
            let error = self.startOnSessionQueue(generation: gen, onFrame: onFrame)
            onStarted(error)
        }
    }

    public func stopAsync() {
        let teardown: (AVAudioEngine?, AVAudioSinkNode?) = lock.withLock {
            desiredRunning = false
            storedOnFrame = nil
            startGeneration &+= 1
            let activeEngine = audioEngine
            let activeSink = sinkNode
            running = false
            audioEngine = nil
            sinkNode = nil
            removeAllObserversLocked()
            return (activeEngine, activeSink)
        }
        analysisQueue.async { [weak self] in
            guard let self else { return }
            self.activeAnalysisGeneration &+= 1
            self.onFrameHandler = nil
            self.engine.stop()
        }
        if let activeEngine = teardown.0 {
            sessionQueue.async {
                Self.teardownEngine(activeEngine, sinkNode: teardown.1)
            }
        }
    }

    public func start(onFrame: @escaping @Sendable (PitchFrame) -> Void) -> String? {
        let gen: UInt64 = lock.withLock {
            desiredRunning = true
            storedOnFrame = onFrame
            startGeneration &+= 1
            return startGeneration
        }
        registerSessionObserversIfNeeded()
        return startOnSessionQueue(generation: gen, onFrame: onFrame)
    }

    public func stop() {
        let teardown: (AVAudioEngine?, AVAudioSinkNode?) = lock.withLock {
            desiredRunning = false
            storedOnFrame = nil
            startGeneration &+= 1
            let activeEngine = audioEngine
            let activeSink = sinkNode
            running = false
            audioEngine = nil
            sinkNode = nil
            removeAllObserversLocked()
            return (activeEngine, activeSink)
        }
        analysisQueue.sync {
            activeAnalysisGeneration &+= 1
            onFrameHandler = nil
            engine.stop()
        }
        if let activeEngine = teardown.0 {
            Self.teardownEngine(activeEngine, sinkNode: teardown.1)
        }
    }

    private func startOnSessionQueue(
        generation: UInt64,
        onFrame: @escaping @Sendable (PitchFrame) -> Void
    ) -> String? {
        let shouldProceed: Bool = lock.withLock {
            desiredRunning && startGeneration == generation && !running
        }
        guard shouldProceed else { return nil }

        guard Self.hasPermission() else {
            return "Microphone permission is required."
        }

        lock.withLock {
            lastSessionConfigureUptime = ProcessInfo.processInfo.systemUptime
        }
        AudioSessionCoordinator.configureForTunerCapture()

        let stillCurrentAfterSession: Bool = lock.withLock {
            desiredRunning && startGeneration == generation && !running
        }
        guard stillCurrentAfterSession else { return nil }

        let avEngine = AVAudioEngine()
        let inputNode = avEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0,
              format.channelCount > 0,
              format.commonFormat == .pcmFormatFloat32 else {
            return "Microphone is unavailable."
        }

        let sampleRate = format.sampleRate
        let hopSamples = max(Self.minHopSamples, Int((sampleRate * Self.analysisIntervalS).rounded()))
        let channelStride = format.isInterleaved ? Int(format.channelCount) : 1
        let currentLowest = lock.withLock { lowestFrequency }

        let preparedAnalysis = analysisQueue.sync { () -> Bool in
            let current: Bool = lock.withLock {
                desiredRunning && startGeneration == generation && !running
            }
            guard current else { return false }

            ring.reset()
            activeAnalysisGeneration = generation
            onFrameHandler = onFrame
            analysisSampleRate = sampleRate
            analysisHopSamples = hopSamples
            appliedSampleRate = sampleRate
            appliedLowest = currentLowest
            applyFiltersOnAnalysisQueue(sampleRate: sampleRate, lowest: currentLowest)
            highpassA.reset()
            highpassB.reset()
            lowpass.reset()
            analysisRing.withUnsafeMutableBufferPointer { $0.initialize(repeating: 0.0) }
            analysisRingWrite = 0
            analysisRingFilled = 0
            samplesSinceAnalysis = 0
            engine.start()
            return true
        }
        guard preparedAnalysis else { return nil }

        let sink = Self.makeSinkNode(ring: ring, stride: channelStride, wakeSource: wakeSource)
        avEngine.attach(sink)
        avEngine.connect(inputNode, to: sink, format: nil)
        avEngine.prepare()

        let committed: Bool = lock.withLock {
            guard desiredRunning, startGeneration == generation, !running else { return false }
            running = true
            audioEngine = avEngine
            sinkNode = sink
            if let existing = engineConfigObserver {
                NotificationCenter.default.removeObserver(existing)
            }
            engineConfigObserver = NotificationCenter.default.addObserver(
                forName: .AVAudioEngineConfigurationChange,
                object: avEngine,
                queue: nil
            ) { [weak self] _ in
                self?.handleEngineConfigurationChange(for: avEngine)
            }
            return true
        }
        guard committed else {
            Self.teardownEngine(avEngine, sinkNode: sink)
            return nil
        }

        do {
            try avEngine.start()
        } catch {
            lock.withLock {
                if audioEngine === avEngine {
                    running = false
                    audioEngine = nil
                    sinkNode = nil
                    if let existing = engineConfigObserver {
                        NotificationCenter.default.removeObserver(existing)
                        engineConfigObserver = nil
                    }
                }
            }
            analysisQueue.async { [weak self] in
                guard let self, self.activeAnalysisGeneration == generation else { return }
                self.onFrameHandler = nil
                self.engine.stop()
            }
            Self.teardownEngine(avEngine, sinkNode: sink)
            return "Microphone initialization failed."
        }

        let cancelledDuringStart: Bool = lock.withLock {
            guard !desiredRunning || startGeneration != generation else { return false }
            if audioEngine === avEngine {
                running = false
                audioEngine = nil
                sinkNode = nil
                if let existing = engineConfigObserver {
                    NotificationCenter.default.removeObserver(existing)
                    engineConfigObserver = nil
                }
            }
            return true
        }
        if cancelledDuringStart {
            analysisQueue.async { [weak self] in
                guard let self, self.activeAnalysisGeneration == generation else { return }
                self.onFrameHandler = nil
                self.engine.stop()
            }
            Self.teardownEngine(avEngine, sinkNode: sink)
        }
        return nil
    }

    // Keep AVAudioSinkNode block creation in a strictly nonisolated helper so Swift 6
    // never injects a MainActor isolation check onto CoreAudio's real-time render thread.
    private nonisolated static func makeSinkNode(
        ring: SpscFloatRing,
        stride: Int,
        wakeSource: DispatchSourceUserDataAdd
    ) -> AVAudioSinkNode {
        AVAudioSinkNode { _, frameCount, audioBufferList in
            let count = Int(frameCount)
            guard count > 0 else { return noErr }
            let abl = UnsafeMutableAudioBufferListPointer(UnsafeMutablePointer(mutating: audioBufferList))
            guard let first = abl.first, let mData = first.mData else { return noErr }
            let floatCapacity = Int(first.mDataByteSize) / MemoryLayout<Float>.stride
            let availableFrames = stride > 1 ? floatCapacity / stride : floatCapacity
            let sampleCount = min(count, availableFrames)
            guard sampleCount > 0 else { return noErr }
            ring.write(from: mData.assumingMemoryBound(to: Float.self), count: sampleCount, stride: stride)
            wakeSource.add(data: 1)
            return noErr
        }
    }

    private static func teardownEngine(_ avEngine: AVAudioEngine, sinkNode: AVAudioSinkNode?) {
        avEngine.stop()
        if let sinkNode {
            avEngine.detach(sinkNode)
        }
    }

    // MARK: - Analysis Queue

    private func applyFiltersOnAnalysisQueue(sampleRate: Double, lowest: Double?) {
        let cutoff = PitchDetector.highpassHzFor(lowestHz: lowest)
        highpassA.setHighpassLinear(sampleRate: sampleRate, frequency: cutoff, q: Self.highpassQA)
        highpassB.setHighpassLinear(sampleRate: sampleRate, frequency: cutoff, q: Self.highpassQB)
        lowpass.setLowpassLinear(sampleRate: sampleRate, frequency: Self.lowpassHz, q: Self.lowpassQ)
    }

    private func drainAndAnalyse() {
        let snapshot: (isRunning: Bool, generation: UInt64, lowest: Double?) = lock.withLock {
            (running && desiredRunning, startGeneration, lowestFrequency)
        }
        guard snapshot.isRunning,
              snapshot.generation == activeAnalysisGeneration,
              let handler = onFrameHandler else {
            return
        }

        if snapshot.lowest != appliedLowest || analysisSampleRate != appliedSampleRate {
            appliedLowest = snapshot.lowest
            appliedSampleRate = analysisSampleRate
            applyFiltersOnAnalysisQueue(sampleRate: analysisSampleRate, lowest: snapshot.lowest)
        }

        while true {
            let readCount = ring.drain(into: &analysisChunk, maxCount: Self.chunkCapacity)
            if readCount <= 0 { break }

            highpassA.process(analysisChunk, output: &analysisFiltered, count: readCount)
            highpassB.process(analysisFiltered, output: &analysisChunk, count: readCount)
            lowpass.process(analysisChunk, output: &analysisFiltered, count: readCount)

            let firstPart = min(readCount, Self.windowSize - analysisRingWrite)
            analysisRing.withUnsafeMutableBufferPointer { dst in
                guard let dstBase = dst.baseAddress else { return }
                analysisFiltered.withUnsafeBufferPointer { src in
                    guard let srcBase = src.baseAddress else { return }
                    (dstBase + analysisRingWrite).update(from: srcBase, count: firstPart)
                    let secondPart = readCount - firstPart
                    if secondPart > 0 {
                        dstBase.update(from: srcBase + firstPart, count: secondPart)
                    }
                }
            }
            analysisRingWrite = (analysisRingWrite + readCount) % Self.windowSize
            analysisRingFilled = min(Self.windowSize, analysisRingFilled + readCount)
            samplesSinceAnalysis += readCount
        }

        guard analysisRingFilled == Self.windowSize,
              samplesSinceAnalysis >= analysisHopSamples else {
            return
        }

        let tailCount = Self.windowSize - analysisRingWrite
        analysisWindow.withUnsafeMutableBufferPointer { dst in
            guard let dstBase = dst.baseAddress else { return }
            analysisRing.withUnsafeBufferPointer { src in
                guard let srcBase = src.baseAddress else { return }
                dstBase.update(from: srcBase + analysisRingWrite, count: tailCount)
                if analysisRingWrite > 0 {
                    (dstBase + tailCount).update(from: srcBase, count: analysisRingWrite)
                }
            }
        }

        samplesSinceAnalysis = 0
        let estimate = engine.process(
            window: &analysisWindow,
            sampleRate: analysisSampleRate,
            minFrequency: PitchDetector.minFrequencyFor(lowestHz: snapshot.lowest)
        )
        let frame = PitchFrame(
            frequency: engine.frequency,
            inputLevel: estimate.inputLevel,
            tracking: engine.trackingState
        )

        let stillValid: Bool = lock.withLock {
            running && desiredRunning && startGeneration == snapshot.generation
        }
        if stillValid {
            handler(frame)
        }
    }

    // MARK: - Interruption & Route Recovery

    private func registerSessionObserversIfNeeded() {
        lock.withLock {
            guard sessionObservers.isEmpty else { return }
            let center = NotificationCenter.default
            let interruption = center.addObserver(
                forName: AVAudioSession.interruptionNotification,
                object: AVAudioSession.sharedInstance(),
                queue: nil
            ) { [weak self] notification in
                self?.handleInterruption(notification)
            }
            let routeChange = center.addObserver(
                forName: AVAudioSession.routeChangeNotification,
                object: AVAudioSession.sharedInstance(),
                queue: nil
            ) { [weak self] notification in
                self?.handleRouteChange(notification)
            }
            sessionObservers = [interruption, routeChange]
        }
    }

    private func removeAllObserversLocked() {
        let center = NotificationCenter.default
        for observer in sessionObservers {
            center.removeObserver(observer)
        }
        sessionObservers.removeAll()
        if let engineConfigObserver {
            center.removeObserver(engineConfigObserver)
            self.engineConfigObserver = nil
        }
    }

    private func handleInterruption(_ notification: Notification) {
        guard let rawType = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: rawType) else {
            return
        }

        switch type {
        case .began:
            pauseActiveEngineForRecovery()
        case .ended:
            scheduleRestartIfDesired()
        @unknown default:
            break
        }
    }

    private func handleRouteChange(_ notification: Notification) {
        guard let rawReason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: rawReason) else {
            return
        }

        switch reason {
        case .newDeviceAvailable, .oldDeviceUnavailable, .override, .wakeFromSleep:
            scheduleRestartIfDesired()
        case .categoryChange:
            let elapsedSinceConfigure = lock.withLock {
                ProcessInfo.processInfo.systemUptime - lastSessionConfigureUptime
            }
            if elapsedSinceConfigure > 0.25 {
                scheduleRestartIfDesired()
            }
        default:
            break
        }
    }

    private func handleEngineConfigurationChange(for engine: AVAudioEngine) {
        let isCurrentEngine: Bool = lock.withLock {
            desiredRunning && audioEngine === engine
        }
        guard isCurrentEngine else { return }
        scheduleRestartIfDesired()
    }

    private func pauseActiveEngineForRecovery() {
        let active: (AVAudioEngine?, AVAudioSinkNode?) = lock.withLock {
            guard desiredRunning else { return (nil, nil) }
            startGeneration &+= 1
            let engine = audioEngine
            let sink = sinkNode
            running = false
            audioEngine = nil
            sinkNode = nil
            if let existing = engineConfigObserver {
                NotificationCenter.default.removeObserver(existing)
                engineConfigObserver = nil
            }
            return (engine, sink)
        }
        analysisQueue.async { [weak self] in
            guard let self else { return }
            self.activeAnalysisGeneration &+= 1
            self.engine.stop()
        }
        if let activeEngine = active.0 {
            sessionQueue.async {
                Self.teardownEngine(activeEngine, sinkNode: active.1)
            }
        }
    }

    private func scheduleRestartIfDesired() {
        let request: (gen: UInt64, onFrame: (@Sendable (PitchFrame) -> Void)?, activeEngine: AVAudioEngine?, activeSink: AVAudioSinkNode?) = lock.withLock {
            guard desiredRunning, let onFrame = storedOnFrame else {
                return (0, nil, nil, nil)
            }
            startGeneration &+= 1
            let gen = startGeneration
            let engine = audioEngine
            let sink = sinkNode
            running = false
            audioEngine = nil
            sinkNode = nil
            if let existing = engineConfigObserver {
                NotificationCenter.default.removeObserver(existing)
                engineConfigObserver = nil
            }
            return (gen, onFrame, engine, sink)
        }
        guard let onFrame = request.onFrame else { return }

        sessionQueue.async { [weak self] in
            if let activeEngine = request.activeEngine {
                Self.teardownEngine(activeEngine, sinkNode: request.activeSink)
            }
            guard let self else { return }
            _ = self.startOnSessionQueue(generation: request.gen, onFrame: onFrame)
        }
    }
}
