package com.omnituner.core.data

data class ScaleInterval(
    val semitones: Int,
    val label: String,
)

data class Scale(
    val id: String,
    val label: String,
    val group: String,
    val intervals: List<ScaleInterval>,
)

val SHARP_NAMES: List<String> = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
)

val FLAT_NAMES: List<String> = listOf(
    "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
)

val SCALES: List<Scale> = listOf(
    Scale(
        id = "major",
        label = "Major",
        group = "Major & minor",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(4, "3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(9, "6"),
            ScaleInterval(11, "7"),
        ),
    ),
    Scale(
        id = "natural-minor",
        label = "Minor",
        group = "Major & minor",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(8, "♭6"),
            ScaleInterval(10, "♭7"),
        ),
    ),
    Scale(
        id = "harmonic-minor",
        label = "Harmonic minor",
        group = "Major & minor",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(8, "♭6"),
            ScaleInterval(11, "7"),
        ),
    ),
    Scale(
        id = "melodic-minor",
        label = "Melodic minor",
        group = "Major & minor",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(9, "6"),
            ScaleInterval(11, "7"),
        ),
    ),
    Scale(
        id = "dorian",
        label = "Dorian",
        group = "Modes",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(9, "6"),
            ScaleInterval(10, "♭7"),
        ),
    ),
    Scale(
        id = "phrygian",
        label = "Phrygian",
        group = "Modes",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(1, "♭2"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(8, "♭6"),
            ScaleInterval(10, "♭7"),
        ),
    ),
    Scale(
        id = "lydian",
        label = "Lydian",
        group = "Modes",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(4, "3"),
            ScaleInterval(6, "♯4"),
            ScaleInterval(7, "5"),
            ScaleInterval(9, "6"),
            ScaleInterval(11, "7"),
        ),
    ),
    Scale(
        id = "mixolydian",
        label = "Mixolydian",
        group = "Modes",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(4, "3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(9, "6"),
            ScaleInterval(10, "♭7"),
        ),
    ),
    Scale(
        id = "locrian",
        label = "Locrian",
        group = "Modes",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(1, "♭2"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(6, "♭5"),
            ScaleInterval(8, "♭6"),
            ScaleInterval(10, "♭7"),
        ),
    ),
    Scale(
        id = "major-pentatonic",
        label = "Major pentatonic",
        group = "Pentatonic & blues",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(2, "2"),
            ScaleInterval(4, "3"),
            ScaleInterval(7, "5"),
            ScaleInterval(9, "6"),
        ),
    ),
    Scale(
        id = "minor-pentatonic",
        label = "Minor pentatonic",
        group = "Pentatonic & blues",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(7, "5"),
            ScaleInterval(10, "♭7"),
        ),
    ),
    Scale(
        id = "blues",
        label = "Blues",
        group = "Pentatonic & blues",
        intervals = listOf(
            ScaleInterval(0, "1"),
            ScaleInterval(3, "♭3"),
            ScaleInterval(5, "4"),
            ScaleInterval(6, "♭5"),
            ScaleInterval(7, "5"),
            ScaleInterval(10, "♭7"),
        ),
    ),
)
