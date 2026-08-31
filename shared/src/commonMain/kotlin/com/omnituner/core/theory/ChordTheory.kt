package com.omnituner.core.theory

const val SHARP_PC_NAMES_COUNT = 12

val SHARP_PC_NAMES: List<String> = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
)

val FLAT_PC_NAMES: List<String> = listOf(
    "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
)

val DEGREE_LABELS: List<String> = listOf(
    "R", "b2", "2", "b3", "3", "4", "b5", "5", "#5", "6", "b7", "7",
)

val PC_LETTER: Map<String, Int> = mapOf(
    "C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11,
)

data class ChordFormula(
    val intervals: List<Int>,
    val optional: List<Int> = emptyList(),
)

val CHORD_FORMULAS: Map<String, ChordFormula> = mapOf(
    "maj" to ChordFormula(listOf(0, 4, 7)),
    "6" to ChordFormula(listOf(0, 4, 7, 9)),
    "6/9" to ChordFormula(listOf(0, 4, 7, 9, 14)),
    "maj7" to ChordFormula(listOf(0, 4, 7, 11)),
    "maj9" to ChordFormula(listOf(0, 4, 7, 11, 14)),
    "maj11" to ChordFormula(listOf(0, 4, 7, 11, 14, 17), listOf(17)),
    "maj13" to ChordFormula(listOf(0, 4, 7, 11, 14, 17, 21), listOf(17, 21)),
    "maj7#11" to ChordFormula(listOf(0, 4, 7, 11, 18), listOf(18)),
    "maj13#11" to ChordFormula(listOf(0, 4, 7, 11, 14, 18, 21), listOf(18, 21)),
    "maj7#5" to ChordFormula(listOf(0, 4, 8, 11)),
    "maj9#5" to ChordFormula(listOf(0, 4, 8, 11, 14)),

    "7" to ChordFormula(listOf(0, 4, 7, 10)),
    "9" to ChordFormula(listOf(0, 4, 7, 10, 14)),
    "11" to ChordFormula(listOf(0, 4, 7, 10, 14, 17), listOf(17)),
    "13" to ChordFormula(listOf(0, 4, 7, 10, 14, 17, 21), listOf(17, 21)),
    "7b5" to ChordFormula(listOf(0, 4, 6, 10)),
    "7#5" to ChordFormula(listOf(0, 4, 8, 10)),
    "7b9" to ChordFormula(listOf(0, 4, 7, 10, 13)),
    "7#9" to ChordFormula(listOf(0, 4, 7, 10, 15)),
    "7#11" to ChordFormula(listOf(0, 4, 7, 10, 18), listOf(18)),
    "7b13" to ChordFormula(listOf(0, 4, 7, 10, 20), listOf(20)),
    "9#11" to ChordFormula(listOf(0, 4, 7, 10, 14, 18), listOf(18)),
    "13b9" to ChordFormula(listOf(0, 4, 7, 10, 13, 21), listOf(21)),
    "7#9b13" to ChordFormula(listOf(0, 4, 7, 10, 15, 20), listOf(20)),

    "min" to ChordFormula(listOf(0, 3, 7)),
    "m6" to ChordFormula(listOf(0, 3, 7, 9)),
    "m6/9" to ChordFormula(listOf(0, 3, 7, 9, 14)),
    "m7" to ChordFormula(listOf(0, 3, 7, 10)),
    "m9" to ChordFormula(listOf(0, 3, 7, 10, 14)),
    "m11" to ChordFormula(listOf(0, 3, 7, 10, 14, 17), listOf(17)),
    "m13" to ChordFormula(listOf(0, 3, 7, 10, 14, 17, 21), listOf(17, 21)),
    "mMaj7" to ChordFormula(listOf(0, 3, 7, 11)),
    "mMaj9" to ChordFormula(listOf(0, 3, 7, 11, 14)),
    "mMaj11" to ChordFormula(listOf(0, 3, 7, 11, 14, 17), listOf(17)),
    "mMaj13" to ChordFormula(listOf(0, 3, 7, 11, 14, 17, 21), listOf(17, 21)),
    "m7b5" to ChordFormula(listOf(0, 3, 6, 10)),

    "dim" to ChordFormula(listOf(0, 3, 6)),
    "dim7" to ChordFormula(listOf(0, 3, 6, 9)),
    "ø9" to ChordFormula(listOf(0, 3, 6, 10, 14)),
    "aug" to ChordFormula(listOf(0, 4, 8)),
    "9#5" to ChordFormula(listOf(0, 4, 8, 10, 14)),
    "sus2" to ChordFormula(listOf(0, 2, 7)),
    "sus4" to ChordFormula(listOf(0, 5, 7)),
    "7sus2" to ChordFormula(listOf(0, 2, 7, 10)),
    "7sus4" to ChordFormula(listOf(0, 5, 7, 10)),
    "maj7sus4" to ChordFormula(listOf(0, 5, 7, 11)),
    "9sus4" to ChordFormula(listOf(0, 5, 7, 10, 14)),
    "13sus4" to ChordFormula(listOf(0, 5, 7, 10, 14, 21), listOf(21)),
    "6sus4" to ChordFormula(listOf(0, 5, 7, 9)),
    "add9" to ChordFormula(listOf(0, 4, 7, 14)),
    "add11" to ChordFormula(listOf(0, 4, 7, 17)),
    "madd9" to ChordFormula(listOf(0, 3, 7, 14)),
    "madd11" to ChordFormula(listOf(0, 3, 7, 17)),
    "5" to ChordFormula(listOf(0, 7)),
)

val QUALITY_ALIASES: Map<String, String> = mapOf(
    "" to "maj",
    "maj" to "maj",
    "M" to "maj",
    "major" to "maj",
    "m" to "min",
    "min" to "min",
    "mi" to "min",
    "-" to "min",
    "7" to "7",
    "dom7" to "7",
    "maj7" to "maj7",
    "M7" to "maj7",
    "Δ7" to "maj7",
    "delta7" to "maj7",
    "m7" to "m7",
    "mMaj7" to "mMaj7",
    "m(maj7)" to "mMaj7",
    "dim" to "dim",
    "°" to "dim",
    "o" to "dim",
    "dim7" to "dim7",
    "°7" to "dim7",
    "o7" to "dim7",
    "m7b5" to "m7b5",
    "ø" to "m7b5",
    "ø7" to "m7b5",
    "halfdim" to "m7b5",
    "aug" to "aug",
    "+" to "aug",
    "sus2" to "sus2",
    "sus4" to "sus4",
    "sus" to "sus4",
    "6" to "6",
    "add6" to "6",
    "m6" to "m6",
    "9" to "9",
    "m9" to "m9",
    "add9" to "add9",
    "add2" to "add9",
    "5" to "5",
    "pow" to "5",

    "maj9" to "maj9",
    "M9" to "maj9",
    "Δ9" to "maj9",
    "delta9" to "maj9",
    "maj11" to "maj11",
    "M11" to "maj11",
    "Δ11" to "maj11",
    "delta11" to "maj11",
    "maj13" to "maj13",
    "M13" to "maj13",
    "Δ13" to "maj13",
    "delta13" to "maj13",
    "maj7#11" to "maj7#11",
    "M7#11" to "maj7#11",
    "Δ7#11" to "maj7#11",
    "maj13#11" to "maj13#11",
    "M13#11" to "maj13#11",
    "Δ13#11" to "maj13#11",
    "maj7#5" to "maj7#5",
    "M7#5" to "maj7#5",
    "Δ7#5" to "maj7#5",
    "maj7+5" to "maj7#5",
    "maj9#5" to "maj9#5",
    "M9#5" to "maj9#5",
    "Δ9#5" to "maj9#5",
    "9+5" to "maj9#5",
    "maj6" to "6",
    "6/9" to "6/9",
    "69" to "6/9",
    "6add9" to "6/9",
    "m6/9" to "m6/9",
    "m69" to "m6/9",
    "m6add9" to "m6/9",

    "11" to "11",
    "13" to "13",
    "7b5" to "7b5",
    "7#5" to "7#5",
    "+7" to "7#5",
    "aug7" to "7#5",
    "7b9" to "7b9",
    "7#9" to "7#9",
    "7#11" to "7#11",
    "7b13" to "7b13",
    "9#11" to "9#11",
    "13b9" to "13b9",
    "7#9b13" to "7#9b13",

    "m11" to "m11",
    "m13" to "m13",
    "mMaj9" to "mMaj9",
    "mM9" to "mMaj9",
    "m(maj9)" to "mMaj9",
    "mΔ9" to "mMaj9",
    "m(M9)" to "mMaj9",
    "mMaj11" to "mMaj11",
    "mM11" to "mMaj11",
    "m(maj11)" to "mMaj11",
    "mΔ11" to "mMaj11",
    "mMaj13" to "mMaj13",
    "mM13" to "mMaj13",
    "m(maj13)" to "mMaj13",
    "mΔ13" to "mMaj13",

    "ø9" to "ø9",
    "halfdim9" to "ø9",
    "7sus2" to "7sus2",
    "7sus" to "7sus4",
    "7sus4" to "7sus4",
    "maj7sus4" to "maj7sus4",
    "M7sus4" to "maj7sus4",
    "Δ7sus4" to "maj7sus4",
    "9sus4" to "9sus4",
    "9sus" to "9sus4",
    "13sus4" to "13sus4",
    "13sus" to "13sus4",
    "6sus4" to "6sus4",
    "add11" to "add11",
    "add4" to "add11",
    "madd9" to "madd9",
    "madd11" to "madd11",
    "madd4" to "madd11",
    "+maj7" to "maj7#5",
    "augmaj7" to "maj7#5",
    "Δ" to "maj7",
)

enum class ModeName(val steps: List<Int>) {
    Ionian(listOf(0, 2, 4, 5, 7, 9, 11)),
    Dorian(listOf(0, 2, 3, 5, 7, 9, 10)),
    Phrygian(listOf(0, 1, 3, 5, 7, 8, 10)),
    Lydian(listOf(0, 2, 4, 6, 7, 9, 11)),
    Mixolydian(listOf(0, 2, 4, 5, 7, 9, 10)),
    Aeolian(listOf(0, 2, 3, 5, 7, 8, 10)),
    Locrian(listOf(0, 1, 3, 5, 6, 8, 10)),
}

val MODE_NAMES: List<ModeName> = ModeName.entries.toList()

fun mod12(value: Int): Int = ((value % 12) + 12) % 12

fun flatsForPc(pc: Int): Boolean = mod12(pc) in setOf(1, 3, 6, 8, 10)

fun pcName(pc: Int, flats: Boolean): String =
    (if (flats) FLAT_PC_NAMES else SHARP_PC_NAMES)[mod12(pc)]

fun midiName(midi: Int, flats: Boolean): String =
    "${pcName(midi, flats)}${kotlin.math.floor(midi / 12.0).toInt() - 1}"

data class ParsedNote(
    val midi: Int,
    val pc: Int,
    val flats: Boolean,
)

private val NOTE_TOKEN_RE = Regex("^([A-Ga-g])\\s*([#b♯♭]?)([-+]?\\d+)$")

fun parseNoteToken(token: String): ParsedNote? {
    val match = NOTE_TOKEN_RE.matchEntire(token.trim()) ?: return null
    var pc = PC_LETTER[match.groupValues[1].uppercase()] ?: return null
    val accidental = match.groupValues[2]
    if (accidental == "#" || accidental == "♯") pc += 1
    if (accidental == "b" || accidental == "♭") pc -= 1
    val midi = (match.groupValues[3].toInt() + 1) * 12 + pc
    if (midi < 0 || midi > 127) return null
    return ParsedNote(midi, mod12(pc), accidental == "b" || accidental == "♭")
}

data class ParsedTuning(
    val midi: List<Int>,
    val labels: List<String>,
    val flats: Boolean,
)

sealed interface TuningParseResult {
    data class Ok(val tuning: ParsedTuning) : TuningParseResult
    data class Error(val error: String) : TuningParseResult
}

fun parseTuning(raw: String): TuningParseResult {
    val tokens = raw.split(Regex("[,\\s]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return TuningParseResult.Error("empty tuning")
    if (tokens.size > 12) return TuningParseResult.Error("max 12 strings")
    val midi = mutableListOf<Int>()
    val labels = mutableListOf<String>()
    val parsedNotes = mutableListOf<ParsedNote>()
    for (token in tokens) {
        val parsed = parseNoteToken(token) ?: return TuningParseResult.Error("bad note '$token'")
        midi.add(parsed.midi)
        labels.add(midiName(parsed.midi, parsed.flats))
        parsedNotes.add(parsed)
    }
    val flatCount = parsedNotes.count { it.flats }
    val flats = flatCount > parsedNotes.size / 2.0
    return TuningParseResult.Ok(ParsedTuning(midi, labels, flats))
}

data class ParsedChord(
    val symbol: String,
    val rootPc: Int,
    val quality: String,
    val intervals: List<Int>,
    val pcs: List<Int>,
    val optionalPcs: List<Int>,
    val flats: Boolean,
)

sealed interface ChordParseResult {
    data class Ok(val chord: ParsedChord) : ChordParseResult
    data class Error(val symbol: String, val error: String) : ChordParseResult
}

private fun normalizeQuality(raw: String): String =
    raw.replace("♯", "#").replace("♭", "b").lowercase()

private val ALTERATION_RE = Regex("(?:b|#)?(?:5|9|11|13)|add(?:9|11)")

private data class ComposedQuality(
    val key: String,
    val intervals: List<Int>,
    val optional: List<Int>,
)

private fun composeQuality(raw: String): ComposedQuality? {
    val normalized = normalizeQuality(raw)
    if (normalized.isEmpty()) return null

    var baseKey: String? = null
    var prefixLength = 0
    for ((key, _) in CHORD_FORMULAS) {
        val lowerKey = key.lowercase()
        if (normalized.startsWith(lowerKey) && (baseKey == null || key.length > baseKey.length)) {
            baseKey = key
            prefixLength = lowerKey.length
        }
    }
    if (baseKey == null && normalized.startsWith("m")) {
        baseKey = "min"
        prefixLength = 1
    }
    baseKey ?: return null

    val base = CHORD_FORMULAS.getValue(baseKey)
    val remainder = normalized.substring(prefixLength)
    val intervals = base.intervals.toMutableList()
    val optional = base.optional.toMutableList()
    val tokens = mutableListOf<String>()
    val matches = ALTERATION_RE.findAll(remainder).toList()
    var cursor = 0
    for (match in matches) {
        if (match.range.first != cursor) return null
        tokens.add(match.value)
        cursor = match.range.last + 1
    }
    if (cursor != remainder.length) return null

    fun drop(value: Int) {
        val i = intervals.indexOf(value)
        if (i >= 0) intervals.removeAt(i)
    }

    fun removeOptional(value: Int) {
        val i = optional.indexOf(value)
        if (i >= 0) optional.removeAt(i)
    }

    fun add(value: Int) {
        if (!intervals.contains(value)) intervals.add(value)
    }

    fun addOptional(value: Int) {
        add(value)
        if (!optional.contains(value)) optional.add(value)
    }

    for (token in tokens) {
        when (token) {
            "b5" -> {
                drop(7); add(6); removeOptional(6)
            }
            "#5" -> {
                drop(7); add(8); removeOptional(8)
            }
            "b9" -> {
                drop(14); add(13)
            }
            "#9" -> {
                drop(14); add(15)
            }
            "9" -> {
                drop(13); drop(15); add(14)
            }
            "11" -> addOptional(17)
            "#11" -> addOptional(18)
            "13" -> addOptional(21)
            "b13" -> addOptional(20)
            "add9" -> add(14)
            "add11" -> addOptional(17)
        }
    }

    return ComposedQuality(
        key = baseKey + tokens.joinToString(""),
        intervals = intervals.distinct().sorted(),
        optional = optional.distinct().sorted(),
    )
}

private val CHORD_SYMBOL_RE = Regex("^([A-Ga-g])\\s*([#b♯♭]?)\\s*(.*?)\\s*$")

fun parseChord(raw: String): ChordParseResult {
    val symbol = raw.trim()
    val match = CHORD_SYMBOL_RE.matchEntire(symbol)
        ?: return ChordParseResult.Error(symbol, "'$symbol' is not a chord symbol")
    var rootPc = PC_LETTER[match.groupValues[1].uppercase()] ?: return ChordParseResult.Error(
        symbol,
        "'$symbol' is not a chord symbol",
    )
    val accidental = match.groupValues[2]
    if (accidental == "#" || accidental == "♯") rootPc += 1
    if (accidental == "b" || accidental == "♭") rootPc -= 1
    rootPc = mod12(rootPc)
    val qualityRaw = match.groupValues[3]
    var quality: String? = QUALITY_ALIASES[qualityRaw] ?: QUALITY_ALIASES[qualityRaw.lowercase()]
    var formula: ChordFormula? = quality?.let { CHORD_FORMULAS[it] }
    if (formula == null) {
        val composed = composeQuality(qualityRaw)
        if (composed != null) {
            quality = composed.key
            formula = ChordFormula(composed.intervals, composed.optional)
        }
    }
    if (quality == null || formula == null) {
        return ChordParseResult.Error(
            symbol,
            "unknown chord quality '${qualityRaw.ifEmpty { "(none)" }}' in '$symbol'",
        )
    }
    val intervals = formula.intervals
    val optionalIntervals = formula.optional
    return ChordParseResult.Ok(
        ParsedChord(
            symbol = symbol,
            rootPc = rootPc,
            quality = quality,
            intervals = intervals,
            pcs = intervals.map { mod12(rootPc + it) },
            optionalPcs = optionalIntervals.map { mod12(rootPc + it) },
            flats = accidental == "b" || accidental == "♭",
        ),
    )
}

fun tokenizeProgression(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    return trimmed.split(Regex("(?:->|→|—|–|,|;|\\|)|/(?![0-9])"))
        .flatMap { it.split(Regex("\\s+")) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

data class DiatonicBadge(
    val kind: String,
    val text: String,
)

private val EXPECTED: Map<String, String> = mapOf(
    "3,6" to "dim",
    "3,7" to "min",
    "4,7" to "maj",
    "4,8" to "aug",
)

private val QUALITY_WORD: Map<String, String> = mapOf(
    "maj" to "major",
    "min" to "minor",
    "dim" to "diminished",
    "aug" to "augmented",
)

private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII")

private data class DegreeLookup(
    val degreeIndex: Int,
    val third: Int,
    val fifth: Int,
    val expectedQuality: String,
    val numeral: String,
)

private fun numeralFor(degreeIndex: Int, expectedQuality: String, steps: List<Int>): String {
    val base = NUMERALS[degreeIndex]
    val lower =
        if (expectedQuality == "min" || expectedQuality == "dim") base.lowercase() else base
    val suffix = when (expectedQuality) {
        "dim" -> "°"
        "aug" -> "+"
        else -> ""
    }
    val diff = mod12(steps[degreeIndex] - ModeName.Ionian.steps[degreeIndex])
    val prefix = when (diff) {
        11 -> "b"
        1 -> "#"
        else -> ""
    }
    return "$prefix$lower$suffix"
}

private fun accidentalPrefixFor(chord: ParsedChord, tonicPc: Int, steps: List<Int>): String {
    if (chord.rootPc == mod12(tonicPc + steps[0])) return ""
    var flats = 0
    var sharps = 0
    for (i in 0 until 7) {
        if (mod12(tonicPc + steps[i] - 1) == chord.rootPc) flats++
        if (mod12(tonicPc + steps[i] + 1) == chord.rootPc) sharps++
    }
    if (flats > 0 && sharps == 0) return "b"
    if (sharps > 0 && flats == 0) return "#"
    return ""
}

private fun lookupIn(chord: ParsedChord, tonicPc: Int, steps: List<Int>): DegreeLookup? {
    var degreeIndex = -1
    for (i in 0 until 7) {
        if (mod12(tonicPc + steps[i]) == chord.rootPc) {
            degreeIndex = i
            break
        }
    }
    if (degreeIndex < 0) return null
    val third = mod12(steps[(degreeIndex + 2) % 7] - steps[degreeIndex])
    val fifth = mod12(steps[(degreeIndex + 4) % 7] - steps[degreeIndex])
    val expectedQuality = EXPECTED["$third,$fifth"] ?: "maj"
    val prefix = accidentalPrefixFor(chord, tonicPc, steps)
    val numeral = prefix + numeralFor(degreeIndex, expectedQuality, steps)
    return DegreeLookup(degreeIndex, third, fifth, expectedQuality, numeral)
}

private fun qualityMatches(lookup: DegreeLookup, actualThird: Int?, actualFifth: Int?): Boolean =
    (actualThird == null || actualThird == lookup.third) &&
        (actualFifth == null || actualFifth == lookup.fifth)

fun computeBadgeForPc(
    chord: ParsedChord,
    tonicPc: Int,
    modeName: ModeName,
    tuningFlats: Boolean,
    tonicFlats: Boolean,
): DiatonicBadge? {
    val scaleRootName = pcName(tonicPc, tonicFlats || tuningFlats)
    val rootName = pcName(chord.rootPc, chord.flats || tuningFlats)

    val actualThird = when {
        chord.intervals.contains(4) -> 4
        chord.intervals.contains(3) -> 3
        else -> null
    }
    var actualFifth: Int? = null
    for (candidate in listOf(7, 6, 8)) {
        if (chord.intervals.contains(candidate)) {
            actualFifth = candidate
            break
        }
    }

    val primary = lookupIn(chord, tonicPc, modeName.steps)
    if (primary != null) {
        if (qualityMatches(primary, actualThird, actualFifth)) {
            return DiatonicBadge(
                kind = "good",
                text = "◈ ${primary.numeral} — diatonic to $scaleRootName $modeName",
            )
        }
        val qualityWord = QUALITY_WORD[primary.expectedQuality] ?: primary.expectedQuality
        return DiatonicBadge(
            kind = "warn",
            text = "◈ ${primary.numeral} — borrowed: $modeName expects ${primary.expectedQuality} ($qualityWord) here",
        )
    }

    for ((steps, label) in listOf(ModeName.Ionian.steps to "major", ModeName.Aeolian.steps to "minor")) {
        val lookup = lookupIn(chord, tonicPc, steps)
        if (lookup != null && qualityMatches(lookup, actualThird, actualFifth)) {
            return DiatonicBadge(
                kind = "warn",
                text = "◈ ${lookup.numeral} — borrowed from $scaleRootName $label",
            )
        }
    }

    return DiatonicBadge(
        kind = "bad",
        text = "◈ chromatic — $rootName isn't in $scaleRootName $modeName",
    )
}
