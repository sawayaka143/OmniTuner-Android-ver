package com.omnituner.core.audio

import com.omnituner.core.data.NOTE_NAMES
import com.omnituner.core.data.NamedFrequency
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class StringTarget(
    val name: String,
    val midi: Int,
    val cents: Double,
)

const val HYSTERESIS_CENTS = 10.0

fun midiNoteToFrequency(midiNote: Int, ref: Double = 440.0): Double =
    ref * 2.0.pow((midiNote - 69) / 12.0)

fun frequencyToMidiNote(frequency: Double, ref: Double = 440.0): Int? {
    if (!frequency.isFinite() || frequency <= 0.0) return null
    return (69.0 + 12.0 * log2(frequency / ref)).roundToInt()
}

fun frequencyToMidiFloat(frequency: Double, ref: Double = 440.0): Double? {
    if (!frequency.isFinite() || frequency <= 0.0) return null
    return 69.0 + 12.0 * log2(frequency / ref)
}

fun centsFromMidiFloat(playedMidiFloat: Double?, targetMidi: Int): Double? {
    if (playedMidiFloat == null) return null
    return (playedMidiFloat - targetMidi) * 100.0
}

fun nearestStringTarget(
    playedMidiFloat: Double?,
    strings: List<NamedFrequency>,
    previousName: String? = null,
): StringTarget? {
    if (playedMidiFloat == null || !playedMidiFloat.isFinite() || strings.isEmpty()) {
        return null
    }

    var best: StringTarget? = null
    var bestDistance = Double.POSITIVE_INFINITY
    var previousMidi: Int? = null
    var previousDistance = Double.POSITIVE_INFINITY

    for (string in strings) {
        val midi = frequencyToMidiNote(string.freq) ?: 69
        val cents = centsFromMidiFloat(playedMidiFloat, midi) ?: 0.0
        val distance = abs(cents)
        if (string.name == previousName) {
            previousMidi = midi
            previousDistance = distance
        }
        if (distance < bestDistance) {
            bestDistance = distance
            best = StringTarget(string.name, midi, cents)
        }
    }

    best ?: return null
    if (previousMidi != null && previousDistance <= bestDistance + HYSTERESIS_CENTS) {
        val cents = centsFromMidiFloat(playedMidiFloat, previousMidi) ?: 0.0
        return StringTarget(previousName!!, previousMidi, cents)
    }
    return best
}

fun needlePercentFromCents(cents: Double?): Double {
    if (cents == null || !cents.isFinite()) return 50.0
    return 50.0 + cents.coerceIn(-50.0, 50.0)
}

fun shouldConfirm(inRange: Boolean, elapsedMs: Long, holdMs: Long): Boolean =
    inRange && elapsedMs >= holdMs

fun midiNoteLabel(midiNote: Int): String {
    val semitoneFromA = midiNote - 69
    val noteIndex = ((semitoneFromA % 12) + 12) % 12
    val octave = floor(midiNote / 12.0).toInt() - 1
    return "${NOTE_NAMES[noteIndex]}$octave"
}

private fun formatTwoDecimals(value: Double): String {
    val scaled = (value * 100.0).roundToInt()
    val whole = scaled / 100
    val frac = abs(scaled) % 100
    return "$whole.${frac.toString().padStart(2, '0')}"
}

fun hzDisplay(frequency: Double?): String =
    frequency?.let { "${formatTwoDecimals(it)} Hz" } ?: "— Hz"

fun nearestSemitone(playedMidiFloat: Double?): Int? {
    if (playedMidiFloat == null || !playedMidiFloat.isFinite()) return null
    return playedMidiFloat.roundToInt()
}

fun tuneDirectionText(cents: Double?, threshold: Double = 5.0): String {
    if (cents == null || !cents.isFinite()) return "—"
    if (abs(cents) <= threshold) return "IN TUNE"
    return if (cents < 0) "TUNE UP" else "TUNE DOWN"
}

fun tuneCentsText(cents: Double?, threshold: Double = 5.0): String {
    if (cents == null || !cents.isFinite()) return ""
    if (abs(cents) <= threshold) return ""
    return "${abs(cents.roundToInt())}¢"
}

private val HEX_COLOR = Regex("^#([0-9a-fA-F]{6})$")

fun interpolateColor(from: String, to: String, t: Double): String? {
    fun parse(hex: String): Triple<Int, Int, Int>? {
        val match = HEX_COLOR.matchEntire(hex) ?: return null
        val value = match.groupValues[1].toInt(16)
        return Triple((value shr 16) and 0xff, (value shr 8) and 0xff, value and 0xff)
    }

    val fromRgb = parse(from) ?: return null
    val toRgb = parse(to) ?: return null

    val amount = t.coerceIn(0.0, 1.0)
    val fromChannels = listOf(fromRgb.first, fromRgb.second, fromRgb.third)
    val toChannels = listOf(toRgb.first, toRgb.second, toRgb.third)
    val channels = fromChannels.mapIndexed { index, channel ->
        (channel + (toChannels[index] - channel) * amount).roundToInt()
    }
    return "#" + channels.joinToString("") { it.toString(16).padStart(2, '0') }
}

fun tuneColorProgress(cents: Double?, threshold: Double = 5.0): Double {
    if (cents == null || !cents.isFinite()) return 0.0
    val magnitude = abs(cents)
    val endpoint = max(5.0, threshold)
    if (endpoint >= 50.0) return if (magnitude <= endpoint) 1.0 else 0.0
    return ((50.0 - magnitude) / (50.0 - endpoint)).coerceIn(0.0, 1.0)
}
