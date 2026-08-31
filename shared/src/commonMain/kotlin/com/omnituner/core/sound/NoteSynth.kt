package com.omnituner.core.sound

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure PCM renderers for scale playback, mirroring scale-playback.ts:
 * triangle osc -> lowpass 2600 Hz -> envelope (12 ms attack to 0.22,
 * exponential decay, min duration 0.08 s). The in-tune chime is
 * A4 (0.12 s, 0.65) + E5 (+60 ms, 0.08 s, 0.7).
 */
object NoteSynth {

    const val ATTACK_SECONDS = 0.012
    const val PEAK = 0.22
    const val MIN_DURATION = 0.08
    const val LOWPASS_HZ = 2600.0
    const val STOP_TAIL_SECONDS = 0.05

    fun midiToFrequency(midi: Int, ref: Double = 440.0): Double =
        ref * 2.0.pow((midi - 69) / 12.0)

    fun noteLengthSeconds(durationSeconds: Double): Double =
        (durationSeconds.coerceAtLeast(MIN_DURATION)) + STOP_TAIL_SECONDS

    fun renderNote(
        midi: Int,
        sampleRate: Double,
        durationSeconds: Double = 0.55,
        ref: Double = 440.0,
    ): FloatArray {
        val duration = durationSeconds.coerceAtLeast(MIN_DURATION)
        return renderTone(
            frequency = midiToFrequency(midi, ref),
            sampleRate = sampleRate,
            attack = ATTACK_SECONDS,
            peak = PEAK,
            duration = duration,
            lowpassHz = LOWPASS_HZ,
        )
    }

    data class ChimeTone(
        val frequency: Double,
        val startSeconds: Double,
        val duration: Double,
        val peak: Double,
    )

    fun chimeTones(ref: Double = 440.0): List<ChimeTone> = listOf(
        ChimeTone(midiToFrequency(69, ref), 0.0, 0.12, 0.65),
        ChimeTone(midiToFrequency(76, ref), 0.06, 0.08, 0.7),
    )

    fun chimeLengthSeconds(): Double =
        chimeTones().maxOf { it.startSeconds + it.duration + STOP_TAIL_SECONDS }

    fun renderChime(sampleRate: Double, ref: Double = 440.0): FloatArray {
        val tones = chimeTones(ref)
        val total = ceil(chimeLengthSeconds() * sampleRate).toInt().coerceAtLeast(1)
        val out = DoubleArray(total)
        for (tone in tones) {
            val buffer = renderPlainTone(tone.frequency, sampleRate, tone.peak, tone.duration)
            val offset = (tone.startSeconds * sampleRate).toInt()
            for (i in buffer.indices) {
                val index = offset + i
                if (index < total) out[index] += buffer[i]
            }
        }
        return FloatArray(total) { out[it].toFloat() }
    }

    /** 14 ms linear attack, exponential decay to 0.0001, no lowpass (chime path). */
    private fun renderPlainTone(
        frequency: Double,
        sampleRate: Double,
        peak: Double,
        duration: Double,
    ): DoubleArray {
        val n = ceil((duration + STOP_TAIL_SECONDS) * sampleRate).toInt().coerceAtLeast(1)
        val out = DoubleArray(n)
        val attack = 0.014
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / sampleRate
            phase += 2.0 * PI * frequency / sampleRate
            val env = when {
                t < attack -> 0.0001 + (peak - 0.0001) * (t / attack)
                t < duration -> peak * 0.0001.let { floor ->
                    val progress = (t - attack) / ((duration - attack).coerceAtLeast(1e-9))
                    (floor / peak).pow(progress)
                }
                else -> 0.0001
            }
            out[i] = (2.0 / PI * asin(sin(phase))) * env
        }
        return out
    }

    private fun renderTone(
        frequency: Double,
        sampleRate: Double,
        attack: Double,
        peak: Double,
        duration: Double,
        lowpassHz: Double,
    ): FloatArray {
        val n = ceil(noteLengthSeconds(duration) * sampleRate).toInt().coerceAtLeast(1)
        val filter = com.omnituner.core.audio.BiquadFilter()
        filter.setLowpass(sampleRate, lowpassHz, 0.7)
        val out = FloatArray(n)
        var phase = 0.0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in 0 until n) {
            val t = i / sampleRate
            phase += 2.0 * PI * frequency / sampleRate
            val triangle = 2.0 / PI * asin(sin(phase))
            val x0 = triangle
            val y0 = filter.b0 * x0 + filter.b1 * x1 + filter.b2 * x2 - filter.a1 * y1 - filter.a2 * y2
            val env = when {
                t < attack -> 0.0001 + (peak - 0.0001) * (t / attack)
                t < duration -> peak * (0.0001 / peak).pow((t - attack) / (duration - attack))
                else -> 0.0001
            }
            out[i] = (y0 * env).toFloat()
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }
}
