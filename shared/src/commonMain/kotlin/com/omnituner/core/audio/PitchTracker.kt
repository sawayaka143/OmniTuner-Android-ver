package com.omnituner.core.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow

enum class PitchTrackingState {
    IDLE,
    LISTENING,
    LOCKED,

    /** An unclear frame after a lock: the last note is still shown, but nothing is confirmed. */
    HOLDING,
}

/**
 * Turns raw detector frames into the one note the tuner shows: a median-then-EMA smoothed pitch
 * that survives dropouts and rejects harmonic or unrelated jumps until they repeat.
 */
class PitchTracker {

    var frequency: Double? = null
        private set

    var trackingState: PitchTrackingState = PitchTrackingState.IDLE
        private set

    /** True only on a frame that produced a valid detection with a full window. */
    val pitched: Boolean get() = trackingState == PitchTrackingState.LOCKED

    /** The note to hand the detector for tracked continuation, once the window is full. */
    val trackedFrequency: Double?
        get() = if (recentLogFreqs.size >= SMOOTHING_WINDOW) smoothedFrequency else null

    private var recentLogFreqs: MutableList<Double> = mutableListOf()
    private var smoothedFrequency: Double? = null
    private var emaLogFreq: Double? = null
    private var missedFrames = 0
    private var pendingLogFreq: Double? = null
    private var pendingFrames = 0
    private var previousLevelDb: Double? = null
    private var onsetFramesLeft = 0

    /** [gate] is the silence gate the frame was analysed with; it tells silence from a muddy note. */
    fun update(estimate: PitchEstimate, gate: Double) {
        val levelDb = if (estimate.inputLevel > 0) 20 * log10(estimate.inputLevel) else -120.0
        val previous = previousLevelDb
        previousLevelDb = levelDb
        if (previous != null && levelDb - previous >= ONSET_RISE_DB) {
            onsetFramesLeft = ONSET_FRAMES
        }
        val inOnset = onsetFramesLeft > 0
        if (inOnset) onsetFramesLeft--

        val chosen = choosePitch(estimate, inOnset)
        if (chosen == null) {
            handleDropout(estimate.inputLevel >= gate)
        } else {
            handleDetection(chosen)
        }
    }

    fun markListening() {
        trackingState = PitchTrackingState.LISTENING
    }

    fun markIdle() {
        trackingState = PitchTrackingState.IDLE
    }

    fun resetTracking() {
        recentLogFreqs = mutableListOf()
        smoothedFrequency = null
        emaLogFreq = null
        missedFrames = 0
        pendingLogFreq = null
        pendingFrames = 0
        previousLevelDb = null
        onsetFramesLeft = 0
    }

    fun reset() {
        resetTracking()
        frequency = null
        trackingState = PitchTrackingState.IDLE
    }

    private fun choosePitch(estimate: PitchEstimate, inOnset: Boolean): Double? {
        val raw = estimate.frequency?.takeIf { it.isFinite() && it > 0 && estimate.confidence > 0 }
        val track = smoothedFrequency
        val tracked = estimate.trackedFrequency?.takeIf {
            recentLogFreqs.size >= SMOOTHING_WINDOW &&
                !inOnset &&
                it.isFinite() && it > 0 &&
                estimate.trackedClarity >= TRACK_CONTINUE_CLARITY
        }
        if (tracked != null && (raw == null || (track != null && isHarmonicOf(raw, track)))) {
            return tracked
        }
        return raw
    }

    /** True when [candidate] sits at x2, x3 or x4 (or the matching subharmonic) of [reference]. */
    private fun isHarmonicOf(candidate: Double, reference: Double): Boolean {
        val cents = abs(1200 * log2(candidate / reference))
        return HARMONIC_RATIOS.any { abs(cents - 1200 * log2(it)) <= HARMONIC_TOLERANCE_CENTS }
    }

    private fun handleDetection(rawFrequency: Double) {
        missedFrames = 0

        val smoothed = smoothFrequency(rawFrequency) ?: return

        frequency = smoothed
        smoothedFrequency = smoothed

        trackingState =
            if (recentLogFreqs.size >= SMOOTHING_WINDOW) PitchTrackingState.LOCKED else PitchTrackingState.LISTENING
    }

    private fun handleDropout(audible: Boolean) {
        missedFrames += 1
        val budget = if (audible) AUDIBLE_HOLD_FRAMES else MAX_DROPOUT_HOLD_FRAMES

        if (missedFrames <= budget && smoothedFrequency != null) {
            // Keep showing the last note; only a locked note counts as "holding".
            if (trackingState == PitchTrackingState.LOCKED) trackingState = PitchTrackingState.HOLDING
            return
        }

        resetTracking()
        frequency = null
        trackingState = PitchTrackingState.LISTENING
    }

    private fun smoothFrequency(frequency: Double): Double? {
        val candidateLog = log2(frequency)

        if (recentLogFreqs.isNotEmpty()) {
            val medianLog = median(recentLogFreqs)

            val jumpCents = abs((candidateLog - medianLog) * 1200)

            if (jumpCents > MAX_SMOOTHING_JUMP_CENTS) {
                val coherent =
                    pendingLogFreq != null &&
                        abs((candidateLog - pendingLogFreq!!) * 1200) <= MAX_SMOOTHING_JUMP_CENTS
                pendingFrames = if (coherent) pendingFrames + 1 else 1
                pendingLogFreq = candidateLog

                val needed = if (isHarmonicOf(frequency, 2.0.pow(medianLog))) HARMONIC_JUMP_FRAMES else NOTE_CHANGE_FRAMES
                if (pendingFrames < needed) {
                    // Unconfirmed: keep emitting the old value.
                    emaLogFreq?.let { return 2.0.pow(it) }
                    return 2.0.pow(medianLog)
                }

                pendingLogFreq = null
                pendingFrames = 0
                recentLogFreqs = MutableList(SMOOTHING_WINDOW) { candidateLog }
                emaLogFreq = candidateLog
                return frequency
            }
            pendingLogFreq = null
            pendingFrames = 0
        }

        recentLogFreqs.add(candidateLog)
        if (recentLogFreqs.size > SMOOTHING_WINDOW) {
            recentLogFreqs.removeAt(0)
        }

        val currentMedianLog = median(recentLogFreqs)

        if (emaLogFreq == null || recentLogFreqs.size < SMOOTHING_WINDOW) {
            emaLogFreq = currentMedianLog
        } else {
            val innovationCents = abs((currentMedianLog - emaLogFreq!!) * 1200)
            val alpha = min(1.0, EMA_ALPHA + innovationCents / ADAPTIVE_ALPHA_CENTS)
            emaLogFreq = alpha * currentMedianLog + (1 - alpha) * emaLogFreq!!
        }

        return 2.0.pow(emaLogFreq!!)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    companion object {
        const val SMOOTHING_WINDOW = 3
        const val EMA_ALPHA = 0.25
        const val ADAPTIVE_ALPHA_CENTS = 20.0
        const val MAX_SMOOTHING_JUMP_CENTS = 380.0
        const val NOTE_CHANGE_FRAMES = 2
        const val HARMONIC_JUMP_FRAMES = 3
        const val HARMONIC_TOLERANCE_CENTS = 40.0
        const val TRACK_CONTINUE_CLARITY = 0.45
        const val ONSET_RISE_DB = 3.0
        const val ONSET_FRAMES = 3
        const val MAX_DROPOUT_HOLD_FRAMES = 6
        const val AUDIBLE_HOLD_FRAMES = 22

        private val HARMONIC_RATIOS = doubleArrayOf(2.0, 3.0, 4.0)
    }
}
