package com.omnituner.core.audiofile

data class ParsedWav(
    val sampleRate: Int,
    val channels: Int,
    val samples: FloatArray,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = samples.contentHashCode()
}

/**
 * Minimal RIFF/WAVE parser for the bundled guitar samples
 * (PCM format 1, mono, 44.1 kHz, 24-bit). Converts 24-bit PCM to FloatArray
 * in [-1, 1] the same way Web Audio's decodeAudioData does.
 */
object WavParser {

    fun parse(bytes: ByteArray): ParsedWav {
        require(bytes.size >= 44) { "WAV file too small" }
        require(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) { "not a RIFF file" }
        require(bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte()) { "not a WAVE file" }

        var offset = 12
        var format = -1
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataLength = -1

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readInt32LE(bytes, offset + 4)
            val bodyOffset = offset + 8

            when (chunkId) {
                "fmt " -> {
                    format = readInt16LE(bytes, bodyOffset)
                    channels = readInt16LE(bytes, bodyOffset + 2)
                    sampleRate = readInt32LE(bytes, bodyOffset + 4)
                    bitsPerSample = readInt16LE(bytes, bodyOffset + 14)
                }
                "data" -> {
                    dataOffset = bodyOffset
                    dataLength = chunkSize.coerceAtMost(bytes.size - bodyOffset)
                }
            }

            offset = bodyOffset + chunkSize + if (chunkSize % 2 == 1) 1 else 0
        }

        require(format == 1) { "only PCM WAV supported, got format $format" }
        require(dataOffset >= 0 && dataLength > 0) { "no data chunk" }
        require(bitsPerSample == 24 || bitsPerSample == 16) {
            "unsupported bit depth $bitsPerSample"
        }

        val bytesPerSample = bitsPerSample / 8
        val frameCount = dataLength / (bytesPerSample * channels)
        val samples = FloatArray(frameCount * channels)

        var pos = dataOffset
        var outIndex = 0
        for (frame in 0 until frameCount) {
            for (channel in 0 until channels) {
                samples[outIndex++] = when (bitsPerSample) {
                    16 -> {
                        val raw = readInt16LE(bytes, pos)
                        val signed = if (raw >= 0x8000) raw - 0x10000 else raw
                        signed / 32768.0f
                    }
                    else -> {
                        val value = (bytes[pos].toInt() and 0xff) or
                            ((bytes[pos + 1].toInt() and 0xff) shl 8) or
                            ((bytes[pos + 2].toInt() and 0xff) shl 16)
                        val signed = if (value >= 0x800000) value - 0x1000000 else value
                        signed / 8388608.0f
                    }
                }
                pos += bytesPerSample
            }
        }

        return ParsedWav(sampleRate, channels, samples)
    }

    private fun readInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
