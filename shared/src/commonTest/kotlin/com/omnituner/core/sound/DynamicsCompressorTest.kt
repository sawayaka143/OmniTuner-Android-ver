package com.omnituner.core.sound

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicsCompressorTest {

    private val sampleRate = 48000.0

    private fun steadyTone(amplitude: Double, seconds: Double): FloatArray {
        val n = (seconds * sampleRate).toInt()
        return FloatArray(n) { i -> (amplitude * kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / sampleRate)).toFloat() }
    }

    @Test
    fun quietSignalsPassThrough() {
        val compressor = DynamicsCompressor(sampleRate)
        val buffer = steadyTone(0.05, 1.0)
        val inRms = DynamicsCompressor.rms(buffer)
        compressor.processInPlace(buffer)
        val outRms = DynamicsCompressor.rms(buffer)
        // -14 dB peak is below the -10 dB threshold: no gain reduction
        assertEquals(inRms, outRms, inRms * 0.05)
    }

    @Test
    fun loudSignalsAreCompressed() {
        val compressor = DynamicsCompressor(sampleRate)
        val buffer = steadyTone(1.2, 1.0)
        val inRms = DynamicsCompressor.rms(buffer)
        compressor.processInPlace(buffer)
        val outRms = DynamicsCompressor.rms(buffer)
        // detector sits ~2 dB over the -10 dB threshold with ratio 4:
        // expect moderate gain reduction, not silence
        assertTrue(outRms < inRms * 0.75, "out=$outRms in=$inRms")
        assertTrue(outRms > 0.1, "out=$outRms")
    }

    @Test
    fun statePersistsAcrossChunks() {
        val whole = DynamicsCompressor(sampleRate)
        val wholeBuffer = steadyTone(0.9, 0.5)
        whole.processInPlace(wholeBuffer)

        val chunked = DynamicsCompressor(sampleRate)
        val chunkedBuffer = steadyTone(0.9, 0.5)
        var offset = 0
        while (offset < chunkedBuffer.size) {
            val count = minOf(480, chunkedBuffer.size - offset)
            chunkedBuffer.copyOfRange(offset, offset + count)
            val view = FloatArray(count)
            System.arraycopy(chunkedBuffer, offset, view, 0, count)
            chunked.processInPlace(view, count)
            System.arraycopy(view, 0, chunkedBuffer, offset, count)
            offset += count
        }
        val wholeRms = DynamicsCompressor.rms(wholeBuffer)
        val chunkedRms = DynamicsCompressor.rms(chunkedBuffer)
        assertEquals(wholeRms, chunkedRms, wholeRms * 0.02)
    }

    @Test
    fun compressorConstantsMatchWebMasterChain() {
        val compressor = DynamicsCompressor(sampleRate)
        assertEquals(-10.0, compressor.thresholdDb)
        assertEquals(8.0, compressor.kneeDb)
        assertEquals(4.0, compressor.ratio)
        assertEquals(0.002, compressor.attackSeconds, 1e-9)
        assertEquals(0.15, compressor.releaseSeconds, 1e-9)
    }

    @Test
    fun noNaNOnSilence() {
        val compressor = DynamicsCompressor(sampleRate)
        val buffer = FloatArray(4800)
        compressor.processInPlace(buffer)
        assertTrue(buffer.all { abs(it) <= 1e-6 && it.isFinite() })
    }
}
