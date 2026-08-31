package com.omnituner.core.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PitchEstimate(
    val frequency: Double?,
    val confidence: Double,
    val inputLevel: Double,
)

/**
 * Verbatim port of pitch-detector.worker.ts.
 * RMS gate -> DC-offset removal -> YIN (CMND) -> octave guard -> parabolic interpolation.
 */
class PitchDetector {
    private var yinBuffer = DoubleArray(0)

    fun analyse(buffer: FloatArray, sampleRate: Double): PitchEstimate {
        val inputLevel = computeRMS(buffer)
        if (inputLevel < SILENCE_RMS) {
            return PitchEstimate(null, 0.0, inputLevel)
        }

        removeDCOffset(buffer)
        val result = yinDetect(buffer, sampleRate)

        return result.copy(inputLevel = inputLevel)
    }

    private fun yinDetect(buffer: FloatArray, sampleRate: Double): PitchEstimate {
        val n = buffer.size
        val minLag = max(1, floor(sampleRate / MAX_FREQUENCY).toInt())
        val maxLag = min(floor(n / 2.0).toInt(), ceil(sampleRate / MIN_FREQUENCY).toInt())

        if (maxLag <= minLag + 2) {
            return PitchEstimate(null, 0.0, 0.0)
        }

        if (yinBuffer.size < maxLag + 1) {
            yinBuffer = DoubleArray(maxLag + 1)
        }
        val yin = yinBuffer

        val w = n - maxLag
        yin[0] = 1.0
        var runningSum = 0.0

        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until w) {
                val delta = buffer[i].toDouble() - buffer[i + lag].toDouble()
                sum += delta * delta
            }
            runningSum += sum
            yin[lag] = if (runningSum > 0) sum * lag / runningSum else 1.0
        }

        var tau = -1
        var lag = minLag
        while (lag <= maxLag) {
            if (yin[lag] < YIN_THRESHOLD) {
                while (lag + 1 <= maxLag && yin[lag + 1] < yin[lag]) {
                    lag++
                }
                tau = lag
                break
            }
            lag++
        }

        if (tau == -1) {
            var minVal = Double.POSITIVE_INFINITY
            for (candidate in minLag..maxLag) {
                if (yin[candidate] < minVal) {
                    minVal = yin[candidate]
                    tau = candidate
                }
            }
        }

        if (tau <= 0) {
            return PitchEstimate(null, 0.0, 0.0)
        }

        tau = preferLowerFundamental(tau, yin, maxLag, sampleRate)

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

        if (refinedTau <= 0) {
            return PitchEstimate(null, 0.0, 0.0)
        }

        val frequency = sampleRate / refinedTau
        val confidence = max(0.0, 1 - yin[tau])

        if (confidence < MIN_CONFIDENCE) {
            return PitchEstimate(null, 0.0, 0.0)
        }

        return PitchEstimate(frequency, confidence, 0.0)
    }

    private fun preferLowerFundamental(
        tau: Int,
        yin: DoubleArray,
        maxLag: Int,
        sampleRate: Double,
    ): Int {
        val frequency = sampleRate / tau
        if (frequency < 180) return tau

        val candidateTau = tau * 2
        if (candidateTau > maxLag) return tau

        val candidateValue = yin[candidateTau]
        val currentValue = yin[tau]

        return if (candidateValue + 0.05 < currentValue) candidateTau else tau
    }

    companion object {
        const val MIN_FREQUENCY = 50.0
        const val MAX_FREQUENCY = 1200.0
        const val YIN_THRESHOLD = 0.15
        const val MIN_CONFIDENCE = 0.58
        const val SILENCE_RMS = 0.004

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
