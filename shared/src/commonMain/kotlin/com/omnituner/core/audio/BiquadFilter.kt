package com.omnituner.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * RBJ cookbook biquad with Web Audio spec Q semantics:
 * for lowpass/highpass the Q parameter is a resonance value in dB, converted to a
 * linear resonance of 10^(Q/20) before computing alpha = sin(w0) / (2 * resonance).
 * (This matches BiquadFilterNode; a classic DSP book would treat Q=0.7 as linear.)
 */
class BiquadFilter {

    var b0 = 0.0
        private set
    var b1 = 0.0
        private set
    var b2 = 0.0
        private set
    var a1 = 0.0
        private set
    var a2 = 0.0
        private set

    // Unnormalized alpha, exposed for spec-semantics tests.
    var rawAlpha = 0.0
        private set

    fun setLowpass(sampleRate: Double, frequency: Double, qDb: Double) {
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosW0 = cos(w0)
        val resonance = resonanceFromQDb(qDb)
        val alpha = sin(w0) / (2.0 * resonance)
        rawAlpha = alpha

        b0 = (1.0 - cosW0) / 2.0
        b1 = 1.0 - cosW0
        b2 = (1.0 - cosW0) / 2.0
        normalize(alpha, cosW0)
    }

    fun setHighpass(sampleRate: Double, frequency: Double, qDb: Double) {
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosW0 = cos(w0)
        val resonance = resonanceFromQDb(qDb)
        val alpha = sin(w0) / (2.0 * resonance)
        rawAlpha = alpha

        b0 = (1.0 + cosW0) / 2.0
        b1 = -(1.0 + cosW0)
        b2 = (1.0 + cosW0) / 2.0
        normalize(alpha, cosW0)
    }

    /**
     * Web Audio "bandpass": Q in linear units, constant 0 dB peak gain form
     * (b0 = alpha, b2 = -alpha), matching BiquadFilterNode/Chromium.
     */
    fun setBandpass(sampleRate: Double, frequency: Double, qLinear: Double) {
        val w0 = 2.0 * PI * frequency / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * qLinear)
        rawAlpha = alpha

        b0 = alpha
        b1 = 0.0
        b2 = -alpha
        normalize(alpha, cosW0)
    }

    private fun normalize(alpha: Double, cosW0: Double) {
        val a0 = 1.0 + alpha
        b0 /= a0
        b1 /= a0
        b2 /= a0
        a1 = -2.0 * cosW0 / a0
        a2 = (1.0 - alpha) / a0
    }

    fun process(input: FloatArray, output: FloatArray) {
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in input.indices) {
            val x0 = input[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            output[i] = y0.toFloat()
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
    }

    fun processInPlace(input: FloatArray) = process(input, input)

    companion object {
        fun resonanceFromQDb(qDb: Double): Double = 10.0.pow(qDb / 20.0)
    }
}
