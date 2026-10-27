package com.omnituner.core.audio

/**
 * The detection pipeline after the filters: detector -> tracker -> adaptive gate, one call per
 * analysis window. Pure and single-threaded; the capture loop owns one instance.
 */
class PitchEngine {

    private val detector = PitchDetector()
    private val gate = AdaptiveNoiseGate()
    private val tracker = PitchTracker()

    val frequency: Double? get() = tracker.frequency
    val trackingState: PitchTrackingState get() = tracker.trackingState

    /** Starts a fresh listening session. */
    fun start() {
        gate.reset()
        tracker.reset()
        tracker.markListening()
    }

    fun stop() {
        gate.reset()
        tracker.reset()
        tracker.markIdle()
    }

    /** [window] is mutated (DC offset removal). Returns the detector's raw view of the frame. */
    fun process(window: FloatArray, sampleRate: Double, minFrequency: Double): PitchEstimate {
        val usedGate = gate.gate
        val estimate = detector.analyse(
            window,
            sampleRate,
            silenceGate = usedGate,
            minFrequency = minFrequency,
            trackedFrequency = tracker.trackedFrequency,
        )
        tracker.update(estimate, usedGate)
        gate.update(estimate.inputLevel, tracker.pitched)
        return estimate
    }
}
