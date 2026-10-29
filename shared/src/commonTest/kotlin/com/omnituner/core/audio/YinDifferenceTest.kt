package com.omnituner.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class YinDifferenceTest {

    @Test
    fun fftDifferenceMatchesBruteForce() {
        val random = Random(3)
        val n = 4096
        val maxLag = 900
        val buffer = FloatArray(n) { i ->
            (0.3 * sin(2.0 * PI * 173.0 * i / 48000.0) + (random.nextDouble() - 0.5) * 0.1).toFloat()
        }
        val fft = DoubleArray(maxLag + 1)
        YinDifference().compute(buffer, maxLag, fft)

        val w = n - maxLag
        for (tau in listOf(1, 17, 277, 555, 900)) {
            var brute = 0.0
            for (i in 0 until w) {
                val delta = buffer[i].toDouble() - buffer[i + tau].toDouble()
                brute += delta * delta
            }
            assertTrue(abs(brute - fft[tau]) < 1e-6 * (1 + brute), "tau=$tau brute=$brute fft=${fft[tau]}")
        }
    }
}
