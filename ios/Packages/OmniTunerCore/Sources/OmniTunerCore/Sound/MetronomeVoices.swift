import Foundation

public struct MetronomeVoiceOption: Sendable, Hashable, Identifiable {
    public let id: String
    public let label: String

    public init(id: String, label: String) {
        self.id = id
        self.label = label
    }
}

public enum MetronomeVoices {
    public static let tailSeconds = 0.02
    public static let minVelocity = 0.0001
    public static let maxVelocity = 1.2

    public static let options: [MetronomeVoiceOption] = [
        MetronomeVoiceOption(id: "beep-hi", label: "Beep · high"),
        MetronomeVoiceOption(id: "beep-mid", label: "Beep · mid"),
        MetronomeVoiceOption(id: "beep-lo", label: "Beep · low"),
        MetronomeVoiceOption(id: "wood", label: "Woodblock"),
        MetronomeVoiceOption(id: "clave", label: "Clave"),
        MetronomeVoiceOption(id: "rim", label: "Rimshot"),
        MetronomeVoiceOption(id: "snare", label: "Snare"),
        MetronomeVoiceOption(id: "shaker", label: "Shaker"),
        MetronomeVoiceOption(id: "cowbell", label: "Cowbell"),
        MetronomeVoiceOption(id: "click", label: "Click"),
    ]

    private static let ids: Set<String> = Set(options.map(\.id))

    public static func has(_ id: String) -> Bool {
        ids.contains(id)
    }

    public static func clampVelocity(_ velocity: Double) -> Double {
        min(maxVelocity, max(minVelocity, velocity))
    }

    public static func render(
        id: String,
        sampleRate: Double,
        velocity: Double
    ) -> [Float] {
        var rng = SystemRandomNumberGenerator()
        return render(id: id, sampleRate: sampleRate, velocity: velocity, rng: &rng)
    }

    public static func render<R: RandomNumberGenerator>(
        id: String,
        sampleRate: Double,
        velocity: Double,
        rng: inout R
    ) -> [Float] {
        let vel = clampVelocity(velocity)
        let voices: [Layer]
        switch id {
        case "beep-hi":
            voices = [.tone(Tone(freq: 1760.0, dur: 0.05))]
        case "beep-mid":
            voices = [.tone(Tone(freq: 1244.5, dur: 0.05))]
        case "beep-lo":
            voices = [.tone(Tone(freq: 932.3, dur: 0.045))]
        case "wood":
            voices = [.tone(Tone(freq: 1150.0, endFreq: 540.0, dur: 0.04))]
        case "clave":
            voices = [.tone(Tone(freq: 2480.0, endFreq: 2000.0, dur: 0.035))]
        case "rim":
            voices = [
                .noise(Noise(dur: 0.025, highpass: 2800.0, gain: 0.7)),
                .tone(Tone(freq: 480.0, dur: 0.03, gain: 0.8)),
            ]
        case "snare":
            voices = [
                .noise(Noise(dur: 0.09, bandpass: 1800.0, q: 0.7)),
                .tone(Tone(freq: 196.0, dur: 0.05, type: .triangle, gain: 0.6)),
            ]
        case "shaker":
            voices = [
                .noise(Noise(dur: 0.06, highpass: 5200.0, gain: 0.85, attack: 0.006)),
            ]
        case "cowbell":
            voices = [.cowbell(Cowbell())]
        case "click":
            voices = [.tone(Tone(freq: 1200.0, dur: 0.04, type: .square, gain: 0.9))]
        default:
            voices = [.tone(Tone(freq: 1244.5, dur: 0.05))]
        }
        return mix(voices, sampleRate: sampleRate, vel: vel, rng: &rng)
    }

    private enum Waveform {
        case sine, square, triangle
    }

    private struct Tone {
        var freq: Double
        var endFreq: Double? = nil
        var dur: Double = 0.05
        var type: Waveform = .sine
        var gain: Double = 1.0
        var attack: Double = 0.002
    }

    private struct Noise {
        var dur: Double = 0.08
        var highpass: Double? = nil
        var bandpass: Double? = nil
        var q: Double = 1.0
        var gain: Double = 1.0
        var attack: Double = 0.002
    }

    private struct Cowbell {
        var attack: Double = 0.001
        var dur: Double = 0.16
    }

    private enum Layer {
        case tone(Tone)
        case noise(Noise)
        case cowbell(Cowbell)

        var duration: Double {
            switch self {
            case .tone(let t): return t.dur + tailSeconds
            case .noise(let n): return n.dur + tailSeconds
            case .cowbell: return 0.22 + tailSeconds
            }
        }
    }

    private static func mix<R: RandomNumberGenerator>(
        _ layers: [Layer],
        sampleRate: Double,
        vel: Double,
        rng: inout R
    ) -> [Float] {
        let totalSeconds = layers.map(\.duration).max() ?? tailSeconds
        var out = [Double](repeating: 0.0, count: totalLength(seconds: totalSeconds, sampleRate: sampleRate))

        for layer in layers {
            let buffer = renderLayer(layer, sampleRate: sampleRate, vel: vel, rng: &rng)
            for i in buffer.indices where i < out.count {
                out[i] += Double(buffer[i])
            }
        }
        return out.map { Float($0) }
    }

    private static func totalLength(seconds: Double, sampleRate: Double) -> Int {
        max(1, Int(ceil(seconds * sampleRate)))
    }

    private static func expRamp(a: Double, b: Double, t: Double, d: Double) -> Double {
        if t <= 0 { return a }
        if t >= d { return b }
        if a <= 0 || b <= 0 { return b }
        return a * pow(b / a, t / d)
    }

    private static func renderLayer<R: RandomNumberGenerator>(
        _ layer: Layer,
        sampleRate: Double,
        vel: Double,
        rng: inout R
    ) -> [Float] {
        switch layer {
        case .tone(let tone): return renderTone(tone, sampleRate: sampleRate, vel: vel)
        case .noise(let noise): return renderNoise(noise, sampleRate: sampleRate, vel: vel, rng: &rng)
        case .cowbell(let cowbell): return renderCowbell(cowbell, sampleRate: sampleRate, vel: vel)
        }
    }

    private static func renderTone(_ layer: Tone, sampleRate: Double, vel: Double) -> [Float] {
        let n = totalLength(seconds: layer.dur + tailSeconds, sampleRate: sampleRate)
        var out = [Float](repeating: 0.0, count: n)
        let peak = max(0.0001, vel * layer.gain)
        let sweepEnd = layer.endFreq != nil ? layer.dur * 0.85 : nil
        var phase = 0.0

        for i in 0..<n {
            let t = Double(i) / sampleRate
            let freq: Double
            if let sweepEnd, let endFreq = layer.endFreq {
                freq = t >= sweepEnd ? endFreq : expRamp(a: layer.freq, b: endFreq, t: t, d: sweepEnd)
            } else {
                freq = layer.freq
            }
            phase += 2.0 * .pi * freq / sampleRate

            let env = envelope(t: t, peak: peak, attack: layer.attack, dur: layer.dur)
            let wave: Double
            switch layer.type {
            case .sine: wave = sin(phase)
            case .square: wave = sin(phase) >= 0 ? 1.0 : -1.0
            case .triangle: wave = 2.0 / .pi * asin(sin(phase))
            }
            out[i] = Float(wave * env)
        }
        return out
    }

    private static func renderNoise<R: RandomNumberGenerator>(
        _ layer: Noise,
        sampleRate: Double,
        vel: Double,
        rng: inout R
    ) -> [Float] {
        let n = totalLength(seconds: layer.dur + tailSeconds, sampleRate: sampleRate)
        let filter = BiquadFilter()
        if let hp = layer.highpass {
            filter.setHighpass(sampleRate: sampleRate, frequency: hp, qDb: 0.7)
        } else if let bp = layer.bandpass {
            filter.setBandpass(sampleRate: sampleRate, frequency: bp, qLinear: layer.q)
        } else {
            filter.setBandpass(sampleRate: sampleRate, frequency: 2000.0, qLinear: layer.q)
        }
        let peak = max(0.0001, vel * layer.gain)
        var out = [Float](repeating: 0.0, count: n)
        var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0

        for i in 0..<n {
            let t = Double(i) / sampleRate
            let x0 = Double.random(in: -1.0...1.0, using: &rng)
            let y0 = filter.b0 * x0 + filter.b1 * x1 + filter.b2 * x2 - filter.a1 * y1 - filter.a2 * y2
            let env = envelope(t: t, peak: peak, attack: layer.attack, dur: layer.dur)
            out[i] = Float(y0 * env)
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }
        return out
    }

    private static func renderCowbell(_ layer: Cowbell, sampleRate: Double, vel: Double) -> [Float] {
        let n = totalLength(seconds: 0.22 + tailSeconds, sampleRate: sampleRate)
        let filter = BiquadFilter()
        filter.setBandpass(sampleRate: sampleRate, frequency: 760.0, qLinear: 1.4)
        let peak = max(0.0001, vel * 0.8)
        var out = [Float](repeating: 0.0, count: n)
        var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0
        var phase1 = 0.0, phase2 = 0.0

        for i in 0..<n {
            let t = Double(i) / sampleRate
            phase1 += 2.0 * .pi * 556.0 / sampleRate
            phase2 += 2.0 * .pi * 833.0 / sampleRate
            let s1 = sin(phase1) >= 0 ? 1.0 : -1.0
            let s2 = sin(phase2) >= 0 ? 1.0 : -1.0
            let x0 = s1 + s2
            let y0 = filter.b0 * x0 + filter.b1 * x1 + filter.b2 * x2 - filter.a1 * y1 - filter.a2 * y2
            let env = envelope(t: t, peak: peak, attack: layer.attack, dur: layer.dur)
            out[i] = Float(y0 * env)
            x2 = x1; x1 = x0; y2 = y1; y1 = y0
        }
        return out
    }

    private static func envelope(t: Double, peak: Double, attack: Double, dur: Double) -> Double {
        if t < attack {
            return expRamp(a: 0.0001, b: peak, t: t, d: attack)
        }
        if t < dur {
            let clamped = min(dur, t)
            return expRamp(a: peak, b: 0.0001, t: clamped - attack, d: max(1e-9, dur - attack))
        }
        return 0.0001
    }
}
