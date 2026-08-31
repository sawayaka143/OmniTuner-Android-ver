package com.omnituner.core.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyDetectorTest {

    private fun chord(symbol: String): ParsedChord {
        val result = parseChord(symbol)
        assertTrue(result is ChordParseResult.Ok, "parse failed: $symbol")
        return (result as ChordParseResult.Ok).chord
    }

    private fun chords(vararg symbols: String): List<ParsedChord> = symbols.map { chord(it) }

    @Test
    fun detectsCIonianForCFG() {
        val dk = detectKey(chords("C", "F", "G"))
        assertTrue(dk != null)
        assertEquals("C", dk!!.tonicName)
        assertEquals(ModeName.Ionian, dk.mode)
        assertEquals("strong", dk.confidence)
    }

    @Test
    fun resolvesAmDmEToCIonianScoringAAeolianEqually() {
        val progression = chords("Am", "Dm", "E")
        val dk = detectKey(progression)
        assertTrue(dk != null)
        assertEquals("C", dk!!.tonicName)
        assertEquals(ModeName.Ionian, dk.mode)

        val ranked = rankKeys(progression)
        val aeolian = ranked.first { it.tonicName == "A" && it.mode == ModeName.Aeolian }
        assertEquals(ranked[0].score, aeolian.score)
    }

    @Test
    fun detectsCIonianForIiViI() {
        val dk = detectKey(chords("Dm7", "G7", "Cmaj7"))
        assertTrue(dk != null)
        assertEquals("C", dk!!.tonicName)
        assertEquals(ModeName.Ionian, dk.mode)
    }

    @Test
    fun spellsFlatsForBbEbF() {
        val dk = detectKey(chords("Bb", "Eb", "F"))
        assertTrue(dk != null)
        assertEquals("Bb", dk!!.tonicName)
        assertEquals(ModeName.Ionian, dk.mode)
    }

    @Test
    fun handlesSingleChord() {
        val dk = detectKey(chords("C"))
        assertTrue(dk != null)
        assertEquals("C", dk!!.tonicName)
    }

    @Test
    fun returnsWeakOrModerateForChromaticProgression() {
        val dk = detectKey(chords("Cmaj7", "Bbmaj7", "Abmaj7"))
        assertTrue(dk != null)
        assertTrue(dk!!.confidence == "weak" || dk.confidence == "moderate")
    }

    @Test
    fun returnsNullForEmpty() {
        assertNull(detectKey(emptyList()))
    }

    @Test
    fun detectsCIonianForPopProgression() {
        val dk = detectKey(chords("C", "G", "Am", "F"))
        assertTrue(dk != null)
        assertEquals("C", dk!!.tonicName)
        assertEquals(ModeName.Ionian, dk.mode)
    }

    @Test
    fun rankKeysReturnsSortedList() {
        val ranked = rankKeys(chords("C", "F", "G"))
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked[0].score >= ranked[1].score)
    }

    @Test
    fun alternativesPresentForStrongKey() {
        val dk = detectKey(chords("C", "F", "G", "Am"))
        assertEquals("C", dk!!.tonicName)
        assertEquals(ModeName.Ionian, dk.mode)
        assertEquals("strong", dk.confidence)
        assertTrue(dk.alternatives.isNotEmpty())
        for (alt in dk.alternatives) {
            assertTrue(alt.score <= dk.score)
        }
    }

    @Test
    fun respectsFlatSpellingForFlatChords() {
        val dk = detectKey(chords("Bb", "Eb"))
        assertTrue(dk!!.tonicName.contains("b"))
    }
}
