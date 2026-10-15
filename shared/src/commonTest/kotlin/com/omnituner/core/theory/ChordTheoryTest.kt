package com.omnituner.core.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordTheoryTest {

    private fun chord(symbol: String): ParsedChord = when (val r = parseChord(symbol)) {
        is ChordParseResult.Ok -> r.chord
        is ChordParseResult.Error -> error("parse failed: $symbol: ${r.error}")
    }

    private fun fails(symbol: String) = assertTrue(parseChord(symbol) is ChordParseResult.Error, symbol)

    @Test
    fun parseNoteTokenParsesAccidentals() {
        assertEquals(ParsedNote(40, 4, false), parseNoteToken("E2"))
        assertEquals(ParsedNote(42, 6, false), parseNoteToken("F#2"))
        assertEquals(ParsedNote(58, 10, true), parseNoteToken("Bb3"))
        assertEquals(ParsedNote(49, 1, true), parseNoteToken("D♭3"))
    }

    @Test
    fun parseNoteTokenRejectsInvalidTokens() {
        assertNull(parseNoteToken("H2"))
        assertNull(parseNoteToken("E"))
        assertNull(parseNoteToken(""))
    }

    @Test
    fun parseTuningParsesStandardGuitar() {
        val tuning = (parseTuning("E2 A2 D3 G3 B3 E4") as TuningParseResult.Ok).tuning
        assertEquals(listOf(40, 45, 50, 55, 59, 64), tuning.midi)
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), tuning.labels)
        assertFalse(tuning.flats)
    }

    @Test
    fun parseTuningInheritsFlats() {
        val tuning = (parseTuning("Eb2 Ab2") as TuningParseResult.Ok).tuning
        assertTrue(tuning.flats)
        assertEquals("Eb2", tuning.labels[0])
    }

    @Test
    fun parseTuningReportsErrors() {
        assertEquals(TuningParseResult.Error("empty tuning"), parseTuning("  "))
        assertEquals(TuningParseResult.Error("max 12 strings"), parseTuning(List(13) { "E2" }.joinToString(" ")))
        assertEquals(TuningParseResult.Error("bad note 'X2'"), parseTuning("E2 X2"))
    }

    @Test
    fun parsesPlainMajor() {
        val c = chord("C")
        assertEquals(0, c.rootPc)
        assertEquals("maj", c.quality)
        assertEquals(listOf(0, 4, 7), c.pcs)
    }

    @Test
    fun parsesFlatRootAndSeventh() {
        val bb7 = chord("Bb7")
        assertEquals(10, bb7.rootPc)
        assertTrue(bb7.flats)
        assertEquals(listOf(10, 2, 5, 8), bb7.pcs)
    }

    @Test
    fun resolvesAliases() {
        assertEquals("min", chord("Cm").quality)
        assertEquals("m7b5", chord("Bø").quality)
        assertEquals("maj7", chord("CΔ7").quality)
    }

    @Test
    fun parsesExtendedFamilies() {
        val maj9 = chord("Cmaj9")
        assertEquals("maj9", maj9.quality)
        assertEquals(listOf(0, 4, 7, 11, 2), maj9.pcs)
        assertEquals(emptyList(), maj9.optionalPcs)

        val thirteen = chord("C13")
        assertEquals("13", thirteen.quality)
        // No natural 11 in a dominant 13th: it clashes with the third.
        assertEquals(listOf(0, 4, 7, 10, 2, 9), thirteen.pcs)
        assertEquals(listOf(9), thirteen.optionalPcs)

        val b13 = chord("C7b13")
        assertEquals("7b13", b13.quality)
        assertEquals(listOf(8), b13.optionalPcs)

        val m69 = chord("Cm6/9")
        assertEquals("m6/9", m69.quality)
        assertEquals(listOf(0, 3, 7, 9, 2), m69.pcs)

        assertEquals("mMaj9", chord("Cm(maj9)").quality)
        assertEquals("ø9", chord("Cø9").quality)

        val sus13 = chord("C13sus4")
        assertEquals("13sus4", sus13.quality)
        assertEquals(listOf(9), sus13.optionalPcs)

        assertEquals("add11", chord("Cadd11").quality)
        assertEquals("madd9", chord("Cmadd9").quality)
    }

    @Test
    fun normalizesUnicodeAccidentals() {
        val sharp11 = chord("Cmaj7♯11")
        assertEquals("maj7#11", sharp11.quality)
        assertEquals(listOf(6), sharp11.optionalPcs)
        assertEquals("7b9", chord("C7♭9").quality)
    }

    @Test
    fun composesAlterations() {
        assertEquals(listOf(0, 4, 6, 10, 13), chord("C7b5b9").intervals)

        val sharp = chord("C7#9b13")
        assertEquals(listOf(0, 4, 7, 10, 15, 20), sharp.intervals)
        assertEquals(listOf(8), sharp.optionalPcs)

        val all = chord("C7b5#9b13#11")
        assertEquals(listOf(0, 4, 6, 10, 15, 18, 20), all.intervals)
        assertEquals(listOf(6, 8), all.optionalPcs)

        assertEquals(listOf(0, 3, 7, 13), chord("Cm♭9").intervals)

        val plus = chord("A7+5")
        assertEquals("7#5", plus.quality)
        assertEquals(listOf(0, 4, 8, 10), plus.intervals)

        assertEquals(listOf(0, 4, 6, 10), chord("C7-5").intervals)
    }

    @Test
    fun parsesParenthesizedAlterationsAndModifiers() {
        val sharp5 = chord("A7(#5)")
        assertEquals("7#5", sharp5.quality)
        assertEquals(listOf(0, 4, 8, 10), sharp5.intervals)

        val sus2 = chord("Fmaj7sus2(#11)")
        assertEquals(listOf(0, 2, 7, 11, 18), sus2.intervals)
        assertEquals(listOf(11), sus2.optionalPcs)

        val list = chord("C7(b9, 13)")
        assertEquals(listOf(0, 4, 7, 10, 13, 21), list.intervals)
        assertEquals(listOf(9), list.optionalPcs)

        val sus4 = chord("Cm7sus4")
        assertEquals("m7sus4", sus4.quality)
        assertEquals(listOf(0, 5, 7, 10), sus4.intervals)

        assertEquals(listOf(0, 5, 7, 14), chord("Csus9").intervals)
    }

    @Test
    fun acceptsSixthAlterationsInBothSpellings() {
        val bare = chord("Emb6")
        assertEquals("minb6", bare.quality)
        assertEquals(listOf(0, 3, 7, 8), bare.intervals)
        assertEquals(listOf(0, 3, 7, 8), chord("Em(b6)").intervals)
        assertEquals(listOf(0, 4, 7, 10, 14), chord("Cadd9#6").intervals)
    }

    @Test
    fun acceptsEveryMinorMajorSpelling() {
        for (symbol in listOf("Fmmaj7", "Fm(maj7)", "Fm(maj)", "FmM7", "FmΔ7")) {
            val c = chord(symbol)
            assertEquals("mMaj7", c.quality, symbol)
            assertEquals(listOf(0, 3, 7, 11), c.intervals, symbol)
        }
    }

    @Test
    fun parsesSlashChordsWithBassOutsideTheChordTones() {
        val overE = chord("C/E")
        assertEquals(0, overE.rootPc)
        assertEquals("maj", overE.quality)
        assertEquals(listOf(0, 4, 7), overE.pcs)
        assertEquals(4, overE.bassPc)

        val altered = chord("A7(#5)/C#")
        assertEquals(9, altered.rootPc)
        assertEquals(1, altered.bassPc)
        assertEquals(listOf(0, 4, 8, 10), altered.intervals)

        val sixthNine = chord("C6/9/G")
        assertEquals("6/9", sixthNine.quality)
        assertEquals(7, sixthNine.bassPc)
        assertEquals(listOf(0, 4, 7, 9, 14), sixthNine.intervals)

        val flatBass = chord("C/Bb")
        assertEquals(10, flatBass.bassPc)
        assertTrue(flatBass.flats)

        assertNull(chord("C").bassPc)
    }

    @Test
    fun capitalMMeansMajorAndLowercaseMMeansMinor() {
        assertEquals(listOf(0, 4, 7, 9), chord("CM6").intervals)
        assertEquals(listOf(0, 4, 6, 11), chord("CM7b5").intervals)
        assertEquals(listOf(0, 4, 7, 11, 14, 18), chord("CM9#11").intervals)
        assertEquals("maj7", chord("CM7").quality)
        assertEquals(listOf(0, 3, 7, 9), chord("Cm6").intervals)
        assertEquals("m7b5", chord("Cm7b5").quality)
        assertEquals("mMaj7", chord("CmM7").quality)
    }

    @Test
    fun spelledOutAndEuropeanQualities() {
        assertEquals("m7", chord("Cmin7").quality)
        assertEquals("m7b5", chord("Cmin7b5").quality)
        assertEquals("m7", chord("Cmi7").quality)
        assertEquals("m7", chord("C-7").quality)
        assertEquals("min", chord("C-").quality)
        assertEquals("maj7", chord("C7M").quality)
        assertEquals("mMaj7", chord("Cm7M").quality)
        assertEquals("maj7", chord("CMaj7").quality)
    }

    @Test
    fun alteredTensionsReplaceTheNaturalOnes() {
        val c13s11 = chord("C13#11")
        assertEquals(listOf(0, 4, 7, 10, 14, 18, 21), c13s11.intervals)
        assertFalse(17 in c13s11.intervals)
        assertEquals(listOf(0, 4, 7, 11, 14, 21), chord("Cmaj13").intervals)
        assertEquals(listOf(0, 4, 7, 10, 14, 20), chord("C9b13").intervals)
    }

    @Test
    fun augmentedNinthCarriesTheSeventh() {
        for (symbol in listOf("Caug9", "C+9", "C9+5", "C9#5", "Caug(9)")) {
            assertEquals(listOf(0, 4, 8, 10, 14), chord(symbol).intervals, symbol)
        }
        assertEquals(listOf(0, 4, 8, 14), chord("Caug(add9)").intervals)
        assertEquals(listOf(0, 4, 8, 10, 14, 21), chord("Caug13").intervals)
    }

    @Test
    fun parsesAltAndAddedTones() {
        assertEquals(listOf(0, 4, 10, 15, 20), chord("C7alt").intervals)
        assertEquals("7alt", chord("Calt").quality)
        assertEquals(listOf(0, 4, 7, 21), chord("Cadd13").intervals)
        assertEquals(listOf(0, 4, 7, 18), chord("Cadd#11").intervals)
        assertEquals(listOf(0, 3, 7, 14), chord("Cmadd2").intervals)
        assertEquals("madd9", chord("Cmadd2").quality)
        assertEquals(listOf(0, 4, 7, 14), chord("Cadd2").intervals)
        assertEquals(listOf(0, 4, 7, 13), chord("C(b9)").intervals)
    }

    @Test
    fun rejectsUnknownQualitiesAndNonSymbols() {
        for (symbol in listOf("Cfoo", "123", "C7x9", "C/E/G", "C7(#5", "C/H")) fails(symbol)
    }

    @Test
    fun tokenizeSplitsOnSeparators() {
        assertEquals(listOf("Cm", "Gmaj", "Bb7", "Fm"), tokenizeProgression("Cm, Gmaj | Bb7 / Fm"))
        assertEquals(listOf("Cm", "Gmaj", "Bb7"), tokenizeProgression("Cm Gmaj Bb7"))
        assertEquals(listOf("C6/9", "Fmaj7"), tokenizeProgression("C6/9, Fmaj7"))
        assertEquals(listOf("Cm6/9"), tokenizeProgression("Cm6/9"))
        assertEquals(listOf("Am", "Dm", "G"), tokenizeProgression("Am -> Dm -> G"))
        assertEquals(listOf("C", "G", "Am"), tokenizeProgression("C → G → Am"))
        assertEquals(listOf("C", "G", "Am"), tokenizeProgression("C — G – Am"))
        assertEquals(listOf("C-", "G"), tokenizeProgression("C- G"))
        assertEquals(listOf("C-", "F"), tokenizeProgression("C- -> F"))
    }

    @Test
    fun tokenizeKeepsSlashChordsGlued() {
        assertEquals(listOf("C/E", "Am"), tokenizeProgression("C/E, Am"))
        assertEquals(listOf("A7(#5)/C#", "Dm9"), tokenizeProgression("A7(#5) / C# – Dm9"))
        assertEquals(listOf("Cmaj9/G"), tokenizeProgression("Cmaj9 / G"))
        assertEquals(listOf("C6/9/G"), tokenizeProgression("C6/9/G"))
        assertEquals(listOf("Cm", "Gmaj"), tokenizeProgression("Cm/Gmaj"))
        assertEquals(listOf("C", "Fm7"), tokenizeProgression("C/Fm7"))
    }

    @Test
    fun tokenizeHandlesParentheses() {
        assertEquals(listOf("C7(b9, 13)", "Gm7"), tokenizeProgression("C7(b9, 13), Gm7"))
        assertEquals(listOf("C7(#9", "Dm7"), tokenizeProgression("C7(#9, Dm7"))
    }

    @Test
    fun tokenizesARealWorldJazzProgression() {
        val tokens = tokenizeProgression("Fmaj7 – Fm(maj7) – C/E – A7(#5)/C# – Dm9 – Fmaj7sus2(#11) – Cmaj9")
        assertEquals(
            listOf("Fmaj7", "Fm(maj7)", "C/E", "A7(#5)/C#", "Dm9", "Fmaj7sus2(#11)", "Cmaj9"),
            tokens,
        )
        for (token in tokens) assertTrue(parseChord(token) is ChordParseResult.Ok, token)
    }

    @Test
    fun noteNamesSpellSharpsAndFlats() {
        assertEquals("C#", pcName(1, false))
        assertEquals("Db", pcName(1, true))
        assertEquals("C4", midiName(60, false))
        assertEquals("Bb3", midiName(58, true))
    }

    @Test
    fun keyNameUsesMajorAndMinor() {
        assertEquals("C major", keyName("C", ModeName.Ionian))
        assertEquals("A minor", keyName("A", ModeName.Aeolian))
        assertEquals("D Dorian", keyName("D", ModeName.Dorian))
    }

    private fun badge(symbol: String, tonicPc: Int, mode: ModeName): DiatonicBadge =
        computeBadgeForPc(chord(symbol), tonicPc, mode, tuningFlats = false, tonicFlats = false)

    @Test
    fun badgeKinds() {
        assertEquals(BadgeKind.Good, badge("Fmaj7", 0, ModeName.Ionian).kind)
        assertEquals(BadgeKind.Good, badge("C", 0, ModeName.Ionian).kind)

        val fMinorMajor = badge("Fm(maj7)", 0, ModeName.Ionian)
        assertEquals(BadgeKind.Warn, fMinorMajor.kind)
        assertEquals("IV", fMinorMajor.numeral)
        assertEquals("altered: C major expects major here", fMinorMajor.detail)

        val leadingTone = badge("C#dim", 2, ModeName.Aeolian)
        assertEquals(BadgeKind.Borrowed, leadingTone.kind)
        assertEquals("borrowed from D major", leadingTone.detail)

        val chromatic = badge("C#dim", 0, ModeName.Ionian)
        assertEquals(BadgeKind.Bad, chromatic.kind)
        assertTrue("chromatic" in chromatic.detail)
    }

    @Test
    fun badgeNumerals() {
        assertEquals("iii", badge("Em", 0, ModeName.Ionian).numeral)
        assertEquals("IV", badge("Fmaj7", 0, ModeName.Ionian).numeral)
        assertEquals("bII", badge("Db", 0, ModeName.Phrygian).numeral)
        assertEquals("vii°", badge("C#dim", 2, ModeName.Aeolian).numeral)
    }
}
