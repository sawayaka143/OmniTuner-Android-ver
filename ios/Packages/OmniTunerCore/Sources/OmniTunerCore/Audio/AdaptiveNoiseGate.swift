import Foundation

public final class AdaptiveNoiseGate {
    public static let minGateDb = -70.0
    public static let maxGateDb = -47.96
    public static let gateMarginDb = 6.0
    public static let floorRiseDbPerFrame = 0.07
    public static let floorFallRate = 0.3
    private static let minLevelDb = -120.0

    private var floorDb: Double?
    public private(set) var gate: Double = PitchDetector.silenceRms

    public init() {}

    public func reset() {
        floorDb = nil
        gate = PitchDetector.silenceRms
    }

    @discardableResult
    public func update(rms: Double, pitched: Bool) -> Double {
        let levelDb = rms > 0 ? max(Self.minLevelDb, 20.0 * log10(rms)) : Self.minLevelDb
        let next: Double
        if let floor = floorDb {
            if levelDb < floor {
                next = floor + Self.floorFallRate * (levelDb - floor)
            } else if !pitched {
                next = min(levelDb, floor + Self.floorRiseDbPerFrame)
            } else {
                next = floor
            }
        } else {
            next = levelDb
        }
        floorDb = next
        let clampedDb = min(Self.maxGateDb, max(Self.minGateDb, next + Self.gateMarginDb))
        gate = pow(10.0, clampedDb / 20.0)
        return gate
    }
}
