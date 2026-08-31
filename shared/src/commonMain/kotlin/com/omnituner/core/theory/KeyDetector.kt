package com.omnituner.core.theory

data class DetectedKey(
    val tonicPc: Int,
    val tonicName: String,
    val mode: ModeName,
    val score: Int,
    val coverage: Double,
    val confidence: String,
    val alternatives: List<DetectedKey>,
)

val MODE_PRIORITY: Map<ModeName, Int> = mapOf(
    ModeName.Ionian to 0,
    ModeName.Aeolian to 1,
    ModeName.Dorian to 2,
    ModeName.Mixolydian to 3,
    ModeName.Lydian to 4,
    ModeName.Phrygian to 5,
    ModeName.Locrian to 6,
)

private fun confidenceFor(coverage: Double): String = when {
    coverage >= 0.85 -> "strong"
    coverage >= 0.6 -> "moderate"
    else -> "weak"
}

fun rankKeys(chords: List<ParsedChord>, tuningFlats: Boolean? = null): List<DetectedKey> {
    if (chords.isEmpty()) return emptyList()
    val hasFlatChord = chords.any { it.flats }
    val candidates = mutableListOf<DetectedKey>()

    for (tonicPc in 0 until 12) {
        val flatsForTonic = flatsForPc(tonicPc)
        val useFlats = hasFlatChord || (tuningFlats ?: flatsForTonic)
        val tonicName = pcName(tonicPc, useFlats || tuningFlats == true)
        for (mode in MODE_NAMES) {
            var score = 0
            var good = 0
            for (chord in chords) {
                val badge = computeBadgeForPc(chord, tonicPc, mode, tuningFlats == true, useFlats)
                if (badge == null) continue
                when (badge.kind) {
                    "good" -> {
                        score += 2
                        good++
                    }
                    "warn" -> score += 1
                }
            }
            val coverage = good.toDouble() / chords.size
            candidates.add(
                DetectedKey(
                    tonicPc = tonicPc,
                    tonicName = tonicName,
                    mode = mode,
                    score = score,
                    coverage = coverage,
                    confidence = confidenceFor(coverage),
                    alternatives = emptyList(),
                ),
            )
        }
    }

    candidates.sortWith(
        compareByDescending<DetectedKey> { it.score }
            .thenByDescending { it.coverage }
            .thenBy { MODE_PRIORITY[it.mode] ?: 99 }
            .thenBy { it.tonicPc },
    )

    return candidates
}

fun detectKey(chords: List<ParsedChord>, tuningFlats: Boolean? = null): DetectedKey? {
    val ranked = rankKeys(chords, tuningFlats)
    if (ranked.isEmpty()) return null
    val top = ranked[0]
    val alts = ranked.drop(1).take(2)
    return if (top.score == 0) {
        top.copy(confidence = "weak", alternatives = alts)
    } else {
        top.copy(alternatives = alts)
    }
}
