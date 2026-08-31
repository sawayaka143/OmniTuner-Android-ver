package com.omnituner.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.omnituner.core.sound.NoteSynth
import java.util.concurrent.Executors

/**
 * One-shot AudioTrack player for the note synth and the in-tune chime.
 * Sample playback (guitar WAVs) is added in M5; this covers the synth paths.
 */
class NotePlayer {

    private val releaseExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "omnituner-note-release").apply { isDaemon = true }
    }

    fun playChime() {
        play(NoteSynth.renderChime(SAMPLE_RATE.toDouble()))
    }

    fun playNote(midi: Int, durationSeconds: Double = 0.55) {
        play(NoteSynth.renderNote(midi, SAMPLE_RATE.toDouble(), durationSeconds))
    }

    fun play(buffer: FloatArray) {
        if (buffer.isEmpty()) return
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
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * Float.SIZE_BYTES)
            .build()

        val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        if (written < 0) {
            track.release()
            return
        }
        track.play()

        val durationMs = buffer.size * 1000L / SAMPLE_RATE + RELEASE_GRACE_MS
        releaseExecutor.execute {
            try {
                Thread.sleep(durationMs)
            } catch (_: InterruptedException) {
            }
            try {
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val RELEASE_GRACE_MS = 150L
    }
}
