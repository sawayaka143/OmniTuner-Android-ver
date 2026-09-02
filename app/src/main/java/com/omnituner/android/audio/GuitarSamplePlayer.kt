package com.omnituner.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.omnituner.core.audiofile.WavParser
import com.omnituner.core.data.GuitarSample
import com.omnituner.core.data.guitarSamplePlaybackRate
import com.omnituner.core.data.nearestGuitarSample

class GuitarSamplePlayer(private val context: Context) {

    private val cache = mutableMapOf<String, DecodedSample>()

    private data class DecodedSample(
        val sampleRate: Int,
        val samples: FloatArray,
    )

    fun preload(): Boolean {
        val sample = com.omnituner.core.data.GUITAR_SAMPLES.firstOrNull() ?: return false
        return load(sample) != null
    }

    fun hasSample(midi: Int): Boolean = nearestGuitarSample(midi) != null

    fun playSampleNote(midi: Int, durationSeconds: Double = defaultDuration()): Boolean {
        val sample = nearestGuitarSample(midi) ?: return false
        val decoded = load(sample) ?: return false

        val rate = guitarSamplePlaybackRate(midi, sample.rootMidi)
        val outputRate = (decoded.sampleRate * rate).toInt().coerceIn(8000, 192000)
        val endFrame = minOf(
            decoded.samples.size,
            (durationSeconds * decoded.sampleRate * rate).toInt().coerceAtLeast(1),
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(outputRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(endFrame * Float.SIZE_BYTES)
            .build()

        val written = track.write(decoded.samples, 0, endFrame, AudioTrack.WRITE_BLOCKING)
        if (written < 0) {
            track.release()
            return false
        }
        track.play()

        Thread {
            try {
                Thread.sleep(endFrame * 1000L / outputRate + NotePlayer.RELEASE_GRACE_MS)
            } catch (_: InterruptedException) {
            }
            try {
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }.apply { isDaemon = true }.start()
        return true
    }

    private fun defaultDuration(): Double = 1.6

    private fun load(sample: GuitarSample): DecodedSample? {
        cache[sample.file]?.let { return it }
        return try {
            val bytes = context.assets.open("audio/guitar/${sample.file}").use { input ->
                input.readBytes()
            }
            val parsed = WavParser.parse(bytes)
            val decoded = DecodedSample(parsed.sampleRate, parsed.samples)
            cache[sample.file] = decoded
            decoded
        } catch (_: Exception) {
            null
        }
    }
}
