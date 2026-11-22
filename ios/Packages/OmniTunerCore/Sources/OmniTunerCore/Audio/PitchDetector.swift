#if canImport(Accelerate)
import Accelerate
#endif
import Foundation

public struct PitchEstimate: Sendable, Hashable {
    public var frequency: Double?
    public var confidence: Double
    public var inputLevel: Double
    public var clarity: Double
    public var candidateFrequency: Double?
    public var trackedFrequency: Double?
    public var trackedClarity: Double

    public init(
        frequency: Double?,
        confidence: Double,
        inputLevel: Double,
        clarity: Double = 0.0,
        candidateFrequency: Double? = nil,
        trackedFrequency: Double? = nil,
        trackedClarity: Double = 0.0
    ) {
        self.frequency = frequency
        self.confidence = confidence
        self.inputLevel = inputLevel
        self.clarity = clarity
        self.candidateFrequency = candidateFrequency
        self.trackedFrequency = trackedFrequency
        self.trackedClarity = trackedClarity
    }
}

public final class PitchDetector {
    public static let minFrequency = 27.0
    public static let maxFrequency = 1500.0
    public static let yinThreshold = 0.15
    public static let dipMargin = 0.05
    public static let minConfidence = 0.58
    public static let silenceRms = 0.004
    public static let trackedLagTolerance = 0.03

    private static let highFundamentalHz = 180.0
    private static let octaveMultiples = [2, 3, 4]
    private static let lowGuardMinCurrent = 0.005
    private static let lowGuardMaxCandidate = 0.3
    private static let lowGuardRatio = 0.5

    private var yinBuffer: [Double] = []
    private let difference = YinDifference()

    public init() {}

    public func analyse(
        buffer: inout [Float],
        sampleRate: Double,
        silenceGate: Double = silenceRms,
        minFrequency: Double = minFrequency,
        trackedFrequency: Double? = nil
    ) -> PitchEstimate {
        let inputLevel = Self.computeRMS(buffer)
        if inputLevel < silenceGate {
            return PitchEstimate(frequency: nil, confidence: 0.0, inputLevel: inputLevel)
        }

        Self.removeDCOffset(&buffer)
        var result = yinDetect(
            buffer: buffer,
            sampleRate: sampleRate,
            minFrequency: minFrequency,
            trackedFrequency: trackedFrequency
        )
        result.inputLevel = inputLevel
        return result
    }

    private func yinDetect(
        buffer: [Float],
        sampleRate: Double,
        minFrequency: Double,
        trackedFrequency: Double?
    ) -> PitchEstimate {
        let n = buffer.count
        let minLag = max(1, Int(floor(sampleRate / Self.maxFrequency)))
        let maxLag = min(Int(floor(Double(n) / 2.0)), Int(ceil(sampleRate / minFrequency)))

        if maxLag <= minLag + 2 {
            return PitchEstimate(frequency: nil, confidence: 0.0, inputLevel: 0.0)
        }

        if yinBuffer.count < maxLag + 1 {
            yinBuffer = [Double](repeating: 0.0, count: maxLag + 1)
        }

        difference.compute(buffer: buffer, maxLag: maxLag, out: &yinBuffer)
        yinBuffer[0] = 1.0
        var runningSum = 0.0
        for lag in 1...maxLag {
            let d = yinBuffer[lag]
            runningSum += d
            yinBuffer[lag] = runningSum > 0 ? d * Double(lag) / runningSum : 1.0
        }

        var deepest = Double.infinity
        for lag in minLag...maxLag {
            if yinBuffer[lag] < deepest { deepest = yinBuffer[lag] }
        }
        let threshold = max(Self.yinThreshold, deepest + Self.dipMargin)

        var tau = -1
        var lag = minLag
        while lag <= maxLag {
            if yinBuffer[lag] < threshold {
                while lag + 1 <= maxLag && yinBuffer[lag + 1] < yinBuffer[lag] {
                    lag += 1
                }
                tau = lag
                break
            }
            lag += 1
        }

        let tracked = trackedFrequency.flatMap {
            trackedContinuation(yin: yinBuffer, sampleRate: sampleRate, minLag: minLag, maxLag: maxLag, trackedFrequency: $0)
        }
        let trackedFreq = tracked?.0
        let trackedClarity = tracked?.1 ?? 0.0

        if tau <= 0 {
            return PitchEstimate(
                frequency: nil,
                confidence: 0.0,
                inputLevel: 0.0,
                trackedFrequency: trackedFreq,
                trackedClarity: trackedClarity
            )
        }

        tau = preferLowerFundamental(tau: tau, yin: yinBuffer, maxLag: maxLag, sampleRate: sampleRate)

        let refinedTau = refine(yin: yinBuffer, tau: tau, maxLag: maxLag)
        if refinedTau <= 0 {
            return PitchEstimate(
                frequency: nil,
                confidence: 0.0,
                inputLevel: 0.0,
                trackedFrequency: trackedFreq,
                trackedClarity: trackedClarity
            )
        }

        let frequency = sampleRate / refinedTau
        let clarity = max(0.0, 1.0 - yinBuffer[tau])

        if clarity < Self.minConfidence {
            return PitchEstimate(
                frequency: nil,
                confidence: 0.0,
                inputLevel: 0.0,
                clarity: clarity,
                candidateFrequency: frequency,
                trackedFrequency: trackedFreq,
                trackedClarity: trackedClarity
            )
        }

        return PitchEstimate(
            frequency: frequency,
            confidence: clarity,
            inputLevel: 0.0,
            clarity: clarity,
            candidateFrequency: frequency,
            trackedFrequency: trackedFreq,
            trackedClarity: trackedClarity
        )
    }

    private func refine(yin: [Double], tau: Int, maxLag: Int) -> Double {
        var refinedTau = Double(tau)
        if tau > 0 && tau < maxLag {
            let y0 = yin[tau - 1]
            let y1 = yin[tau]
            let y2 = yin[tau + 1]
            let denom = y0 - 2.0 * y1 + y2
            if denom != 0.0 {
                let shift = 0.5 * (y0 - y2) / denom
                refinedTau = Double(tau) + min(1.0, max(-1.0, shift))
            }
        }
        return refinedTau
    }

    private func trackedContinuation(
        yin: [Double],
        sampleRate: Double,
        minLag: Int,
        maxLag: Int,
        trackedFrequency: Double
    ) -> (Double, Double)? {
        guard trackedFrequency.isFinite, trackedFrequency > 0 else { return nil }
        let centerLag = sampleRate / trackedFrequency
        let lo = max(minLag + 1, Int(ceil(centerLag * (1.0 - Self.trackedLagTolerance))))
        let hi = min(maxLag - 1, Int(floor(centerLag * (1.0 + Self.trackedLagTolerance))))
        if hi - lo < 2 { return nil }

        var best = lo
        for candidate in lo...hi {
            if yin[candidate] < yin[best] { best = candidate }
        }
        if best <= lo || best >= hi { return nil }

        let refined = refine(yin: yin, tau: best, maxLag: maxLag)
        if refined <= 0 { return nil }
        return (sampleRate / refined, max(0.0, 1.0 - yin[best]))
    }

    private func preferLowerFundamental(
        tau: Int,
        yin: [Double],
        maxLag: Int,
        sampleRate: Double
    ) -> Int {
        let frequency = sampleRate / Double(tau)
        if frequency >= Self.highFundamentalHz {
            let candidateTau = tau * 2
            if candidateTau > maxLag { return tau }
            return (yin[candidateTau] + Self.dipMargin < yin[tau]) ? candidateTau : tau
        }

        var current = tau
        var moved = true
        while moved {
            moved = false
            for multiple in Self.octaveMultiples {
                let candidateTau = current * multiple
                if candidateTau > maxLag { break }
                let candidate = yin[candidateTau]
                if yin[current] > Self.lowGuardMinCurrent &&
                    candidate <= Self.lowGuardMaxCandidate &&
                    candidate <= Self.lowGuardRatio * yin[current] {
                    current = candidateTau
                    moved = true
                    break
                }
            }
        }
        return current
    }

    public static func minFrequencyFor(lowestHz: Double?) -> Double {
        if let lowestHz { return max(minFrequency, lowestHz * 0.6) }
        return minFrequency
    }

    public static func highpassHzFor(lowestHz: Double?) -> Double {
        if let lowestHz { return max(20.0, lowestHz * 0.5) }
        return 20.0
    }

    public static func computeRMS(_ buffer: [Float]) -> Double {
        guard !buffer.isEmpty else { return 0.0 }
#if canImport(Accelerate)
        var rms: Float = 0.0
        vDSP_rmsqv(buffer, 1, &rms, vDSP_Length(buffer.count))
        return Double(rms)
#else
        var sum = 0.0
        for v in buffer {
            let d = Double(v)
            sum += d * d
        }
        return sqrt(sum / Double(buffer.count))
#endif
    }

    public static func removeDCOffset(_ buffer: inout [Float]) {
        guard !buffer.isEmpty else { return }
#if canImport(Accelerate)
        var mean: Float = 0.0
        vDSP_meanv(buffer, 1, &mean, vDSP_Length(buffer.count))
        if abs(Double(mean)) < 0.0001 { return }
        buffer.withUnsafeMutableBufferPointer { ptr in
            guard let base = ptr.baseAddress else { return }
            var negMean = -mean
            vDSP_vsadd(base, 1, &negMean, base, 1, vDSP_Length(ptr.count))
        }
#else
        var sum = 0.0
        for v in buffer {
            sum += Double(v)
        }
        let mean = sum / Double(buffer.count)
        if abs(mean) < 0.0001 { return }
        for i in buffer.indices {
            buffer[i] = Float(Double(buffer[i]) - mean)
        }
#endif
    }
}
