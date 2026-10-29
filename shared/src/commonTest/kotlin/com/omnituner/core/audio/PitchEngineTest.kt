package com.omnituner.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RATE = 48000.0
private const val WINDOW_SIZE = 8192
private const val HOP = 2160 // 45 ms at 48 kHz

class PitchEngineTest {

    /** A string that rings with a strong 2nd partial and decays, sampled as the capture ring would. */
    private fun pluck(fundamental: Double, seconds: Double, decayPerSecond: Double): FloatArray {
        val n = (seconds * RATE).toInt()
        return FloatArray(n) { i ->
            val t = i / RATE
            val env = exp(-decayPerSecond * t)
            val v = 0.12 * sin(2 * PI * fundamental * t) +
                0.25 * sin(2 * PI * 2 * fundamental * t) +
                0.10 * sin(2 * PI * 3 * fundamental * t)
            (env * v).toFloat()
        }
    }

    private fun frames(signal: FloatArray): Sequence<FloatArray> = sequence {
        var end = WINDOW_SIZE
        while (end <= signal.size) {
            yield(signal.copyOfRange(end - WINDOW_SIZE, end))
            end += HOP
        }
    }

    private fun centsOff(f: Double, expected: Double) = abs(1200 * log2(f / expected))

    @Test
    fun decayingPluckStaysOnTheFundamentalNeverASubharmonic() {
        val engine = PitchEngine()
        engine.start()
        val fundamental = 82.4069
        val seen = mutableListOf<Double>()

        for (frame in frames(pluck(fundamental, seconds = 3.0, decayPerSecond = 1.4))) {
            engine.process(frame, RATE, PitchDetector.minFrequencyFor(fundamental))
            engine.frequency?.let { seen += it }
        }

        assertTrue(seen.isNotEmpty())
        assertTrue(
            seen.all { centsOff(it, fundamental) < 25.0 },
            "left the fundamental: ${seen.filter { centsOff(it, fundamental) >= 25.0 }}",
        )
    }

    @Test
    fun engineLocksOnASteadyNote() {
        val engine = PitchEngine()
        engine.start()
        val signal = FloatArray(RATE.toInt()) { (0.2 * sin(2 * PI * 196.0 * it / RATE)).toFloat() }

        for (frame in frames(signal)) engine.process(frame, RATE, 27.0)

        assertEquals(PitchTrackingState.LOCKED, engine.trackingState)
        assertEquals(196.0, assertNotNull(engine.frequency), 0.3)
    }

    @Test
    fun silenceNeverLocks() {
        val engine = PitchEngine()
        engine.start()
        repeat(20) { engine.process(FloatArray(WINDOW_SIZE), RATE, 27.0) }
        assertNull(engine.frequency)
        assertEquals(PitchTrackingState.LISTENING, engine.trackingState)
    }

    @Test
    fun stopReturnsToIdle() {
        val engine = PitchEngine()
        engine.start()
        engine.stop()
        assertEquals(PitchTrackingState.IDLE, engine.trackingState)
    }
}
