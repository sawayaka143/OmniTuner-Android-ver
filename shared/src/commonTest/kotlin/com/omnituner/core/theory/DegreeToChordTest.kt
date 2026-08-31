package com.omnituner.core.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DegreeToChordTest {

    @Test
    fun mapsDiatonicDegreesInCMajor() {
        assertEquals("C", degreeToChordSymbol("I", 0, false))
        assertEquals("Am", degreeToChordSymbol("vi", 0, false))
        assertEquals("Dm", degreeToChordSymbol("ii", 0, false))
        assertEquals("F", degreeToChordSymbol("IV", 0, false))
        assertEquals("G", degreeToChordSymbol("V", 0, false))
    }

    @Test
    fun handlesBorrowedFlats() {
        assertEquals("Bb", degreeToChordSymbol("bVII", 0, true))
        assertEquals("A#", degreeToChordSymbol("bVII", 0, false))
        assertEquals("Eb", degreeToChordSymbol("bIII", 0, true))
    }

    @Test
    fun passesSuffixesThrough() {
        assertEquals("Dm7", degreeToChordSymbol("ii7", 0, false))
        assertEquals("G7", degreeToChordSymbol("V7", 0, false))
        assertEquals("Cmaj7", degreeToChordSymbol("Imaj7", 0, false))
        assertEquals("A7", degreeToChordSymbol("VI7", 0, false))
    }

    @Test
    fun spellsFlatsWhenRequested() {
        val gPc = tonicPcOf("G")
        assertNotNull(gPc)
        assertEquals("Gm", degreeToChordSymbol("i", gPc, true))
        assertEquals("Eb", degreeToChordSymbol("bVI", gPc, true))
        assertEquals("D#", degreeToChordSymbol("bVI", gPc, false))
    }

    @Test
    fun everyPresetDegreeProducesParseableChord() {
        val roots = listOf(0, 2, 4, 5, 7, 9, 11)
        val degrees = listOf("I", "vi", "bVII", "bIII", "ii7", "V7", "Imaj7", "i", "iv", "bVI", "bII")
        for (pc in roots) {
            for (d in degrees) {
                val sym = degreeToChordSymbol(d, pc, false)
                assertNotNull(sym, "$d in pc $pc")
                val parsed = parseChord(sym)
                assertTrue(parsed is ChordParseResult.Ok, "$d in pc $pc -> $sym")
            }
        }
    }

    @Test
    fun keepsClassicMixtureAtBb() {
        val bbPc = tonicPcOf("Bb")
        assertNotNull(bbPc)
        assertEquals(
            listOf("Bbm", "G", "D", "A"),
            degreesToProgression(listOf("i", "VI", "III", "VII"), bbPc, true),
        )
    }

    @Test
    fun transposesDegreesToOtherTonics() {
        assertEquals(
            listOf("Cm", "A", "E", "B"),
            degreesToProgression(listOf("i", "VI", "III", "VII"), 0, false),
        )
        assertEquals(
            listOf("Dm", "B", "F#", "C#"),
            degreesToProgression(listOf("i", "VI", "III", "VII"), 2, false),
        )
    }

    @Test
    fun capsResultAtSixChords() {
        val progression = degreesToProgression(
            listOf("I", "I", "IV", "V", "IV", "I", "V"),
            0,
            false,
        )
        assertEquals(6, progression.size)
    }

    @Test
    fun tonicPcParsesAccidentals() {
        assertEquals(0, tonicPcOf("C"))
        assertEquals(6, tonicPcOf("F#"))
        assertEquals(10, tonicPcOf("Bb"))
    }
}
