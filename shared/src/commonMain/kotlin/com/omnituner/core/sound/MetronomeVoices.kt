package com.omnituner.core.sound

import com.omnituner.core.audio.BiquadFilter
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

data class MetronomeVoiceOption(
    val id: String,
    val label: String,
)

object MetronomeVoices {

    const val TAIL_SECONDS = 0.02

    const val MIN_VELOCITY = 0.0001
    const val MAX_VELOCITY = 1.2

    private val OPTIONS: List<MetronomeVoiceOption> = listOf(
        MetronomeVoiceOption("beep-hi", "Beep · high"),
        MetronomeVoiceOption("beep-mid", "Beep · mid"),
        MetronomeVoiceOption("beep-lo", "Beep · low"),
        MetronomeVoiceOption("wood", "Woodblock"),
        MetronomeVoiceOption("clave", "Clave"),
        MetronomeVoiceOption("rim", "Rimshot"),
        MetronomeVoiceOption("snare", "Snare"),
        MetronomeVoiceOption("shaker", "Shaker"),
        MetronomeVoiceOption("cowbell", "Cowbell"),
        MetronomeVoiceOption("click", "Click"),
    )

    private val IDS: Set<String> = OPTIONS.map { it.id }.toSet()

    fun options(): List<MetronomeVoiceOption> = OPTIONS

    fun has(id: String): Boolean = id in IDS

    fun clampVelocity(velocity: Double): Double =
        velocity.coerceIn(MIN_VELOCITY, MAX_VELOCITY)

    fun render(
        id: String,
        sampleRate: Double,
        velocity: Double,
        random: Random = Random.Default,
    ): FloatArray {
        val vel = clampVelocity(velocity)
        val voices = when (id) {
            "beep-hi" -> listOf(tone(1760.0, dur = 0.05))
            "beep-mid" -> listOf(tone(1244.5, dur = 0.05))
            "beep-lo" -> listOf(tone(932.3, dur = 0.045))
            "wood" -> listOf(tone(1150.0, endFreq = 540.0, dur = 0.04))
            "clave" -> listOf(tone(2480.0, endFreq = 2000.0, dur = 0.035))
            "rim" -> listOf(
                noiseHit(dur = 0.025, highpass = 2800.0, gain = 0.7, random = random),
                tone(480.0, dur = 0.03, gain = 0.8),
            )
            "snare" -> listOf(
                noiseHit(dur = 0.09, bandpass = 1800.0, q = 0.7, random = random),
                tone(196.0, type = Waveform.TRIANGLE, dur = 0.05, gain = 0.6),
            )
            "shaker" -> listOf(
                noiseHit(dur = 0.06, highpass = 5200.0, attack = 0.006, gain = 0.85, random = random),
            )
            "cowbell" -> listOf(cowbell())
            "click" -> listOf(tone(1200.0, type = Waveform.SQUARE, dur = 0.04, gain = 0.9))
            else -> listOf(tone(1244.5, dur = 0.05))
        }
        return mix(voices, sampleRate, vel)
    }

    private enum class Waveform { SINE, SQUARE, TRIANGLE }

    private sealed interface Layer

    private data class Tone(
        val freq: Double,
        val endFreq: Double? = null,
        val dur: Double,
        val type: Waveform = Waveform.SINE,
        val gain: Double = 1.0,
        val attack: Double = 0.002,
    ) : Layer

    private data class Noise(
        val dur: Double,
        val highpass: Double? = null,
        val bandpass: Double? = null,
        val q: Double = 1.0,
        val gain: Double = 1.0,
        val attack: Double = 0.002,
        val random: Random,
    ) : Layer

    private data class Cowbell(val attack: Double = 0.001, val dur: Double = 0.16) : Layer

    private fun tone(
        freq: Double,
        endFreq: Double? = null,
        dur: Double = 0.05,
        type: Waveform = Waveform.SINE,
        gain: Double = 1.0,
        attack: Double = 0.002,
    ): Layer = Tone(freq, endFreq, dur, type, gain, attack)

    private fun noiseHit(
        dur: Double = 0.08,
        highpass: Double? = null,
        bandpass: Double? = null,
        q: Double = 1.0,
        gain: Double = 1.0,
        attack: Double = 0.002,
        random: Random,
    ): Layer = Noise(dur, highpass, bandpass, q, gain, attack, random)

    private fun cowbell(): Layer = Cowbell()

    private fun mix(layers: List<Layer>, sampleRate: Double, vel: Double): FloatArray {
        val totalSeconds = layers.maxOf { layerDuration(it) }
        val out = DoubleArray(totalLength(totalSeconds, sampleRate))

        for (layer in layers) {
            val buffer = renderLayer(layer, sampleRate, vel)
            for (i in buffer.indices) {
                if (i < out.size) out[i] += buffer[i]
            }
        }

        return FloatArray(out.size) { out[it].toFloat() }
    }

    private fun totalLength(seconds: Double, sampleRate: Double): Int =
        kotlin.math.ceil(seconds * sampleRate).toInt().coerceAtLeast(1)

    private fun layerDuration(layer: Layer): Double = when (layer) {
        is Tone -> layer.dur + TAIL_SECONDS
        is Noise -> layer.dur + TAIL_SECONDS
        is Cowbell -> 0.22 + TAIL_SECONDS
    }

    private fun expRamp(a: Double, b: Double, t: Double, d: Double): Double {
        if (t <= 0) return a
        if (t >= d) return b
        if (a <= 0 || b <= 0) return b
        val ratio = b / a
        return a * ratio.pow(t / d)
    }

    private fun renderLayer(layer: Layer, sampleRate: Double, vel: Double): FloatArray {
        return when (layer) {
            is Tone -> renderTone(layer, sampleRate, vel)
            is Noise -> renderNoise(layer, sampleRate, vel)
            is Cowbell -> renderCowbell(layer, sampleRate, vel)
        }
    }

    private fun renderTone(layer: Tone, sampleRate: Double, vel: Double): FloatArray {
        val n = totalLength(layerDuration(layer), sampleRate)
        val out = FloatArray(n)
        val peak = (vel * layer.gain).coerceAtLeast(0.0001)
        val sweepEnd = layer.endFreq?.let { layer.dur * 0.85 }
        var phase = 0.0

        for (i in 0 until n) {
            val t = i / sampleRate
            val freq = if (sweepEnd != null) {
                if (t >= sweepEnd) layer.endFreq!!
                else expRamp(layer.freq, layer.endFreq!!, t, sweepEnd)
            } else {
                layer.freq
            }
            phase += 2.0 * PI * freq / sampleRate

            val env = envelope(t, peak, layer.attack, layer.dur)
            val wave = when (layer.type) {
                Waveform.SINE -> sin(phase)
                Waveform.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
                Waveform.TRIANGLE -> 2.0 / PI * asin(sin(phase))
            }
            out[i] = (wave * env).toFloat()
        }
        return out
    }

    private fun renderNoise(layer: Noise, sampleRate: Double, vel: Double): FloatArray {
        val n = totalLength(layerDuration(layer), sampleRate)
        val filter = BiquadFilter()
        when {
            layer.highpass != null -> filter.setHighpass(sampleRate, layer.highpass, 0.7)
            layer.bandpass != null -> filter.setBandpass(sampleRate, layer.bandpass, layer.q)
            else -> filter.setBandpass(sampleRate, 2000.0, layer.q)
        }
        val peak = (vel * layer.gain).coerceAtLeast(0.0001)
        val out = FloatArray(n)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        val random = layer.random

        for (i in 0 until n) {
            val t = i / sampleRate
            val x0 = random.nextDouble() * 2 - 1
            val y0 = filter.b0 * x0 + filter.b1 * x1 + filter.b2 * x2 - filter.a1 * y1 - filter.a2 * y2
            val env = envelope(t, peak, layer.attack, layer.dur)
            out[i] = (y0 * env).toFloat()
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }

    private fun renderCowbell(layer: Cowbell, sampleRate: Double, vel: Double): FloatArray {
        val n = totalLength(layerDuration(layer), sampleRate)
        val filter = BiquadFilter()
        filter.setBandpass(sampleRate, 760.0, 1.4)
        val peak = (vel * 0.8).coerceAtLeast(0.0001)
        val out = FloatArray(n)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until n) {
            val t = i / sampleRate
            phase1 += 2.0 * PI * 556.0 / sampleRate
            phase2 += 2.0 * PI * 833.0 / sampleRate
            val square1 = if (sin(phase1) >= 0) 1.0 else -1.0
            val square2 = if (sin(phase2) >= 0) 1.0 else -1.0
            val x0 = square1 + square2
            val y0 = filter.b0 * x0 + filter.b1 * x1 + filter.b2 * x2 - filter.a1 * y1 - filter.a2 * y2
            val env = envelope(t, peak, layer.attack, layer.dur)
            out[i] = (y0 * env).toFloat()
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }

    private fun envelope(t: Double, peak: Double, attack: Double, dur: Double): Double {
        if (t < attack) {
            return expRamp(0.0001, peak, t, attack)
        }
        if (t < dur) {
            val clamped = t.coerceAtMost(dur)
            return expRamp(peak, 0.0001, clamped - attack, (dur - attack).coerceAtLeast(1e-9))
        }
        return 0.0001
    }
}
