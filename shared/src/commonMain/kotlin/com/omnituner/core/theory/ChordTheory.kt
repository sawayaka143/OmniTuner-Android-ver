package com.omnituner.core.theory

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
    "mmaj" to "mMaj7",
    "m(maj)" to "mMaj7",
    "mM7" to "mMaj7",
    "mΔ7" to "mMaj7",
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
    // Slash-chord bass ("C/G" -> 7); not part of pcs. Null for a plain chord.
    val bassPc: Int? = null,
)

sealed interface ChordParseResult {
    data class Ok(val chord: ParsedChord) : ChordParseResult
    data class Error(val symbol: String, val error: String) : ChordParseResult
}

// Drops the parentheses and any commas/spaces inside them: "7(b9, 13)" -> "7b913".
private fun stripParens(raw: String): String? {
    var depth = 0
    val out = StringBuilder()
    for (char in raw) {
        if (char == '(') {
            depth++
            continue
        }
        if (char == ')') {
            depth--
            if (depth < 0) return null
            continue
        }
        if (depth > 0 && (char == ',' || char.isWhitespace())) continue
        out.append(char)
    }
    return if (depth == 0) out.toString() else null
}

private fun normalizeQuality(raw: String): String? =
    stripParens(raw.replace("♯", "#").replace("♭", "b"))?.lowercase()

private val ALTERATION_RE = Regex("(?:b|#)?(?:5|9|11|13)|[b#]6|add(?:9|11)|sus(?:2|4)?|[+-]5")

private val CANONICAL_TOKENS: Map<String, String> = mapOf("+5" to "#5", "-5" to "b5", "sus" to "sus4")

private data class ComposedQuality(
    val key: String,
    val intervals: List<Int>,
    val optional: List<Int>,
)

private fun composeQuality(raw: String): ComposedQuality? {
    val normalized = normalizeQuality(raw)
    if (normalized.isNullOrEmpty()) return null

    var baseKey: String? = null
    var prefixLength = 0
    for (key in CHORD_FORMULAS.keys) {
        val lowerKey = key.lowercase()
        if (normalized.startsWith(lowerKey) && (baseKey == null || key.length > baseKey.length)) {
            baseKey = key
            prefixLength = lowerKey.length
        }
    }
    if (baseKey == null) {
        if (normalized.startsWith("m")) {
            baseKey = "min"
            prefixLength = 1
        } else if (normalized.startsWith("sus")) {
            baseKey = "sus4"
            prefixLength = 3
        }
    }
    baseKey ?: return null

    val base = CHORD_FORMULAS.getValue(baseKey)
    val remainder = normalized.substring(prefixLength)
    val intervals = base.intervals.toMutableList()
    val optional = base.optional.toMutableList()
    val tokens = mutableListOf<String>()
    var cursor = 0
    for (match in ALTERATION_RE.findAll(remainder)) {
        if (match.range.first != cursor) return null
        tokens.add(match.value)
        cursor = match.range.last + 1
    }
    if (cursor != remainder.length) return null

    fun drop(value: Int) {
        intervals.remove(value)
    }

    fun removeOptional(value: Int) {
        optional.remove(value)
    }

    fun add(value: Int) {
        if (value !in intervals) intervals.add(value)
    }

    fun addOptional(value: Int) {
        add(value)
        if (value !in optional) optional.add(value)
    }

    for (token in tokens) {
        when (token) {
            "b5", "-5" -> {
                drop(7); add(6); removeOptional(6)
            }
            "#5", "+5" -> {
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
            "b6" -> add(8)
            "#6" -> add(10)
            "sus2" -> {
                drop(3); drop(4); add(2)
            }
            "sus4", "sus" -> {
                drop(3); drop(4); add(5)
            }
            "add9" -> add(14)
            "add11" -> addOptional(17)
        }
    }

    return ComposedQuality(
        key = baseKey + tokens.joinToString("") { CANONICAL_TOKENS[it] ?: it },
        intervals = intervals.distinct().sorted(),
        optional = optional.distinct().sorted(),
    )
}

private val ROOT_RE = Regex("^([A-Ga-g])\\s*([#b♯♭]?)\\s*(.*?)\\s*$")
private val BASS_NOTE_RE = Regex("^\\s*([A-Ga-g])\\s*([#b♯♭]?)\\s*$")

private fun pcOf(letter: String, accidental: String): Int {
    var pc = PC_LETTER.getValue(letter.uppercase())
    if (accidental == "#" || accidental == "♯") pc += 1
    if (accidental == "b" || accidental == "♭") pc -= 1
    return mod12(pc)
}

private fun isFlat(accidental: String): Boolean = accidental == "b" || accidental == "♭"

fun parseChord(raw: String): ChordParseResult {
    val symbol = raw.trim()
    val slash = symbol.lastIndexOf('/')
    val bassMatch = if (slash >= 0) BASS_NOTE_RE.matchEntire(symbol.substring(slash + 1)) else null
    val chordPart = if (bassMatch != null) symbol.substring(0, slash) else symbol
    val bassPc = bassMatch?.let { pcOf(it.groupValues[1], it.groupValues[2]) }

    val match = ROOT_RE.matchEntire(chordPart)
        ?: return ChordParseResult.Error(symbol, "'$symbol' is not a chord symbol")
    val rootPc = pcOf(match.groupValues[1], match.groupValues[2])
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
    return ChordParseResult.Ok(
        ParsedChord(
            symbol = symbol,
            rootPc = rootPc,
            quality = quality,
            intervals = intervals,
            pcs = intervals.map { mod12(rootPc + it) },
            optionalPcs = formula.optional.map { mod12(rootPc + it) },
            flats = isFlat(match.groupValues[2]) || (bassMatch != null && isFlat(bassMatch.groupValues[2])),
            bassPc = bassPc,
        ),
    )
}

private val STRONG_SEPARATORS = setOf(',', ';', '|', '→', '—', '–')
private const val NOTE_OR_END = "(?=$|[\\s,;|→—–]|->)"
private val BASS_SLASH_RE = Regex("^\\s*[A-Ga-g][#b♯♭]?$NOTE_OR_END")
private val BASS_SLASH_SPACES_RE = Regex("\\s*/\\s*(?=[A-Ga-g][#b♯♭]?$NOTE_OR_END)")
private val DIGIT_RE = Regex("^\\s*\\d")

/**
 * Splits a progression into chord tokens. A slash followed by a lone note ("C/E", "Cmaj9 / G")
 * or a digit ("6/9") stays inside the token; any other slash separates chords. Commas and
 * spaces inside balanced parentheses ("C7(b9, 13)") stay inside the token too.
 */
fun tokenizeProgression(raw: String): List<String> {
    val text = BASS_SLASH_SPACES_RE.replace(raw, "/")
    val tokens = mutableListOf<String>()
    val balanced = text.count { it == '(' } == text.count { it == ')' }
    val current = StringBuilder()
    var depth = 0

    fun flush() {
        val token = current.toString().trim()
        if (token.isNotEmpty() && token != "/") tokens.add(token)
        current.clear()
    }

    var i = 0
    while (i < text.length) {
        val char = text[i]
        when {
            balanced && char == '(' -> {
                depth++
                current.append(char)
            }
            balanced && char == ')' -> {
                depth = maxOf(0, depth - 1)
                current.append(char)
            }
            depth > 0 -> current.append(char)
            char in STRONG_SEPARATORS -> flush()
            char == '-' && text.getOrNull(i + 1) == '>' -> {
                flush()
                i++
            }
            char == '/' -> {
                val rest = text.substring(i + 1)
                if (BASS_SLASH_RE.containsMatchIn(rest) || DIGIT_RE.containsMatchIn(rest)) {
                    current.append('/')
                } else {
                    flush()
                }
            }
            char.isWhitespace() -> flush()
            else -> current.append(char)
        }
        i++
    }
    flush()
    return tokens
}

enum class BadgeKind { Good, Warn, Borrowed, Bad }

data class DiatonicBadge(
    val kind: BadgeKind,
    val numeral: String,
    val detail: String,
)

fun keyName(tonicName: String, mode: ModeName): String = when (mode) {
    ModeName.Ionian -> "$tonicName major"
    ModeName.Aeolian -> "$tonicName minor"
    else -> "$tonicName $mode"
}

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
    val prefix = when (mod12(steps[degreeIndex] - ModeName.Ionian.steps[degreeIndex])) {
        11 -> "b"
        1 -> "#"
        else -> ""
    }
    return "$prefix$lower$suffix"
}

private fun lookupIn(rootPc: Int, tonicPc: Int, steps: List<Int>): DegreeLookup? {
    val degreeIndex = (0 until 7).firstOrNull { mod12(tonicPc + steps[it]) == rootPc } ?: return null
    val third = mod12(steps[(degreeIndex + 2) % 7] - steps[degreeIndex])
    val fifth = mod12(steps[(degreeIndex + 4) % 7] - steps[degreeIndex])
    val expectedQuality = EXPECTED["$third,$fifth"] ?: "maj"
    return DegreeLookup(degreeIndex, third, fifth, expectedQuality, numeralFor(degreeIndex, expectedQuality, steps))
}

/**
 * Where [chord] sits in the key of [tonicPc] [modeName]: Good when its root and quality are
 * diatonic, Warn when the root is diatonic but the quality isn't, Borrowed when it is diatonic
 * to the parallel major or minor instead, Bad otherwise. Only the third and fifth are compared.
 */
fun computeBadgeForPc(
    chord: ParsedChord,
    tonicPc: Int,
    modeName: ModeName,
    tuningFlats: Boolean,
    tonicFlats: Boolean,
): DiatonicBadge {
    val scaleRootName = pcName(tonicPc, tonicFlats || tuningFlats)
    val key = keyName(scaleRootName, modeName)
    val rootName = pcName(chord.rootPc, chord.flats || tuningFlats)

    val actualThird = when {
        4 in chord.intervals -> 4
        3 in chord.intervals -> 3
        else -> null
    }
    val actualFifth = listOf(7, 6, 8).firstOrNull { it in chord.intervals }

    fun qualityMatches(lookup: DegreeLookup): Boolean =
        (actualThird == null || actualThird == lookup.third) &&
            (actualFifth == null || actualFifth == lookup.fifth)

    val primary = lookupIn(chord.rootPc, tonicPc, modeName.steps)
    if (primary != null) {
        if (qualityMatches(primary)) {
            return DiatonicBadge(BadgeKind.Good, primary.numeral, "diatonic to $key")
        }
        return DiatonicBadge(
            BadgeKind.Warn,
            primary.numeral,
            "altered: $key expects ${QUALITY_WORD[primary.expectedQuality]} here",
        )
    }

    for ((steps, label) in listOf(ModeName.Ionian.steps to "major", ModeName.Aeolian.steps to "minor")) {
        val lookup = lookupIn(chord.rootPc, tonicPc, steps)
        if (lookup != null && qualityMatches(lookup)) {
            return DiatonicBadge(BadgeKind.Borrowed, lookup.numeral, "borrowed from $scaleRootName $label")
        }
    }

    return DiatonicBadge(BadgeKind.Bad, "—", "chromatic: $rootName isn't in $key")
}
