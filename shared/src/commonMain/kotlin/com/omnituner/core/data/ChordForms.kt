package com.omnituner.core.data

/**
 * A familiar six-string grip, written as fret offsets from the fret where string [anchor]
 * sounds the chord root (null = muted). The voicing search instantiates the forms that fit the
 * chord's quality and string count and gives a matching shape a small ranking bonus.
 */
data class ChordForm(
    val id: String,
    val qualities: List<String>,
    val anchor: Int,
    val offsets: List<Int?>,
)

val CHORD_FORMS: List<ChordForm> = listOf(
    ChordForm("maj-e", listOf("maj"), 0, listOf(0, 2, 2, 1, 0, 0)),
    ChordForm("maj-a", listOf("maj"), 1, listOf(null, 0, 2, 2, 2, 0)),
    ChordForm("min-em", listOf("min"), 0, listOf(0, 2, 2, 0, 0, 0)),
    ChordForm("min-am", listOf("min"), 1, listOf(null, 0, 2, 2, 1, 0)),
    ChordForm("7-e", listOf("7"), 0, listOf(0, 2, 0, 1, 0, 0)),
    ChordForm("7-a", listOf("7"), 1, listOf(null, 0, 2, 0, 2, 0)),
    ChordForm("maj7-e", listOf("maj7"), 0, listOf(0, null, 1, 1, 0, null)),
    ChordForm("maj7-a", listOf("maj7"), 1, listOf(null, 0, 2, 1, 2, 0)),
    ChordForm("m7-em", listOf("m7"), 0, listOf(0, 2, 0, 0, 0, 0)),
    ChordForm("m7-a", listOf("m7"), 1, listOf(null, 0, 2, 0, 1, 0)),
    ChordForm("m7b5-a", listOf("m7b5"), 1, listOf(null, 0, 1, 0, 1, null)),
    ChordForm("dim7-d", listOf("dim7"), 2, listOf(null, null, 0, 1, 0, 1)),
    ChordForm("13-e", listOf("13"), 0, listOf(0, null, 0, 1, 2, null)),
    ChordForm("7b13-e", listOf("7b13"), 0, listOf(0, null, 0, 1, 1, null)),
)
