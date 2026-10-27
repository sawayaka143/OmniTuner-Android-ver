package com.omnituner.core.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One analysed frame. [frequency] is null when the frame was rejected (silence or low confidence);
 * [clarity] and [candidateFrequency] still report what the detector saw. The tracked fields are
 * filled only when the caller passed a tracked frequency.
 */
data class PitchEstimate(
    val frequency: Double?,
    val confidence: Double,
    val inputLevel: Double,
    val clarity: Double = 0.0,
    val candidateFrequency: Double? = null,
    val trackedFrequency: Double? = null,
    val trackedClarity: Double = 0.0,
)

class PitchDetector {
    private var yinBuffer = DoubleArray(0)
    private val difference = YinDifference()

    fun analyse(
        buffer: FloatArray,
        sampleRate: Double,
        silenceGate: Double = SILENCE_RMS,
        minFrequency: Double = MIN_FREQUENCY,
        trackedFrequency: Double? = null,
    ): PitchEstimate {
        val inputLevel = computeRMS(buffer)
        if (inputLevel < silenceGate) {
            return PitchEstimate(null, 0.0, inputLevel)
        }

        removeDCOffset(buffer)
        val result = yinDetect(buffer, sampleRate, minFrequency, trackedFrequency)

        return result.copy(inputLevel = inputLevel)
    }

    private fun yinDetect(
        buffer: FloatArray,
        sampleRate: Double,
        minFrequency: Double,
        trackedFrequency: Double?,
    ): PitchEstimate {
        val n = buffer.size
        val minLag = max(1, floor(sampleRate / MAX_FREQUENCY).toInt())
        val maxLag = min(floor(n / 2.0).toInt(), ceil(sampleRate / minFrequency).toInt())

        if (maxLag <= minLag + 2) {
            return PitchEstimate(null, 0.0, 0.0)
        }

        if (yinBuffer.size < maxLag + 1) {
            yinBuffer = DoubleArray(maxLag + 1)
        }
        val yin = yinBuffer

        difference.compute(buffer, maxLag, yin)
        yin[0] = 1.0
        var runningSum = 0.0
        for (lag in 1..maxLag) {
            val d = yin[lag]
            runningSum += d
            yin[lag] = if (runningSum > 0) d * lag / runningSum else 1.0
        }

        // First dip below an adaptive threshold, never the global minimum: with noise the dips at
        // 2x/3x/4x the period are nearly equal and the minimum lands on a random subharmonic.
        var deepest = Double.POSITIVE_INFINITY
        for (lag in minLag..maxLag) {
            if (yin[lag] < deepest) deepest = yin[lag]
        }
        val threshold = max(YIN_THRESHOLD, deepest + DIP_MARGIN)

        var tau = -1
        var lag = minLag
        while (lag <= maxLag) {
            if (yin[lag] < threshold) {
                while (lag + 1 <= maxLag && yin[lag + 1] < yin[lag]) {
                    lag++
                }
                tau = lag
                break
            }
            lag++
        }

        val tracked = trackedFrequency?.let { trackedContinuation(yin, sampleRate, minLag, maxLag, it) }
        val trackedFreq = tracked?.first
        val trackedClarity = tracked?.second ?: 0.0

        if (tau <= 0) {
            return PitchEstimate(null, 0.0, 0.0, trackedFrequency = trackedFreq, trackedClarity = trackedClarity)
        }

        tau = preferLowerFundamental(tau, yin, maxLag, sampleRate)

        val refinedTau = refine(yin, tau, maxLag)
        if (refinedTau <= 0) {
            return PitchEstimate(null, 0.0, 0.0, trackedFrequency = trackedFreq, trackedClarity = trackedClarity)
        }

        val frequency = sampleRate / refinedTau
        val clarity = max(0.0, 1 - yin[tau])

        if (clarity < MIN_CONFIDENCE) {
            return PitchEstimate(
                frequency = null,
                confidence = 0.0,
                inputLevel = 0.0,
                clarity = clarity,
                candidateFrequency = frequency,
                trackedFrequency = trackedFreq,
                trackedClarity = trackedClarity,
            )
        }

        return PitchEstimate(
            frequency = frequency,
            confidence = clarity,
            inputLevel = 0.0,
            clarity = clarity,
            candidateFrequency = frequency,
            trackedFrequency = trackedFreq,
            trackedClarity = trackedClarity,
        )
    }

    private fun refine(yin: DoubleArray, tau: Int, maxLag: Int): Double {
        var refinedTau = tau.toDouble()
        if (tau > 0 && tau < maxLag) {
            val y0 = yin[tau - 1]
            val y1 = yin[tau]
            val y2 = yin[tau + 1]
            val denom = y0 - 2 * y1 + y2
            if (denom != 0.0) {
                val shift = 0.5 * (y0 - y2) / denom
                refinedTau = tau + shift.coerceIn(-1.0, 1.0)
            }
        }
        return refinedTau
    }

    /**
     * Looks for the yin minimum within +/-3% of the tracked note's lag, so a decaying pluck whose
     * pick has wandered off still reports its own clarity. Does not change the primary pick.
     */
    private fun trackedContinuation(
        yin: DoubleArray,
        sampleRate: Double,
        minLag: Int,
        maxLag: Int,
        trackedFrequency: Double,
    ): Pair<Double, Double>? {
        if (!trackedFrequency.isFinite() || trackedFrequency <= 0) return null
        val centerLag = sampleRate / trackedFrequency
        val lo = max(minLag + 1, ceil(centerLag * (1 - TRACKED_LAG_TOLERANCE)).toInt())
        val hi = min(maxLag - 1, floor(centerLag * (1 + TRACKED_LAG_TOLERANCE)).toInt())
        if (hi - lo < 2) return null

        var best = lo
        for (candidate in lo..hi) {
            if (yin[candidate] < yin[best]) best = candidate
        }
        // A minimum on the window edge is just a slope, not a dip.
        if (best <= lo || best >= hi) return null

        val refined = refine(yin, best, maxLag)
        if (refined <= 0) return null
        return (sampleRate / refined) to max(0.0, 1 - yin[best])
    }

    private fun preferLowerFundamental(
        tau: Int,
        yin: DoubleArray,
        maxLag: Int,
        sampleRate: Double,
    ): Int {
        val frequency = sampleRate / tau
        if (frequency >= HIGH_FUNDAMENTAL_HZ) {
            val candidateTau = tau * 2
            if (candidateTau > maxLag) return tau
            return if (yin[candidateTau] + DIP_MARGIN < yin[tau]) candidateTau else tau
        }

        // Low strings: climb to a multiple of the lag while it is a clearly better dip.
        var current = tau
        var moved = true
        while (moved) {
            moved = false
            for (multiple in OCTAVE_MULTIPLES) {
                val candidateTau = current * multiple
                if (candidateTau > maxLag) break
                val candidate = yin[candidateTau]
                if (yin[current] > LOW_GUARD_MIN_CURRENT &&
                    candidate <= LOW_GUARD_MAX_CANDIDATE &&
                    candidate <= LOW_GUARD_RATIO * yin[current]
                ) {
                    current = candidateTau
                    moved = true
                    break
                }
            }
        }
        return current
    }

    companion object {
        const val MIN_FREQUENCY = 27.0
        const val MAX_FREQUENCY = 1500.0
        const val YIN_THRESHOLD = 0.15
        const val DIP_MARGIN = 0.05
        const val MIN_CONFIDENCE = 0.58
        const val SILENCE_RMS = 0.004
        const val TRACKED_LAG_TOLERANCE = 0.03

        private const val HIGH_FUNDAMENTAL_HZ = 180.0
        private val OCTAVE_MULTIPLES = intArrayOf(2, 3, 4)
        private const val LOW_GUARD_MIN_CURRENT = 0.005
        private const val LOW_GUARD_MAX_CANDIDATE = 0.3
        private const val LOW_GUARD_RATIO = 0.5

        /** Detection floor for a tuning whose lowest string is [lowestHz] (null when unknown). */
        fun minFrequencyFor(lowestHz: Double?): Double =
            if (lowestHz != null) max(MIN_FREQUENCY, lowestHz * 0.6) else MIN_FREQUENCY

        /** High-pass cutoff that keeps rumble out while leaving the lowest string alone. */
        fun highpassHzFor(lowestHz: Double?): Double =
            if (lowestHz != null) max(20.0, lowestHz * 0.5) else 20.0

        fun computeRMS(buffer: FloatArray): Double {
            var sum = 0.0
            for (v in buffer) {
                sum += v.toDouble() * v
            }
            return sqrt(sum / buffer.size)
        }

        fun removeDCOffset(buffer: FloatArray) {
            var sum = 0.0
            for (v in buffer) {
                sum += v.toDouble()
            }
            val mean = sum / buffer.size
            if (abs(mean) < 0.0001) return
            for (i in buffer.indices) {
                buffer[i] = (buffer[i] - mean).toFloat()
            }
        }
    }
}
