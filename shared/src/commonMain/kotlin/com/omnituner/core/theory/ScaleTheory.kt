package com.omnituner.core.theory

import com.omnituner.core.data.FLAT_NAMES
import com.omnituner.core.data.SHARP_NAMES
import com.omnituner.core.data.ScaleInterval
import com.omnituner.core.data.colorForLabel

data class FretCell(
    val stringIndex: Int,
    val fret: Int,
    val pitchClass: Int,
    val midi: Int?,
    val interval: ScaleInterval?,
    val noteName: String,
    val color: String,
    val isRoot: Boolean,
)

private fun normalizeNote(input: String): String {
    val trimmed = input.trim().replace("♭", "b").replace("♯", "#")
    if (trimmed.isEmpty()) return trimmed
    return trimmed.substring(0, 1).uppercase() + trimmed.substring(1)
}

private val SHARP_INDEX: Map<String, Int> =
    SHARP_NAMES.withIndex().associate { (index, name) -> name to index }

private val FLAT_INDEX: Map<String, Int> =
    FLAT_NAMES.withIndex().associate { (index, name) -> name to index }

fun parseNote(input: String): Int? {
    if (input.isEmpty()) return null
    val normalized = normalizeNote(input)

    val withoutOctave = normalized.replace(Regex("[0-9].*$"), "")

    return SHARP_INDEX[withoutOctave] ?: FLAT_INDEX[withoutOctave]
}

fun noteName(pitchClass: Int, preferFlats: Boolean): String {
    val index = ((pitchClass % 12) + 12) % 12
    return (if (preferFlats) FLAT_NAMES else SHARP_NAMES)[index]
}

fun intervalByPitchClass(intervals: List<ScaleInterval>): Map<Int, ScaleInterval> {
    val map = mutableMapOf<Int, ScaleInterval>()
    for (interval in intervals) {
        val pc = ((interval.semitones % 12) + 12) % 12
        map[pc] = interval
    }
    return map
}

fun computeFretboard(
    openPitchClasses: List<Int>,
    fretCount: Int,
    intervals: List<ScaleInterval>,
    preferFlats: Boolean,
    openMidiNotes: List<Int>? = null,
): List<List<FretCell>> {
    val intervalMap = intervalByPitchClass(intervals)

    val board = mutableListOf<List<FretCell>>()
    for (stringIndex in openPitchClasses.indices) {
        val openPc = ((openPitchClasses[stringIndex] % 12) + 12) % 12
        val row = mutableListOf<FretCell>()
        for (fret in 0..fretCount) {
            val pitchClass = (((openPc + fret) % 12) + 12) % 12
            val interval = intervalMap[pitchClass]
            row.add(
                FretCell(
                    stringIndex = stringIndex,
                    fret = fret,
                    pitchClass = pitchClass,
                    midi = openMidiNotes?.getOrNull(stringIndex)?.plus(fret),
                    interval = interval,
                    noteName = noteName(pitchClass, preferFlats),
                    color = if (interval != null) colorForLabel(interval.label) else "",
                    isRoot = interval?.label == "R" || interval?.label == "1",
                ),
            )
        }
        board.add(row)
    }
    return board
}
