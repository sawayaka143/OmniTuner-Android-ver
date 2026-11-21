import AVFoundation
import Foundation
import OmniTunerCore

public final class MetronomeAudioEngine: @unchecked Sendable {
    private static let sampleRate: Double = 48000.0
    private static let transportEmitIntervalS: Double = 0.04

    public struct TransportUpdate: Sendable {
        public let barIndex: Int
        public let patternPos: Int
        public let progress: Double
        public let countIn: Bool
        public let barActive: Bool
        public let pulsesPerBar: Int
        public let pulsesPerBeat: Int
    }

    private let lock = NSLock()
    private let compressor = DynamicsCompressor(sampleRate: sampleRate)
    private var clockSecondsValue: Double = 0.0
    private var pendingMix: [(startFrame: Int64, samples: [Float])] = []
    private var sampleCursor: Int64 = 0
    private var lastTransportEmit: Double = -1.0
    private var isBackground = false
    private var running = false

    private var audioEngine: AVAudioEngine?
    private var sourceNode: AVAudioSourceNode?
    private var previewEngine: AVAudioEngine?
    private var previewPlayer: AVAudioPlayerNode?
    private var transportHandler: (@Sendable (TransportUpdate) -> Void)?

    public init() {}

    // Separate scheduler wrapper because clock closure captures self weakly/safely under lock.
    private var activeScheduler: MetronomeScheduler?

    public func setTransportHandler(_ handler: (@Sendable (TransportUpdate) -> Void)?) {
        lock.withLock { transportHandler = handler }
    }

    public func configure(_ state: MetronomeState) {
        lock.withLock {
            ensureScheduler().configure(state)
        }
    }

    public func setBackground(_ value: Bool) {
        lock.withLock { isBackground = value }
    }

    public func start(with state: MetronomeState) {
        let alreadyRunning = lock.withLock { running }
        if alreadyRunning { return }

        AudioSessionCoordinator.configureForPlayback()

        let engine = AVAudioEngine()
        let format = AVAudioFormat(standardFormatWithSampleRate: Self.sampleRate, channels: 1)!

        lock.withLock {
            let sched = ensureScheduler()
            sched.configure(state)
            compressor.reset()
            pendingMix.removeAll()
            sampleCursor = 0
            clockSecondsValue = 0.0
            lastTransportEmit = -1.0
            sched.start(now: 0.0)
            running = true
        }

        let node = Self.makeSourceNode(format: format, engine: self)
        engine.attach(node)
        engine.connect(node, to: engine.mainMixerNode, format: format)

        do {
            try engine.start()
            lock.withLock {
                self.audioEngine = engine
                self.sourceNode = node
            }
        } catch {
            lock.withLock {
                running = false
                activeScheduler?.stop()
            }
        }
    }

    // Keep ObjC render block construction strictly nonisolated so Swift 6 never emits
    // a MainActor executor check on CoreAudio's real-time IO thread.
    private nonisolated static func makeSourceNode(
        format: AVAudioFormat,
        engine: MetronomeAudioEngine
    ) -> AVAudioSourceNode {
        AVAudioSourceNode(format: format) { [weak engine] _, _, frameCount, audioBufferList -> OSStatus in
            guard let engine else { return noErr }
            engine.render(frameCount: Int(frameCount), audioBufferList: audioBufferList)
            return noErr
        }
    }

    public func stop() {
        let engineToStop: AVAudioEngine? = lock.withLock {
            guard running else { return nil }
            running = false
            activeScheduler?.stop()
            pendingMix.removeAll()
            compressor.reset()
            let e = audioEngine
            audioEngine = nil
            sourceNode = nil
            return e
        }
        engineToStop?.stop()
    }

    public func previewVoice(id: String, vol: Double, noteAudio: NoteAudio) {
        var samples = MetronomeVoices.render(id: id, sampleRate: Self.sampleRate, velocity: vol)
        DynamicsCompressor(sampleRate: Self.sampleRate).processInPlace(&samples)
        playPreview(samples: samples)
    }

    private func playPreview(samples: [Float]) {
        guard !samples.isEmpty else { return }
        AudioSessionCoordinator.configureForPlayback()
        let format = AVAudioFormat(standardFormatWithSampleRate: Self.sampleRate, channels: 1)!
        let engine = previewEngine ?? AVAudioEngine()
        let player: AVAudioPlayerNode
        if let existing = previewPlayer, existing.engine === engine {
            player = existing
        } else {
            let created = AVAudioPlayerNode()
            engine.attach(created)
            engine.connect(created, to: engine.mainMixerNode, format: format)
            previewPlayer = created
            player = created
        }
        if !engine.isRunning {
            try? engine.start()
            previewEngine = engine
        }
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count)) else { return }
        buffer.frameLength = AVAudioFrameCount(samples.count)
        if let ch = buffer.floatChannelData?[0] {
            samples.withUnsafeBufferPointer { ptr in
                if let base = ptr.baseAddress { ch.update(from: base, count: samples.count) }
            }
        }
        player.stop()
        player.scheduleBuffer(buffer, at: nil, options: [])
        player.play()
    }

    private func ensureScheduler() -> MetronomeScheduler {
        if let activeScheduler { return activeScheduler }
        let created = MetronomeScheduler { [weak self] in
            self?.clockSecondsValue ?? 0.0
        }
        activeScheduler = created
        return created
    }

    private func render(frameCount: Int, audioBufferList: UnsafeMutablePointer<AudioBufferList>) {
        let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
        guard let firstBuffer = buffers.first,
              let outPtr = firstBuffer.mData?.assumingMemoryBound(to: Float.self) else {
            return
        }

        var chunk = [Float](repeating: 0.0, count: frameCount)
        var transportToEmit: TransportUpdate?
        var handler: (@Sendable (TransportUpdate) -> Void)?

        lock.lock()
        guard running, let sched = activeScheduler else {
            lock.unlock()
            for i in 0..<frameCount { outPtr[i] = 0 }
            return
        }

        let startFrame = sampleCursor
        let now = Double(startFrame) / Self.sampleRate
        clockSecondsValue = now
        let lookahead = isBackground ? MetronomeScheduler.lookaheadHiddenS : MetronomeScheduler.lookaheadVisibleS

        for event in sched.tick(lookaheadSeconds: lookahead, now: now) {
            let eventFrame = Int64((event.time * Self.sampleRate).rounded())
            let pcm = MetronomeVoices.render(id: event.id, sampleRate: Self.sampleRate, velocity: event.vol)
            pendingMix.append((startFrame: eventFrame, samples: pcm))
        }

        let endFrame = startFrame + Int64(frameCount)
        for item in pendingMix {
            let overlapStart = max(item.startFrame, startFrame)
            let overlapEnd = min(item.startFrame + Int64(item.samples.count), endFrame)
            if overlapEnd <= overlapStart { continue }
            for f in overlapStart..<overlapEnd {
                chunk[Int(f - startFrame)] += item.samples[Int(f - item.startFrame)]
            }
        }

        compressor.processInPlace(&chunk, count: frameCount)
        let gain = Float(sched.config.masterVol)
        if gain != 1.0 {
            for i in 0..<frameCount {
                outPtr[i] = chunk[i] * gain
            }
        } else {
            for i in 0..<frameCount {
                outPtr[i] = chunk[i]
            }
        }

        sampleCursor = endFrame
        pendingMix.removeAll { $0.startFrame + Int64($0.samples.count) <= sampleCursor }

        if let currentHandler = transportHandler,
           lastTransportEmit < 0 || now - lastTransportEmit >= Self.transportEmitIntervalS {
            lastTransportEmit = now
            if let snap = sched.getTransport(now: now) {
                handler = currentHandler
                transportToEmit = TransportUpdate(
                    barIndex: snap.barIndex,
                    patternPos: snap.patternPos,
                    progress: snap.progress,
                    countIn: snap.countIn,
                    barActive: snap.barActive,
                    pulsesPerBar: snap.pulsesPerBar,
                    pulsesPerBeat: snap.pulsesPerBeat
                )
            }
        }
        lock.unlock()

        if let handler, let transportToEmit {
            handler(transportToEmit)
        }
    }
}
