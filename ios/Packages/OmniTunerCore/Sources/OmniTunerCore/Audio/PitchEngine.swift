public final class PitchEngine {
    private let detector = PitchDetector()
    private let gate = AdaptiveNoiseGate()
    private let tracker = PitchTracker()

    public var frequency: Double? { tracker.frequency }
    public var trackingState: PitchTrackingState { tracker.trackingState }

    public init() {}

    public func start() {
        gate.reset()
        tracker.reset()
        tracker.markListening()
    }

    public func stop() {
        gate.reset()
        tracker.reset()
        tracker.markIdle()
    }

    public func process(window: inout [Float], sampleRate: Double, minFrequency: Double) -> PitchEstimate {
        let usedGate = gate.gate
        let estimate = detector.analyse(
            buffer: &window,
            sampleRate: sampleRate,
            silenceGate: usedGate,
            minFrequency: minFrequency,
            trackedFrequency: tracker.trackedFrequency
        )
        tracker.update(estimate: estimate, gate: usedGate)
        gate.update(rms: estimate.inputLevel, pitched: tracker.pitched)
        return estimate
    }
}
