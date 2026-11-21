import OmniTunerCore

public final class TuneLock {
    public static let releaseHysteresisMs: Int64 = 135
    public static let holdHysteresisCents = 1.5

    public private(set) var confirmed = false
    private var holdStartedAt: Int64?
    private var outOfRangeSince: Int64?

    public init() {}

    public func reset() {
        confirmed = false
        holdStartedAt = nil
        outOfRangeSince = nil
    }

    public func step(
        now: Int64,
        cents: Double?,
        tracking: PitchTrackingState,
        tolerance: Double,
        holdMs: Int64
    ) -> Bool {
        if tracking == .idle {
            reset()
            return false
        }

        if tracking == .holding {
            if !confirmed { holdStartedAt = nil }
            outOfRangeSince = nil
            return false
        }

        let locked = tracking == .locked
        let inRange = locked && cents != nil && abs(cents!) <= tolerance

        if confirmed {
            if inRange {
                outOfRangeSince = nil
            } else {
                let since = outOfRangeSince ?? now
                outOfRangeSince = since
                if now - since >= Self.releaseHysteresisMs {
                    reset()
                }
            }
            return false
        }

        if !inRange {
            let withinHysteresis = locked && holdStartedAt != nil && cents != nil &&
                abs(cents!) <= tolerance + Self.holdHysteresisCents
            if !withinHysteresis {
                holdStartedAt = nil
                return false
            }
        }

        let start = holdStartedAt ?? now
        holdStartedAt = start
        if now - start < holdMs { return false }

        confirmed = true
        holdStartedAt = nil
        outOfRangeSince = nil
        return true
    }
}
