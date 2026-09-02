package com.omnituner.core.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordTheoryTest {

    private fun chord(symbol: String): ParsedChord {
        val result = parseChord(symbol)
        assertTrue(result is ChordParseResult.Ok, "parse failed: $symbol")
        return (result as ChordParseResult.Ok).chord
    }

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
        val result = parseTuning("E2 A2 D3 G3 B3 E4")
        assertTrue(result is TuningParseResult.Ok)
        val tuning = (result as TuningParseResult.Ok).tuning
        assertEquals(listOf(40, 45, 50, 55, 59, 64), tuning.midi)
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), tuning.labels)
        assertFalse(tuning.flats)
    }

    @Test
    fun parseTuningInheritsFlats() {
        val result = parseTuning("Eb2 Ab2")
        assertTrue(result is TuningParseResult.Ok)
        val tuning = (result as TuningParseResult.Ok).tuning
        assertTrue(tuning.flats)
        assertEquals("Eb2", tuning.labels[0])
    }

    @Test
    fun parseTuningReportsErrors() {
        assertEquals(TuningParseResult.Error("empty tuning"), parseTuning("  "))
        assertEquals(
            TuningParseResult.Error("max 12 strings"),
            parseTuning(List(13) { "E2" }.joinToString(" ")),
        )
        assertEquals(TuningParseResult.Error("bad note 'X2'"), parseTuning("E2 X2"))
    }

    @Test
    fun parseChordPlainMajor() {
        val c = chord("C")
        assertEquals(0, c.rootPc)
        assertEquals("maj", c.quality)
        assertEquals(listOf(0, 4, 7), c.pcs)
    }

    @Test
    fun parseChordFlatRootAndSeventh() {
        val bb7 = chord("Bb7")
        assertEquals(10, bb7.rootPc)
        assertTrue(bb7.flats)
        assertEquals(listOf(10, 2, 5, 8), bb7.pcs)
    }

    @Test
    fun parseChordResolvesAliases() {
        assertEquals("min", chord("Cm").quality)
        assertEquals("m7b5", chord("Bø").quality)
        assertEquals("maj7", chord("CΔ7").quality)
    }

    @Test
    fun parseChordExtendedFamilies() {
        val maj9 = chord("Cmaj9")
        assertEquals("maj9", maj9.quality)
        assertEquals(listOf(0, 4, 7, 11, 2), maj9.pcs)
        assertEquals(emptyList(), maj9.optionalPcs)

        val thirteen = chord("C13")
        assertEquals("13", thirteen.quality)
        assertEquals(listOf(0, 4, 7, 10, 2, 5, 9), thirteen.pcs)
        assertEquals(listOf(5, 9), thirteen.optionalPcs)

        val b13 = chord("C7b13")
        assertEquals("7b13", b13.quality)
        assertEquals(listOf(8), b13.optionalPcs)

        assertEquals("m6/9", chord("Cm6/9").quality)
        assertEquals(listOf(0, 3, 7, 9, 2), chord("Cm6/9").pcs)

        assertEquals("mMaj9", chord("Cm(maj9)").quality)
        assertEquals("ø9", chord("Cø9").quality)

        val sus13 = chord("C13sus4")
        assertEquals("13sus4", sus13.quality)
        assertEquals(listOf(9), sus13.optionalPcs)

        assertEquals("add11", chord("Cadd11").quality)
        assertEquals("madd9", chord("Cmadd9").quality)
    }

    @Test
    fun parseChordNormalizesUnicodeAccidentals() {
        val sharp11 = chord("Cmaj7♯11")
        assertEquals("maj7#11", sharp11.quality)
        assertEquals(listOf(6), sharp11.optionalPcs)

        assertEquals("7b9", chord("C7♭9").quality)
    }

    @Test
    fun parseChordComposesAlterations() {
        val b5b9 = chord("C7b5b9")
        assertEquals(listOf(0, 4, 6, 10, 13), b5b9.intervals)

        val sharp = chord("C7#9b13")
        assertEquals(listOf(0, 4, 7, 10, 15, 20), sharp.intervals)
        assertEquals(listOf(8), sharp.optionalPcs)

        val all = chord("C7b5#9b13#11")
        assertEquals(listOf(0, 4, 6, 10, 15, 18, 20), all.intervals)
        assertEquals(listOf(6, 8), all.optionalPcs)

        val theoretical = chord("Cm♭9")
        assertEquals(listOf(0, 3, 7, 13), theoretical.intervals)
    }

    @Test
    fun parseChordRejectsUnknownQualities() {
        assertTrue(parseChord("Cfoo") is ChordParseResult.Error)
        assertTrue(parseChord("123") is ChordParseResult.Error)
        assertTrue(parseChord("C7x9") is ChordParseResult.Error)
    }

    @Test
    fun tokenizeProgressionSplitsOnSeparators() {
        assertEquals(
            listOf("Cm", "Gmaj", "Bb7", "Fm"),
            tokenizeProgression("Cm, Gmaj | Bb7 / Fm"),
        )
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
    fun noteNamesSpellSharpsAndFlats() {
        assertEquals("C#", pcName(1, false))
        assertEquals("Db", pcName(1, true))
        assertEquals("C4", midiName(60, false))
        assertEquals("Bb3", midiName(58, true))
    }

    @Test
    fun flatsForPcMatchesCircleOfFifthsSide() {
        assertTrue(flatsForPc(1))
        assertTrue(flatsForPc(10))
        assertFalse(flatsForPc(0))
        assertFalse(flatsForPc(4))
    }

    @Test
    fun computeBadgeForPcDiatonicWarnAndBad() {
        val c = chord("C")
        val g = chord("G")
        val badge = computeBadgeForPc(g, 0, ModeName.Ionian, false, false)
        assertTrue(badge != null && badge.kind == "good")
        assertTrue(badge.text.contains("V"))

        val fsm = chord("F#m")
        val bad = computeBadgeForPc(fsm, 0, ModeName.Ionian, false, false)
        assertTrue(bad != null && bad.kind == "bad")

        val em = chord("Em")
        val good = computeBadgeForPc(em, 0, ModeName.Ionian, false, false)
        assertTrue(good != null && good.kind == "good")
        assertTrue(good.text.contains("iii"))

        val e = chord("E")
        val warn = computeBadgeForPc(e, 0, ModeName.Ionian, false, false)
        assertTrue(warn != null && warn.kind == "warn")
        assertTrue(warn.text.contains("borrowed"))
    }
}
