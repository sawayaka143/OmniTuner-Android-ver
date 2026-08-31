package com.omnituner.core.data

val SHARP_DISPLAY_NAMES: List<String> = listOf(
    "C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B",
)

val FLAT_DISPLAY_NAMES: List<String> = listOf(
    "C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B",
)

fun midiDisplayName(midi: Int, accidental: String = "sharp"): String {
    val names = if (accidental == "flat") FLAT_DISPLAY_NAMES else SHARP_DISPLAY_NAMES
    val index = ((midi % 12) + 12) % 12
    val octave = kotlin.math.floor(midi / 12.0).toInt() - 1
    return "${names[index]}$octave"
}
