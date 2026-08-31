package com.omnituner.core.timing

import kotlin.math.floor
import kotlin.math.roundToInt

data class MeterModel(
    val numerator: Int,
    val denominator: Int,
    val compound: Boolean,
    val divisionsPerBeat: Int,
    val beatsPerBar: Int,
    val beatQuarters: Double,
    val barQuarters: Double,
)

private val NOTE_NAME: Map<Int, String> = mapOf(
    2 to "half",
    4 to "quarter",
    8 to "eighth",
    16 to "sixteenth",
)

private fun noteName(denominator: Int): String = NOTE_NAME[denominator] ?: "1/$denominator"

fun meterModel(numerator: Int, denominator: Int): MeterModel {
    val unitQuarters = 4.0 / denominator
    val compound = numerator % 3 == 0 && numerator >= 6 && denominator >= 8
    val divisionsPerBeat = if (compound) 3 else 1
    val beatsPerBar = if (compound) numerator / 3 else numerator
    return MeterModel(
        numerator = numerator,
        denominator = denominator,
        compound = compound,
        divisionsPerBeat = divisionsPerBeat,
        beatsPerBar = beatsPerBar,
        beatQuarters = unitQuarters * divisionsPerBeat,
        barQuarters = unitQuarters * numerator,
    )
}

fun describeMeter(model: MeterModel): String {
    if (model.compound) {
        return "Compound ${model.numerator}/${model.denominator} — ${model.beatsPerBar} " +
            "dotted-${noteName(model.denominator)} beats per bar"
    }
    val plural = if (model.beatsPerBar > 1) "s" else ""
    return "${model.numerator}/${model.denominator} — ${model.beatsPerBar} " +
        "${noteName(model.denominator)}-note beat$plural per bar"
}

data class BarEvent(
    val beats: Double,
    val layer: String,
    val role: String,
)

fun buildBarEvents(
    model: MeterModel,
    subdivision: Int = 1,
    poly: PolyEvents? = null,
): List<BarEvent> {
    val events = mutableListOf<BarEvent>()
    for (b in 0 until model.beatsPerBar) {
        for (s in 0 until subdivision) {
            events.add(
                BarEvent(
                    beats = b + s / subdivision.toDouble(),
                    layer = "meter",
                    role = if (b == 0 && s == 0) "downbeat" else if (s == 0) "beat" else "subdivision",
                ),
            )
        }
    }
    if (poly != null && poly.enabled && poly.events > 0) {
        val span = model.beatsPerBar
        for (i in 0 until poly.events) {
            events.add(
                BarEvent(
                    beats = (i * span) / poly.events.toDouble(),
                    layer = "poly",
                    role = if (i == 0 && poly.accentFirst) "polyAccent" else "poly",
                ),
            )
        }
    }
    events.sortWith { a, b ->
        if (a.beats != b.beats) a.beats.compareTo(b.beats)
        else if (a.layer == "meter") -1 else 1
    }
    return events
}

data class PolyEvents(
    val enabled: Boolean,
    val events: Int,
    val accentFirst: Boolean = true,
)

fun quarterDuration(bpm: Double): Double = 60.0 / bpm

fun beatDuration(bpm: Double, model: MeterModel): Double = (60.0 / bpm) * model.beatQuarters

fun barDuration(bpm: Double, model: MeterModel): Double = (60.0 / bpm) * model.barQuarters

fun subdivisionInterval(bpm: Double, model: MeterModel, divisionsPerBeat: Int): Double =
    beatDuration(bpm, model) / divisionsPerBeat

fun ticksPerBar(model: MeterModel, divisionsPerBeat: Int): Int =
    model.beatsPerBar * divisionsPerBeat

typealias TickKind = String

fun tickKind(tickIndexInBar: Int, divisionsPerBeat: Int): TickKind = when {
    tickIndexInBar == 0 -> "downbeat"
    tickIndexInBar % divisionsPerBeat == 0 -> "beat"
    else -> "subdivision"
}

fun polyTimes(barStart: Double, barDur: Double, count: Int): List<Double> {
    if (count <= 0) return emptyList()
    val out = mutableListOf<Double>()
    val step = barDur / count
    for (i in 0 until count) out.add(barStart + i * step)
    return out
}

fun isBarAudible(barIndex: Int, pattern: List<Int>): Boolean {
    if (pattern.isEmpty()) return true
    return pattern[barIndex % pattern.size] == 1
}

fun tapBpm(intervalsMs: List<Double>): Double? {
    if (intervalsMs.isEmpty()) return null
    val valid = intervalsMs.filter { it > 120 && it < 2500 }
    if (valid.isEmpty()) return null
    val avg = valid.sum() / valid.size
    return (60000.0 / avg).roundToInt().toDouble()
}

fun formatBarDuration(ms: Double): String {
    if (ms < 1000) return "${ms.roundToInt()} ms"
    val seconds = ms / 1000.0
    val whole = seconds.toInt()
    val frac = ((seconds - whole) * 100).roundToInt()
    return "$whole.${frac.toString().padStart(2, '0')} s"
}

fun getTempoMarking(bpm: Double): String = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Lento"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

private fun Double.roundToInt(): Int = floor(this + 0.5).toInt()
