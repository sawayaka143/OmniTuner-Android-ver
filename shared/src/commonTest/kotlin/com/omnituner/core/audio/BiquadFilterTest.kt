package com.omnituner.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiquadFilterTest {

    private fun magnitudeAt(
        b0: Double,
        b1: Double,
        b2: Double,
        a1: Double,
        a2: Double,
        w: Double,
    ): Double {
        val cos1 = cos(w)
        val cos2 = cos(2 * w)
        val sin1 = sin(w)
        val sin2 = sin(2 * w)
        val numRe = b0 + b1 * cos1 + b2 * cos2
        val numIm = -(b1 * sin1 + b2 * sin2)
        val denRe = 1.0 + a1 * cos1 + a2 * cos2
        val denIm = -(a1 * sin1 + a2 * sin2)
        val num = kotlin.math.sqrt(numRe * numRe + numIm * numIm)
        val den = kotlin.math.sqrt(denRe * denRe + denIm * denIm)
        return num / den
    }

    @Test
    fun resonanceConvertsDbToLinear() {
        assertEquals(1.0839266, BiquadFilter.resonanceFromQDb(0.7), 1e-6)
        assertEquals(1.0, BiquadFilter.resonanceFromQDb(0.0), 1e-12)
        assertEquals(10.0, BiquadFilter.resonanceFromQDb(20.0), 1e-9)
    }

    @Test
    fun highpassAlphaUsesWebAudioDbSemantics() {
        val filter = BiquadFilter()
        filter.setHighpass(48000.0, 38.0, 0.7)
        assertEquals(2.294456e-3, filter.rawAlpha, 1e-6)
    }

    @Test
    fun lowpassAlphaStructure() {
        val filter = BiquadFilter()
        filter.setLowpass(48000.0, 1250.0, 0.7)
        val w0 = 2.0 * PI * 1250.0 / 48000.0
        assertEquals(sin(w0) / (2.0 * BiquadFilter.resonanceFromQDb(0.7)), filter.rawAlpha, 1e-12)
    }

    private fun coefficientsOf(configure: BiquadFilter.() -> Unit): DoubleArray {
        val filter = BiquadFilter()
        filter.configure()
        return doubleArrayOf(filter.b0, filter.b1, filter.b2, filter.a1, filter.a2)
    }

    @Test
    fun lowpassPassesDCAndRejectsNyquist() {
        val (b0, b1, b2, a1, a2) = coefficientsOf { setLowpass(48000.0, 1250.0, 0.7) }
        assertEquals(1.0, magnitudeAt(b0, b1, b2, a1, a2, 0.0), 1e-12)
        assertEquals(0.0, magnitudeAt(b0, b1, b2, a1, a2, PI), 1e-12)
    }

    @Test
    fun highpassRejectsDCAndPassesNyquist() {
        val (b0, b1, b2, a1, a2) = coefficientsOf { setHighpass(48000.0, 38.0, 0.7) }
        assertEquals(0.0, magnitudeAt(b0, b1, b2, a1, a2, 0.0), 1e-12)
        assertEquals(1.0, magnitudeAt(b0, b1, b2, a1, a2, PI), 1e-12)
    }

    private fun rms(buffer: FloatArray, from: Int = 0): Double {
        var sum = 0.0
        for (i in from until buffer.size) {
            sum += buffer[i].toDouble() * buffer[i]
        }
        return kotlin.math.sqrt(sum / (buffer.size - from))
    }

    private fun tone(hz: Double, seconds: Double, sampleRate: Double = 48000.0): FloatArray {
        val n = (seconds * sampleRate).toInt()
        return FloatArray(n) { i ->
            (0.2 * sin(2.0 * PI * hz * i / sampleRate)).toFloat()
        }
    }

    @Test
    fun processingAttenuatesOutOfBand() {
        val hp = BiquadFilter()
        hp.setHighpass(48000.0, 38.0, 0.7)
        val lowTone = tone(20.0, 1.0)
        hp.processInPlace(lowTone)
        val lowIn = rms(tone(20.0, 1.0))
        assertTrue(rms(lowTone, lowTone.size / 2) < 0.5 * lowIn, "20 Hz must be attenuated by HP 38")

        val lp = BiquadFilter()
        lp.setLowpass(48000.0, 1250.0, 0.7)
        val highTone = tone(3000.0, 1.0)
        lp.processInPlace(highTone)
        val highIn = rms(tone(3000.0, 1.0))
        assertTrue(rms(highTone, highTone.size / 2) < 0.3 * highIn, "3 kHz must be attenuated by LP 1250")

        val hp2 = BiquadFilter()
        hp2.setHighpass(48000.0, 38.0, 0.7)
        val midTone = tone(440.0, 1.0)
        hp2.processInPlace(midTone)
        val midIn = rms(tone(440.0, 1.0))
        assertTrue(abs(rms(midTone, midTone.size / 2) - midIn) < 0.05 * midIn, "440 Hz must pass HP 38")
    }

    @Test
    fun chunkedProcessingMatchesSinglePass() {
        val signal = tone(440.0, 0.5)

        val single = BiquadFilter()
        single.setLowpass(48000.0, 1250.0, 0.7)
        val whole = signal.copyOf()
        single.processInPlace(whole)

        val chunked = BiquadFilter()
        chunked.setLowpass(48000.0, 1250.0, 0.7)
        val piecewise = signal.copyOf()
        val tmp = FloatArray(2048)
        var offset = 0
        while (offset < piecewise.size) {
            val len = minOf(tmp.size, piecewise.size - offset)
            for (i in 0 until len) tmp[i] = signal[offset + i]
            chunked.process(tmp, tmp, len)
            for (i in 0 until len) piecewise[offset + i] = tmp[i]
            offset += len
        }

        for (i in signal.size - 256 until signal.size) {
            assertEquals(whole[i], piecewise[i], 1e-4f)
        }
    }

    @Test
    fun resetClearsDelayLine() {
        val signal = tone(220.0, 0.25)

        val first = BiquadFilter()
        first.setHighpass(48000.0, 38.0, 0.7)
        val expected = signal.copyOf()
        first.processInPlace(expected)

        val reused = BiquadFilter()
        reused.setHighpass(48000.0, 38.0, 0.7)
        reused.processInPlace(signal.copyOf())
        reused.reset()
        val again = signal.copyOf()
        reused.processInPlace(again)

        for (i in signal.size - 256 until signal.size) {
            assertEquals(expected[i], again[i], 1e-4f)
        }
    }
}
