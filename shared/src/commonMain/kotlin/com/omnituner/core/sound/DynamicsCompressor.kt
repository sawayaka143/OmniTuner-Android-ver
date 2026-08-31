package com.omnituner.core.sound

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Feed-forward dynamics compressor mirroring the web master chain's
 * DynamicsCompressorNode: threshold -10 dB, knee 8 dB, ratio 4,
 * attack 2 ms, release 150 ms. Applied per mixed PCM chunk with
 * continuous envelope state across chunks.
 */
class DynamicsCompressor(
    private val sampleRate: Double,
    val thresholdDb: Double = -10.0,
    val kneeDb: Double = 8.0,
    val ratio: Double = 4.0,
    val attackSeconds: Double = 0.002,
    val releaseSeconds: Double = 0.15,
) {

    private var envelopeDb = 0.0

    // First-order detector averaging (web spec uses ~10 ms RMS-ish window).
    private val detectorCoef = exp(-1.0 / (DETECTOR_SECONDS * sampleRate))
    private var detectorLevel = 0.0
    private val attackCoef = exp(-1.0 / (attackSeconds * sampleRate))
    private val releaseCoef = exp(-1.0 / (releaseSeconds * sampleRate))

    fun reset() {
        envelopeDb = 0.0
        detectorLevel = 0.0
    }

    /** Processes input in place; state persists across chunks. */
    fun processInPlace(buffer: FloatArray, count: Int = buffer.size) {
        val n = count.coerceAtMost(buffer.size)
        for (i in 0 until n) {
            val x = buffer[i].toDouble()

            // Envelope detector on the rectified signal.
            val rectified = if (x < 0) -x else x
            detectorLevel = rectified + detectorCoef * (detectorLevel - rectified)

            val inputDb = 20.0 * log10(max(detectorLevel, MIN_LEVEL))

            // Soft-knee gain computer.
            val kneeHalf = kneeDb / 2.0
            val over = inputDb - thresholdDb
            val targetDb = when {
                over < -kneeHalf -> 0.0
                over > kneeHalf -> over * (1.0 / ratio - 1.0)
                else -> {
                    val shaped = over + kneeHalf
                    (1.0 / ratio - 1.0) * shaped * shaped / (2.0 * kneeDb)
                }
            }

            // Attack/release smoothing toward the target gain.
            envelopeDb = if (targetDb < envelopeDb) {
                targetDb + attackCoef * (envelopeDb - targetDb)
            } else {
                targetDb + releaseCoef * (envelopeDb - targetDb)
            }

            val gain = 10.0.pow(envelopeDb / 20.0)
            buffer[i] = (x * gain).toFloat()
        }
    }

    companion object {
        private const val MIN_LEVEL = 1e-6
        private const val DETECTOR_SECONDS = 0.01

        fun log10(value: Double): Double = kotlin.math.ln(value) / kotlin.math.ln(10.0)

        fun rms(buffer: FloatArray): Double {
            var sum = 0.0
            for (v in buffer) sum += v.toDouble() * v
            return sqrt(sum / buffer.size)
        }
    }
}
