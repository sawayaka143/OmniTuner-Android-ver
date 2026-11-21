import Foundation

public enum PitchTrackingState: Sendable, Hashable {
    case idle
    case listening
    case locked
    case holding
}

public final class PitchTracker {
    public static let smoothingWindow = 3
    public static let emaAlpha = 0.25
    public static let adaptiveAlphaCents = 20.0
    public static let maxSmoothingJumpCents = 380.0
    public static let noteChangeFrames = 2
    public static let harmonicJumpFrames = 3
    public static let harmonicToleranceCents = 40.0
    public static let trackContinueClarity = 0.45
    public static let onsetRiseDb = 3.0
    public static let onsetFrames = 3
    public static let maxDropoutHoldFrames = 6
    public static let audibleHoldFrames = 22

    private static let harmonicRatios = [2.0, 3.0, 4.0]

    public private(set) var frequency: Double?
    public private(set) var trackingState: PitchTrackingState = .idle

    public var pitched: Bool { trackingState == .locked }

    public var trackedFrequency: Double? {
        recentLogFreqs.count >= Self.smoothingWindow ? smoothedFrequency : nil
    }

    private var recentLogFreqs: [Double] = []
    private var smoothedFrequency: Double?
    private var emaLogFreq: Double?
    private var missedFrames = 0
    private var pendingLogFreq: Double?
    private var pendingFrames = 0
    private var previousLevelDb: Double?
    private var onsetFramesLeft = 0

    public init() {}

    public func update(estimate: PitchEstimate, gate: Double) {
        let levelDb = estimate.inputLevel > 0 ? 20.0 * log10(estimate.inputLevel) : -120.0
        if let previous = previousLevelDb, levelDb - previous >= Self.onsetRiseDb {
            onsetFramesLeft = Self.onsetFrames
        }
        previousLevelDb = levelDb
        let inOnset = onsetFramesLeft > 0
        if inOnset { onsetFramesLeft -= 1 }

        if let chosen = choosePitch(estimate: estimate, inOnset: inOnset) {
            handleDetection(chosen)
        } else {
            handleDropout(audible: estimate.inputLevel >= gate)
        }
    }

    public func markListening() {
        trackingState = .listening
    }

    public func markIdle() {
        trackingState = .idle
    }

    public func resetTracking() {
        recentLogFreqs = []
        smoothedFrequency = nil
        emaLogFreq = nil
        missedFrames = 0
        pendingLogFreq = nil
        pendingFrames = 0
        previousLevelDb = nil
        onsetFramesLeft = 0
    }

    public func reset() {
        resetTracking()
        frequency = nil
        trackingState = .idle
    }

    private func choosePitch(estimate: PitchEstimate, inOnset: Bool) -> Double? {
        let raw: Double? = if let f = estimate.frequency, f.isFinite, f > 0, estimate.confidence > 0 { f } else { nil }
        let track = smoothedFrequency
        let tracked: Double? = if let f = estimate.trackedFrequency,
            recentLogFreqs.count >= Self.smoothingWindow,
            !inOnset,
            f.isFinite,
            f > 0,
            estimate.trackedClarity >= Self.trackContinueClarity {
            f
        } else {
            nil
        }
        if let tracked, raw == nil || (track != nil && isHarmonicOf(raw!, reference: track!)) {
            return tracked
        }
        return raw
    }

    private func isHarmonicOf(_ candidate: Double, reference: Double) -> Bool {
        let cents = abs(1200.0 * log2(candidate / reference))
        return Self.harmonicRatios.contains { abs(cents - 1200.0 * log2($0)) <= Self.harmonicToleranceCents }
    }

    private func handleDetection(_ rawFrequency: Double) {
        missedFrames = 0
        guard let smoothed = smoothFrequency(rawFrequency) else { return }

        frequency = smoothed
        smoothedFrequency = smoothed
        trackingState = recentLogFreqs.count >= Self.smoothingWindow ? .locked : .listening
    }

    private func handleDropout(audible: Bool) {
        missedFrames += 1
        let budget = audible ? Self.audibleHoldFrames : Self.maxDropoutHoldFrames

        if missedFrames <= budget && smoothedFrequency != nil {
            if trackingState == .locked { trackingState = .holding }
            return
        }

        resetTracking()
        frequency = nil
        trackingState = .listening
    }

    private func smoothFrequency(_ frequency: Double) -> Double? {
        let candidateLog = log2(frequency)

        if !recentLogFreqs.isEmpty {
            let medianLog = median(recentLogFreqs)
            let jumpCents = abs((candidateLog - medianLog) * 1200.0)

            if jumpCents > Self.maxSmoothingJumpCents {
                let coherent = pendingLogFreq != nil &&
                    abs((candidateLog - pendingLogFreq!) * 1200.0) <= Self.maxSmoothingJumpCents
                pendingFrames = coherent ? pendingFrames + 1 : 1
                pendingLogFreq = candidateLog

                let needed = isHarmonicOf(frequency, reference: pow(2.0, medianLog))
                    ? Self.harmonicJumpFrames
                    : Self.noteChangeFrames
                if pendingFrames < needed {
                    if let emaLogFreq { return pow(2.0, emaLogFreq) }
                    return pow(2.0, medianLog)
                }

                pendingLogFreq = nil
                pendingFrames = 0
                recentLogFreqs = [Double](repeating: candidateLog, count: Self.smoothingWindow)
                emaLogFreq = candidateLog
                return frequency
            }
            pendingLogFreq = nil
            pendingFrames = 0
        }

        recentLogFreqs.append(candidateLog)
        if recentLogFreqs.count > Self.smoothingWindow {
            recentLogFreqs.removeFirst()
        }

        let currentMedianLog = median(recentLogFreqs)

        if emaLogFreq == nil || recentLogFreqs.count < Self.smoothingWindow {
            emaLogFreq = currentMedianLog
        } else {
            let innovationCents = abs((currentMedianLog - emaLogFreq!) * 1200.0)
            let alpha = min(1.0, Self.emaAlpha + innovationCents / Self.adaptiveAlphaCents)
            emaLogFreq = alpha * currentMedianLog + (1.0 - alpha) * emaLogFreq!
        }

        return pow(2.0, emaLogFreq!)
    }

    private func median(_ values: [Double]) -> Double {
        let sorted = values.sorted()
        let mid = sorted.count / 2
        if sorted.count % 2 == 0 {
            return (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            return sorted[mid]
        }
    }
}
