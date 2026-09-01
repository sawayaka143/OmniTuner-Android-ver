package com.omnituner.core.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow

enum class PitchTrackingState {
    IDLE,
    LISTENING,
    LOCKED,
}

/**
 * Port of the smoothing/tracking half of audio-capture-service.ts.
 * Median-of-3 in log domain -> jump guard with pending-snap -> adaptive EMA ->
 * dropout holds (silent + audible) -> idle/listening/locked states.
 */
class PitchSmoother {

    var frequency: Double? = null
        private set

    var trackingState: PitchTrackingState = PitchTrackingState.IDLE
        private set

    private var recentLogFreqs: MutableList<Double> = mutableListOf()
    private var smoothedFrequency: Double? = null
    private var emaLogFreq: Double? = null
    private var missedFrames = 0
    private var pendingLogFreq: Double? = null

    /** Mirror of the worker.onmessage dispatch (session/generation checks live in the engine). */
    fun onAnalysisResult(frequency: Double?, confidence: Double, inputLevel: Double) {
        if (frequency == null || confidence <= 0) {
            handleDropout(inputLevel)
        } else {
            handleDetection(frequency)
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
    }

    /** Full stop-reset (mirror of stopCapture): clears the displayed frequency too. */
    fun reset() {
        resetTracking()
        frequency = null
        trackingState = PitchTrackingState.IDLE
    }

    private fun handleDetection(rawFrequency: Double) {
        missedFrames = 0

        val smoothed = smoothFrequency(rawFrequency) ?: return

        frequency = smoothed
        smoothedFrequency = smoothed

        trackingState =
            if (recentLogFreqs.size >= 3) PitchTrackingState.LOCKED else PitchTrackingState.LISTENING
    }

    private fun handleDropout(inputLevel: Double) {
        missedFrames += 1
        val audible = inputLevel >= PitchDetector.SILENCE_RMS

        if (audible && missedFrames <= AUDIBLE_HOLD_FRAMES) return

        if (missedFrames <= MAX_DROPOUT_HOLD_FRAMES && smoothedFrequency != null) {
            return
        }

        resetTracking()
        frequency = null
        trackingState = PitchTrackingState.LISTENING
    }

    private fun smoothFrequency(frequency: Double): Double? {
        if (!frequency.isFinite() || frequency <= 0) {
            emaLogFreq?.let { return 2.0.pow(it) }
            if (recentLogFreqs.isNotEmpty()) return 2.0.pow(median(recentLogFreqs))
            smoothedFrequency?.let { return it }

            return null
        }
        val candidateLog = log2(frequency)

        if (recentLogFreqs.isNotEmpty()) {
            val medianLog = median(recentLogFreqs)

            val jumpCents = abs((candidateLog - medianLog) * 1200)

            if (jumpCents > MAX_SMOOTHING_JUMP_CENTS) {
                val coherent =
                    pendingLogFreq != null &&
                        abs((candidateLog - pendingLogFreq!!) * 1200) <= MAX_SMOOTHING_JUMP_CENTS
                pendingLogFreq = candidateLog

                if (!coherent) {
                    emaLogFreq?.let { return 2.0.pow(it) }
                    return 2.0.pow(median(recentLogFreqs))
                }

                pendingLogFreq = null
                recentLogFreqs = mutableListOf(candidateLog, candidateLog, candidateLog)
                emaLogFreq = candidateLog
                return frequency
            }
            pendingLogFreq = null
        }

        recentLogFreqs.add(candidateLog)
        if (recentLogFreqs.size > SMOOTHING_WINDOW) {
            recentLogFreqs.removeAt(0)
        }

        val currentMedianLog = median(recentLogFreqs)

        if (emaLogFreq == null || recentLogFreqs.size < 3) {
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
        const val EMA_ALPHA = 0.12
        const val MAX_SMOOTHING_JUMP_CENTS = 380.0
        const val ADAPTIVE_ALPHA_CENTS = 100.0
        const val MAX_DROPOUT_HOLD_FRAMES = 6
        const val AUDIBLE_HOLD_FRAMES = 60
    }
}
