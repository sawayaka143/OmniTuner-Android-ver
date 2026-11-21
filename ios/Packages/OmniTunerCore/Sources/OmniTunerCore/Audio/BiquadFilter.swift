import Foundation

public final class BiquadFilter {
    public private(set) var b0 = 0.0
    public private(set) var b1 = 0.0
    public private(set) var b2 = 0.0
    public private(set) var a1 = 0.0
    public private(set) var a2 = 0.0
    public private(set) var rawAlpha = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    public init() {}

    public func setLowpass(sampleRate: Double, frequency: Double, qDb: Double) {
        let w0 = 2.0 * .pi * frequency / sampleRate
        let cosW0 = cos(w0)
        let resonance = Self.resonanceFromQDb(qDb)
        let alpha = sin(w0) / (2.0 * resonance)
        rawAlpha = alpha

        b0 = (1.0 - cosW0) / 2.0
        b1 = 1.0 - cosW0
        b2 = (1.0 - cosW0) / 2.0
        normalize(alpha: alpha, cosW0: cosW0)
    }

    public func setHighpass(sampleRate: Double, frequency: Double, qDb: Double) {
        let w0 = 2.0 * .pi * frequency / sampleRate
        let cosW0 = cos(w0)
        let resonance = Self.resonanceFromQDb(qDb)
        let alpha = sin(w0) / (2.0 * resonance)
        rawAlpha = alpha

        b0 = (1.0 + cosW0) / 2.0
        b1 = -(1.0 + cosW0)
        b2 = (1.0 + cosW0) / 2.0
        normalize(alpha: alpha, cosW0: cosW0)
    }

    public func setHighpassLinear(sampleRate: Double, frequency: Double, q: Double) {
        let w0 = 2.0 * .pi * frequency / sampleRate
        let cosW0 = cos(w0)
        let alpha = sin(w0) / (2.0 * q)
        rawAlpha = alpha

        b0 = (1.0 + cosW0) / 2.0
        b1 = -(1.0 + cosW0)
        b2 = (1.0 + cosW0) / 2.0
        normalize(alpha: alpha, cosW0: cosW0)
    }

    public func setLowpassLinear(sampleRate: Double, frequency: Double, q: Double) {
        let w0 = 2.0 * .pi * frequency / sampleRate
        let cosW0 = cos(w0)
        let alpha = sin(w0) / (2.0 * q)
        rawAlpha = alpha

        b0 = (1.0 - cosW0) / 2.0
        b1 = 1.0 - cosW0
        b2 = (1.0 - cosW0) / 2.0
        normalize(alpha: alpha, cosW0: cosW0)
    }

    public func setBandpass(sampleRate: Double, frequency: Double, qLinear: Double) {
        let w0 = 2.0 * .pi * frequency / sampleRate
        let cosW0 = cos(w0)
        let alpha = sin(w0) / (2.0 * qLinear)
        rawAlpha = alpha

        b0 = alpha
        b1 = 0.0
        b2 = -alpha
        normalize(alpha: alpha, cosW0: cosW0)
    }

    private func normalize(alpha: Double, cosW0: Double) {
        let a0 = 1.0 + alpha
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 = -2.0 * cosW0 / a0
        a2 = (1.0 - alpha) / a0
    }

    public func reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    public func process(_ input: [Float], output: inout [Float], count: Int? = nil) {
        let n = min(count ?? input.count, min(input.count, output.count))
        for i in 0..<n {
            let x0 = Double(input[i])
            let y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            output[i] = Float(y0)
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
    }

    public func processInPlace(_ input: inout [Float]) {
        let n = input.count
        for i in 0..<n {
            let x0 = Double(input[i])
            let y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            input[i] = Float(y0)
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
    }

    public static func resonanceFromQDb(_ qDb: Double) -> Double {
        pow(10.0, qDb / 20.0)
    }
}
