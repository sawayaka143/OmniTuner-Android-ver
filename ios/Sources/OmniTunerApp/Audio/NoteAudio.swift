import AVFoundation
import Foundation
import OmniTunerCore

@MainActor
public final class NoteAudio {
    private static let outputSampleRate: Double = 48000.0
    private static let sampleDurationSeconds: Double = 1.6
    private static let synthDurationSeconds: Double = 0.55
    private static let voiceCount = 8

    private var engine: AVAudioEngine?
    private var players: [AVAudioPlayerNode] = []
    private var nextVoice = 0
    private let outputFormat = AVAudioFormat(standardFormatWithSampleRate: outputSampleRate, channels: 1)!
    private var decodedCache: [String: ParsedWav] = [:]

    public init() {}

    public func playChime() {
        let samples = NoteSynth.renderChime(sampleRate: Self.outputSampleRate)
        play(samples: samples)
    }

    public func playNote(midi: Int, durationSeconds: Double? = nil) {
        if let samples = renderGuitarSample(midi: midi, durationSeconds: durationSeconds ?? Self.sampleDurationSeconds) {
            play(samples: samples)
            return
        }
        let synth = NoteSynth.renderNote(
            midi: midi,
            sampleRate: Self.outputSampleRate,
            durationSeconds: durationSeconds ?? Self.synthDurationSeconds
        )
        play(samples: synth)
    }

    private func renderGuitarSample(midi: Int, durationSeconds: Double) -> [Float]? {
        guard let sample = nearestGuitarSample(midi: midi),
              let wav = loadWav(file: sample.file),
              wav.samples.count > 1 else {
            return nil
        }

        let rate = guitarSamplePlaybackRate(midi: midi, rootMidi: sample.rootMidi)
        let sourceRate = Double(wav.sampleRate)
        let step = (sourceRate * rate) / Self.outputSampleRate
        let maxOutFrames = Int(durationSeconds * Self.outputSampleRate)
        let availableOutFrames = Int(Double(wav.samples.count - 1) / step)
        let outCount = max(1, min(maxOutFrames, availableOutFrames))

        var out = [Float](repeating: 0.0, count: outCount)
        let src = wav.samples
        for i in 0..<outCount {
            let srcPos = Double(i) * step
            let idx = Int(srcPos)
            let frac = Float(srcPos - Double(idx))
            let a = src[idx]
            let b = idx + 1 < src.count ? src[idx + 1] : a
            out[i] = a + (b - a) * frac
        }

        // Gentle 12ms fade-out at end so truncated samples never click.
        let fadeFrames = min(outCount, Int(0.012 * Self.outputSampleRate))
        if fadeFrames > 1 {
            let start = outCount - fadeFrames
            for k in 0..<fadeFrames {
                let gain = Float(fadeFrames - 1 - k) / Float(fadeFrames)
                out[start + k] *= gain
            }
        }
        return out
    }

    private func loadWav(file: String) -> ParsedWav? {
        if let cached = decodedCache[file] { return cached }
        let stem = (file as NSString).deletingPathExtension
        let ext = (file as NSString).pathExtension
        guard let url = Bundle.module.url(forResource: stem, withExtension: ext)
            ?? Bundle.module.url(forResource: stem, withExtension: ext, subdirectory: "Resources/Audio")
            ?? Bundle.module.url(forResource: stem, withExtension: ext, subdirectory: "Audio"),
              let data = try? Data(contentsOf: url),
              let parsed = try? WavParser.parse(data) else {
            return nil
        }
        decodedCache[file] = parsed
        return parsed
    }

    private func ensureEngine() -> Bool {
        if let engine, engine.isRunning { return true }
        let engine = self.engine ?? AVAudioEngine()
        if players.isEmpty {
            let mainMixer = engine.mainMixerNode
            for _ in 0..<Self.voiceCount {
                let player = AVAudioPlayerNode()
                engine.attach(player)
                engine.connect(player, to: mainMixer, format: outputFormat)
                players.append(player)
            }
        }
        do {
            try engine.start()
            self.engine = engine
            return true
        } catch {
            return false
        }
    }

    private func play(samples: [Float]) {
        guard !samples.isEmpty else { return }
        if AVAudioSession.sharedInstance().category != .playAndRecord {
            AudioSessionCoordinator.configureForPlayback()
        }
        guard ensureEngine(), !players.isEmpty else { return }
        let frameCount = AVAudioFrameCount(samples.count)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: frameCount) else { return }
        buffer.frameLength = frameCount
        if let channel = buffer.floatChannelData?[0] {
            samples.withUnsafeBufferPointer { ptr in
                if let base = ptr.baseAddress {
                    channel.update(from: base, count: samples.count)
                }
            }
        }
        let player = players[nextVoice]
        nextVoice = (nextVoice + 1) % players.count
        player.stop()
        player.scheduleBuffer(buffer, at: nil, options: [], completionHandler: nil)
        player.play()
    }
}
