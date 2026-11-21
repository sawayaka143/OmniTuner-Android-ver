import Foundation

public enum NoteSynth {
    public static let attackSeconds = 0.012
    public static let peak = 0.22
    public static let minDuration = 0.08
    public static let lowpassHz = 2600.0
    public static let stopTailSeconds = 0.05

    public static func midiToFrequency(_ midi: Int, ref: Double = 440.0) -> Double {
        ref * pow(2.0, Double(midi - 69) / 12.0)
    }

    public static func noteLengthSeconds(_ durationSeconds: Double) -> Double {
        max(minDuration, durationSeconds) + stopTailSeconds
    }

    public static func renderNote(
        midi: Int,
        sampleRate: Double,
        durationSeconds: Double = 0.55,
        ref: Double = 440.0
    ) -> [Float] {
        let duration = max(minDuration, durationSeconds)
        return renderTone(
            frequency: midiToFrequency(midi, ref: ref),
            sampleRate: sampleRate,
            attack: attackSeconds,
            peak: peak,
            duration: duration,
            lowpassHz: lowpassHz
        )
    }

    public struct ChimeTone: Sendable, Hashable {
        public let frequency: Double
        public let startSeconds: Double
        public let duration: Double
        public let peak: Double
    }

    public static func chimeTones(ref: Double = 440.0) -> [ChimeTone] {
        [
            ChimeTone(frequency: midiToFrequency(69, ref: ref), startSeconds: 0.0, duration: 0.12, peak: 0.65),
            ChimeTone(frequency: midiToFrequency(76, ref: ref), startSeconds: 0.06, duration: 0.08, peak: 0.7),
        ]
    }

    public static func chimeLengthSeconds() -> Double {
        chimeTones().map { $0.startSeconds + $0.duration + stopTailSeconds }.max() ?? stopTailSeconds
    }

    public static func renderChime(sampleRate: Double, ref: Double = 440.0) -> [Float] {
        let tones = chimeTones(ref: ref)
        let total = max(1, Int(ceil(chimeLengthSeconds() * sampleRate)))
        var out = [Double](repeating: 0.0, count: total)
        for tone in tones {
            let buffer = renderPlainTone(
                frequency: tone.frequency,
                sampleRate: sampleRate,
                peak: tone.peak,
                duration: tone.duration
            )
            let offset = Int(tone.startSeconds * sampleRate)
            for i in buffer.indices {
                let index = offset + i
                if index < total {
                    out[index] += buffer[i]
                }
            }
        }
        return out.map { Float($0) }
    }

    private static func renderPlainTone(
        frequency: Double,
        sampleRate: Double,
        peak: Double,
        duration: Double
    ) -> [Double] {
        let n = max(1, Int(ceil((duration + stopTailSeconds) * sampleRate)))
        var out = [Double](repeating: 0.0, count: n)
        let attack = 0.014
        var phase = 0.0
        for i in 0..<n {
            let t = Double(i) / sampleRate
            phase += 2.0 * .pi * frequency / sampleRate
            let env: Double
            if t < attack {
                env = 0.0001 + (peak - 0.0001) * (t / attack)
            } else if t < duration {
                let progress = (t - attack) / max(1e-9, duration - attack)
                env = peak * pow(0.0001 / peak, progress)
            } else {
                env = 0.0001
            }
            out[i] = (2.0 / .pi * asin(sin(phase))) * env
        }
        return out
    }

    private static func renderTone(
        frequency: Double,
        sampleRate: Double,
        attack: Double,
        peak: Double,
        duration: Double,
        lowpassHz: Double
    ) -> [Float] {
        let n = max(1, Int(ceil(noteLengthSeconds(duration) * sampleRate)))
        let filter = BiquadFilter()
        filter.setLowpass(sampleRate: sampleRate, frequency: lowpassHz, qDb: 0.7)
        var out = [Float](repeating: 0.0, count: n)
        var phase = 0.0
        var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0
        for i in 0..<n {
            let t = Double(i) / sampleRate
            phase += 2.0 * .pi * frequency / sampleRate
            let x0 = 2.0 / .pi * asin(sin(phase))
            let y0 = filter.b0 * x0 + filter.b1 * x1 + filter.b2 * x2 - filter.a1 * y1 - filter.a2 * y2
            let env: Double
            if t < attack {
                env = 0.0001 + (peak - 0.0001) * (t / attack)
            } else if t < duration {
                env = peak * pow(0.0001 / peak, (t - attack) / (duration - attack))
            } else {
                env = 0.0001
            }
            out[i] = Float(y0 * env)
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }
        return out
    }
}
