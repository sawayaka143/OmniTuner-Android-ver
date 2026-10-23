package com.omnituner.core.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyDetectorTest {

    private fun chord(symbol: String): ParsedChord = when (val r = parseChord(symbol)) {
        is ChordParseResult.Ok -> r.chord
        is ChordParseResult.Error -> error("parse failed: $symbol: ${r.error}")
    }

    private fun chords(vararg symbols: String): List<ParsedChord> = symbols.map { chord(it) }

    private fun detect(vararg symbols: String): DetectedKey = assertNotNull(detectKey(chords(*symbols)))

    private fun DetectedKey.name() = "$tonicName $mode"

    @Test
    fun detectsCIonianForCFG() {
        val dk = detect("C", "F", "G")
        assertEquals("C Ionian", dk.name())
        assertEquals("strong", dk.confidence)
    }

    @Test
    fun resolvesAmDmEToAAeolianThroughTheHarmonicMinorDominant() {
        val dk = detect("Am", "Dm", "E")
        assertEquals("A Aeolian", dk.name())
        assertEquals("strong", dk.confidence)
    }

    @Test
    fun detectsGSharpMinorOverItsRelativeMajor() {
        val dk = detect("Emaj7", "C#m7", "D#7", "G#m")
        assertEquals("G# Aeolian", dk.name())
        assertEquals("strong", dk.confidence)
    }

    @Test
    fun countsTheLeadingToneDiminishedInMinor() {
        assertEquals("A Aeolian", detect("Am", "G#dim", "Am").name())
    }

    @Test
    fun aMajorDominantDoesNotPullMajorKeysToMinor() {
        assertEquals("C Ionian", detect("C", "F", "G7", "C").name())
    }

    @Test
    fun detectsCIonianForTwoFiveOne() {
        assertEquals("C Ionian", detect("Dm7", "G7", "Cmaj7").name())
    }

    @Test
    fun spellsFlatsForBbEbF() {
        assertEquals("Bb Ionian", detect("Bb", "Eb", "F").name())
    }

    @Test
    fun handlesASingleChord() {
        assertEquals("C", detect("C").tonicName)
    }

    @Test
    fun chromaticProgressionIsWeakOrModerate() {
        assertTrue(detect("Cmaj7", "Bbmaj7", "Abmaj7").confidence in setOf("weak", "moderate"))
    }

    @Test
    fun returnsNullForEmpty() {
        assertNull(detectKey(emptyList()))
    }

    @Test
    fun detectsCIonianForPopProgression() {
        assertEquals("C Ionian", detect("C", "G", "Am", "F").name())
    }

    @Test
    fun rankKeysIsSorted() {
        val ranked = rankKeys(chords("C", "F", "G"))
        assertTrue(ranked[0].score >= ranked[1].score)
    }

    @Test
    fun strongKeyHasLowerScoringAlternatives() {
        val dk = detect("C", "F", "G", "Am")
        assertEquals("C Ionian", dk.name())
        assertEquals("strong", dk.confidence)
        assertTrue(dk.alternatives.isNotEmpty())
        for (alt in dk.alternatives) assertTrue(alt.score <= dk.score)
    }

    @Test
    fun respectsFlatSpellingForFlatChords() {
        assertTrue("b" in detect("Bb", "Eb").tonicName)
    }

    @Test
    fun detectsCIonianForMixtureProgression() {
        val dk = detect("Fmaj7", "Fm(maj7)", "C/E", "C#dim/E")
        assertEquals("weak", dk.confidence)
        assertEquals(
            // F Ionian drops out of the runners-up: its own tonic also sounds as Fm.
            listOf("C Ionian", "D Aeolian", "F Aeolian"),
            listOf(dk.name()) + dk.alternatives.map { it.name() },
        )
    }

    @Test
    fun detectsCIonianForExtendedProgressionWithSlashChords() {
        assertEquals(
            "C Ionian",
            detect("Fmaj7", "Fm(maj7)", "C/E", "A7(#5)/C#", "Dm9", "Fmaj7sus2(#11)", "Cmaj9").name(),
        )
    }

    @Test
    fun doesNotCreditABorrowWhenTheTonicChordNeverSounds() {
        // C#dim is the leading-tone chord of D, but Dm never appears, so the borrow is
        // chromaticism rather than evidence for D Aeolian.
        val ranked = rankKeys(chords("Fmaj7", "Fm(maj7)", "C/E", "C#dim/E"))
        val dAeolian = ranked.first { it.tonicName == "D" && it.mode == ModeName.Aeolian }
        val cIonian = ranked.first { it.tonicName == "C" && it.mode == ModeName.Ionian }
        assertTrue(cIonian.score >= dAeolian.score)
        assertEquals("C", ranked[0].tonicName)
    }

    @Test
    fun creditsABorrowWhenTheTonicChordSounds() {
        // C#dim is the leading tone back to Dm, a textbook minor-key cadence.
        val ranked = rankKeys(chords("Dm", "C#dim", "Dm"))
        assertEquals("D Aeolian", ranked[0].name())
    }
}
