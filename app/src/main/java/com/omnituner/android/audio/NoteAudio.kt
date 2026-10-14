package com.omnituner.android.audio

import android.content.Context
import android.media.AudioAttributes
import com.omnituner.core.audiofile.WavParser
import com.omnituner.core.data.GuitarSample
import com.omnituner.core.data.guitarSamplePlaybackRate
import com.omnituner.core.data.nearestGuitarSample
import com.omnituner.core.sound.NoteSynth

class NoteAudio(private val context: Context) {

    private val decoded = HashMap<String, DecodedSample>()

    fun playChime() {
        val samples = NoteSynth.renderChime(PcmOutput.SAMPLE_RATE.toDouble())
        PcmOutput.playOnce(samples, samples.size, PcmOutput.SAMPLE_RATE, AudioAttributes.CONTENT_TYPE_MUSIC)
    }

    fun playNote(midi: Int, durationSeconds: Double? = null) {
        if (playSample(midi, durationSeconds ?: SAMPLE_DURATION_SECONDS)) return
        val samples = NoteSynth.renderNote(
            midi,
            PcmOutput.SAMPLE_RATE.toDouble(),
            durationSeconds ?: SYNTH_DURATION_SECONDS,
        )
        PcmOutput.playOnce(samples, samples.size, PcmOutput.SAMPLE_RATE, AudioAttributes.CONTENT_TYPE_MUSIC)
    }

    private fun playSample(midi: Int, durationSeconds: Double): Boolean {
        val sample = nearestGuitarSample(midi) ?: return false
        val source = decode(sample) ?: return false
        val rate = guitarSamplePlaybackRate(midi, sample.rootMidi)
        val outputRate = (source.sampleRate * rate).toInt().coerceIn(8000, 192000)
        val frames = minOf(
            source.samples.size,
            (durationSeconds * source.sampleRate * rate).toInt().coerceAtLeast(1),
        )
        PcmOutput.playOnce(source.samples, frames, outputRate, AudioAttributes.CONTENT_TYPE_MUSIC)
        return true
    }

    private fun decode(sample: GuitarSample): DecodedSample? {
        decoded[sample.file]?.let { return it }
        return try {
            val bytes = context.assets.open("audio/guitar/${sample.file}").use { it.readBytes() }
            val parsed = WavParser.parse(bytes)
            DecodedSample(parsed.sampleRate, parsed.samples).also { decoded[sample.file] = it }
        } catch (_: Exception) {
            null
        }
    }

    private class DecodedSample(val sampleRate: Int, val samples: FloatArray)

    private companion object {
        const val SAMPLE_DURATION_SECONDS = 1.6
        const val SYNTH_DURATION_SECONDS = 0.55
    }
}
