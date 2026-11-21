import Foundation

public final class DynamicsCompressor {
    public let thresholdDb: Double
    public let kneeDb: Double
    public let ratio: Double
    public let attackSeconds: Double
    public let releaseSeconds: Double

    private var envelopeDb = 0.0
    private let detectorCoef: Double
    private var detectorLevel = 0.0
    private let attackCoef: Double
    private let releaseCoef: Double

    private static let minLevel = 1e-6
    private static let detectorSeconds = 0.01

    public init(
        sampleRate: Double,
        thresholdDb: Double = -10.0,
        kneeDb: Double = 8.0,
        ratio: Double = 4.0,
        attackSeconds: Double = 0.002,
        releaseSeconds: Double = 0.15
    ) {
        self.thresholdDb = thresholdDb
        self.kneeDb = kneeDb
        self.ratio = ratio
        self.attackSeconds = attackSeconds
        self.releaseSeconds = releaseSeconds
        self.detectorCoef = exp(-1.0 / (Self.detectorSeconds * sampleRate))
        self.attackCoef = exp(-1.0 / (attackSeconds * sampleRate))
        self.releaseCoef = exp(-1.0 / (releaseSeconds * sampleRate))
    }

    public func reset() {
        envelopeDb = 0.0
        detectorLevel = 0.0
    }

    public func processInPlace(_ buffer: inout [Float], count: Int? = nil) {
        let n = min(count ?? buffer.count, buffer.count)
        for i in 0..<n {
            let x = Double(buffer[i])
            let rectified = x < 0 ? -x : x
            detectorLevel = rectified + detectorCoef * (detectorLevel - rectified)

            let inputDb = 20.0 * Self.log10(max(detectorLevel, Self.minLevel))

            let kneeHalf = kneeDb / 2.0
            let over = inputDb - thresholdDb
            let targetDb: Double
            if over < -kneeHalf {
                targetDb = 0.0
            } else if over > kneeHalf {
                targetDb = over * (1.0 / ratio - 1.0)
            } else {
                let shaped = over + kneeHalf
                targetDb = (1.0 / ratio - 1.0) * shaped * shaped / (2.0 * kneeDb)
            }

            if targetDb < envelopeDb {
                envelopeDb = targetDb + attackCoef * (envelopeDb - targetDb)
            } else {
                envelopeDb = targetDb + releaseCoef * (envelopeDb - targetDb)
            }

            let gain = pow(10.0, envelopeDb / 20.0)
            buffer[i] = Float(x * gain)
        }
    }

    public static func log10(_ value: Double) -> Double {
        Foundation.log(value) / Foundation.log(10.0)
    }

    public static func rms(_ buffer: [Float]) -> Double {
        var sum = 0.0
        for v in buffer {
            let d = Double(v)
            sum += d * d
        }
        return sqrt(sum / Double(buffer.count))
    }
}
