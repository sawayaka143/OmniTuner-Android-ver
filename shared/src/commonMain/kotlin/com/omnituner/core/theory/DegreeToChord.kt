package com.omnituner.core.theory

private val ROMAN_OFFSET: Map<String, Int> = mapOf(
    "I" to 0,
    "II" to 2,
    "III" to 4,
    "IV" to 5,
    "V" to 7,
    "VI" to 9,
    "VII" to 11,
)

private val DEGREE_RE = Regex("^(b|#)?(VII|VI|IV|V|III|II|I)(.*)$", RegexOption.IGNORE_CASE)

data class ParsedDegree(
    val accidental: Int,
    val romanUpper: String,
    val isMinorCore: Boolean,
    val suffix: String,
)

fun parseDegree(raw: String): ParsedDegree? {
    val m = DEGREE_RE.matchEntire(raw.trim()) ?: return null
    val rawAcc = m.groupValues[1]
    if (rawAcc == "B") return null
    val accidental = when {
        rawAcc.lowercase() == "b" -> -1
        rawAcc == "#" -> 1
        else -> 0
    }
    val core = m.groupValues[2]
    val suffixRaw = m.groupValues[3].trim()
    val romanUpper = core.uppercase()
    val isMinorCore = core != romanUpper
    return ParsedDegree(accidental, romanUpper, isMinorCore, suffixRaw)
}

fun degreeToChordSymbol(degree: String, tonicPc: Int, useFlats: Boolean): String? {
    val parsed = parseDegree(degree) ?: return null
    val offset = (ROMAN_OFFSET[parsed.romanUpper] ?: 0) + parsed.accidental
    val rootPc = mod12(tonicPc + offset)
    val rootName = pcName(rootPc, useFlats)
    val suffixLower = parsed.suffix.lowercase()
    val suffixAlreadyHasQuality =
        suffixLower.startsWith("maj") ||
            suffixLower == "m" ||
            suffixLower.startsWith("m ") ||
            suffixLower.startsWith("m(") ||
            suffixLower.startsWith("dim") ||
            suffixLower.startsWith("°") ||
            suffixLower.startsWith("ø") ||
            suffixLower == "+" ||
            suffixLower == "aug"
    val quality: String = if (parsed.suffix.isNotEmpty()) {
        if (parsed.isMinorCore && !suffixAlreadyHasQuality) "m${parsed.suffix}" else parsed.suffix
    } else {
        if (parsed.isMinorCore) "m" else ""
    }
    return "$rootName$quality"
}

fun degreesToProgression(degrees: List<String>, tonicPc: Int, useFlats: Boolean): List<String> =
    degrees.mapNotNull { degreeToChordSymbol(it, tonicPc, useFlats) }.take(6)

private val TONIC_RE = Regex("^([A-Ga-g])\\s*([#b♯♭]?)")

fun tonicPcOf(noteName: String): Int? {
    val m = TONIC_RE.find(noteName.trim()) ?: return null
    var pc = PC_LETTER[m.groupValues[1].uppercase()] ?: return null
    when (m.groupValues[2]) {
        "#", "♯" -> pc += 1
        "b", "♭" -> pc -= 1
    }
    return mod12(pc)
}
