package com.omnituner.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SAMPLE_RATE = 48000.0
private const val WINDOW = 8192

private fun centsBetween(f1: Double, f2: Double): Double = 1200.0 * log2(f1 / f2)

private fun sine(
    freq: Double,
    amplitude: Double = 0.2,
    sampleRate: Double = SAMPLE_RATE,
    size: Int = WINDOW,
    harmonics: List<Pair<Double, Double>> = emptyList(),
    dcOffset: Double = 0.0,
): FloatArray {
    val out = FloatArray(size)
    for (i in 0 until size) {
        val t = i / sampleRate
        var v = amplitude * sin(2.0 * PI * freq * t)
        for ((hFreq, hAmp) in harmonics) {
            v += hAmp * sin(2.0 * PI * hFreq * t)
        }
        out[i] = (v + dcOffset).toFloat()
    }
    return out
}

class PitchDetectorTest {

    private val detector = PitchDetector()

    private fun assertDetects(buffer: FloatArray, expectedHz: Double, toleranceCents: Double = 1.0) {
        val estimate = detector.analyse(buffer, SAMPLE_RATE)
        val f = assertNotNull(estimate.frequency, "expected a pitch near $expectedHz Hz")
        val off = abs(centsBetween(f, expectedHz))
        assertTrue(
            off <= toleranceCents,
            "detected ${f}Hz, expected ${expectedHz}Hz, off by $off cents",
        )
        assertTrue(estimate.confidence >= PitchDetector.MIN_CONFIDENCE)
        assertTrue(estimate.inputLevel > 0.0)
    }

    @Test
    fun detectsPureSinesWithinOneCent() {
        assertDetects(sine(440.0), 440.0)
        assertDetects(sine(220.0), 220.0)
        assertDetects(sine(880.0), 880.0)
        assertDetects(sine(82.4069), 82.4069)
        assertDetects(sine(329.63), 329.63)
        assertDetects(sine(196.0), 196.0)
    }

    @Test
    fun octaveGuardPrefersLowerFundamentalWhenSecondHarmonicDominates() {
        // 220 Hz fundamental with a much louder 440 Hz second harmonic:
        // YIN's first dip lands at 440 (an octave too high); the guard must
        // drop to the sub-octave when yin[2t] is 0.05 better.
        assertDetects(sine(220.0, amplitude = 0.2, harmonics = listOf(440.0 to 0.8)), 220.0, 1.0)
    }

    @Test
    fun octaveGuardDoesNotFalselyDropPureSine() {
        assertDetects(sine(220.0, amplitude = 0.2), 220.0, 1.0)
    }

    @Test
    fun dcOffsetIsRemoved() {
        assertDetects(sine(440.0, dcOffset = 0.3), 440.0)
    }

    @Test
    fun silenceIsRejectedByRmsGate() {
        val estimate = detector.analyse(FloatArray(WINDOW), SAMPLE_RATE)
        assertNull(estimate.frequency)
        assertEquals(0.0, estimate.confidence, 1e-12)
        assertTrue(estimate.inputLevel < PitchDetector.SILENCE_RMS)
    }

    @Test
    fun inaudibleNoiseIsRejectedByRmsGate() {
        val random = Random(42)
        val buffer = FloatArray(WINDOW) { ((random.nextDouble() - 0.5) * 0.002).toFloat() }
        val estimate = detector.analyse(buffer, SAMPLE_RATE)
        assertNull(estimate.frequency)
        assertTrue(estimate.inputLevel < PitchDetector.SILENCE_RMS)
    }

    @Test
    fun loudNoiseFailsConfidenceGate() {
        val random = Random(7)
        val buffer = FloatArray(WINDOW) { ((random.nextDouble() - 0.5) * 0.6).toFloat() }
        val estimate = detector.analyse(buffer, SAMPLE_RATE)
        assertNull(estimate.frequency)
        assertTrue(estimate.inputLevel >= PitchDetector.SILENCE_RMS)
    }

    @Test
    fun detectableWindowBoundariesHold() {
        // Lowest and highest in-range pitches round-trip through the lag bounds.
        assertDetects(sine(50.5), 50.5, 2.0)
        assertDetects(sine(1150.0), 1150.0, 2.0)
    }

    @Test
    fun rmsMatchesDefinition() {
        val buffer = floatArrayOf(0.1f, -0.1f, 0.1f, -0.1f)
        assertEquals(0.1, PitchDetector.computeRMS(buffer), 1e-7)
    }

    @Test
    fun dcRemovalSkipsTinyMeans() {
        val buffer = FloatArray(4) { 0.00001f }
        PitchDetector.removeDCOffset(buffer)
        assertEquals(0.00001f, buffer[0])
    }
}
