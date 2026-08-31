package com.omnituner.core.audiofile

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WavParserTest {

    private fun buildWav(
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitsPerSample: Int = 24,
        samples: List<Int>,
    ): ByteArray {
        val bytesPerSample = bitsPerSample / 8
        val dataBytes = samples.size * bytesPerSample
        val totalSize = 36 + 8 + dataBytes
        val out = ByteArray(totalSize)
        var pos = 0

        fun writeAscii(text: String) {
            for (char in text) out[pos++] = char.code.toByte()
        }

        fun writeInt32(value: Int) {
            out[pos++] = (value and 0xff).toByte()
            out[pos++] = ((value shr 8) and 0xff).toByte()
            out[pos++] = ((value shr 16) and 0xff).toByte()
            out[pos++] = ((value shr 24) and 0xff).toByte()
        }

        fun writeInt16(value: Int) {
            out[pos++] = (value and 0xff).toByte()
            out[pos++] = ((value shr 8) and 0xff).toByte()
        }

        writeAscii("RIFF")
        writeInt32(36 + dataBytes)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeInt32(16)
        writeInt16(1) // PCM
        writeInt16(channels)
        writeInt32(sampleRate)
        writeInt32(sampleRate * channels * bytesPerSample)
        writeInt16(channels * bytesPerSample)
        writeInt16(bitsPerSample)
        writeAscii("data")
        writeInt32(dataBytes)

        for (sample in samples) {
            if (bitsPerSample == 24) {
                val v = sample and 0xffffff
                out[pos++] = (v and 0xff).toByte()
                out[pos++] = ((v shr 8) and 0xff).toByte()
                out[pos++] = ((v shr 16) and 0xff).toByte()
            } else {
                val v = sample and 0xffff
                out[pos++] = (v and 0xff).toByte()
                out[pos++] = ((v shr 8) and 0xff).toByte()
            }
        }
        return out
    }

    @Test
    fun parses24BitMono() {
        val samples = listOf(0, 0x400000, -0x400000, 0x7fffff, -0x800000)
        val wav = buildWav(samples = samples)
        val parsed = WavParser.parse(wav)

        assertEquals(44100, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(5, parsed.samples.size)

        assertEquals(0.0f, parsed.samples[0])
        assertEquals(0.5f, parsed.samples[1], 1e-6f)
        assertEquals(-0.5f, parsed.samples[2], 1e-6f)
        assertTrue(abs(parsed.samples[3] - 0.99999988f) < 1e-6f)
        assertEquals(-1.0f, parsed.samples[4], 1e-6f)
    }

    @Test
    fun parses16Bit() {
        val wav = buildWav(bitsPerSample = 16, samples = listOf(0, 16384, -16384))
        val parsed = WavParser.parse(wav)
        assertEquals(0.0f, parsed.samples[0])
        assertEquals(0.5f, parsed.samples[1], 1e-6f)
        assertEquals(-0.5f, parsed.samples[2], 1e-6f)
    }

    @Test
    fun parsesStereoInterleaved() {
        val wav = buildWav(channels = 2, samples = listOf(0x400000, 0x200000, -0x400000, -0x200000))
        val parsed = WavParser.parse(wav)
        assertEquals(2, parsed.channels)
        assertEquals(4, parsed.samples.size)
        assertEquals(0.5f, parsed.samples[0], 1e-6f)
        assertEquals(0.25f, parsed.samples[1], 1e-6f)
        assertEquals(-0.5f, parsed.samples[2], 1e-6f)
    }

    @Test
    fun rejectsNonWav() {
        assertFailsWith<IllegalArgumentException> {
            WavParser.parse(ByteArray(64))
        }
    }
}
