package com.omnituner.core.theory

import com.omnituner.core.data.ScaleInterval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScaleTheoryTest {

    @Test
    fun parseNoteParsesNaturals() {
        assertEquals(0, parseNote("C"))
        assertEquals(2, parseNote("D"))
        assertEquals(4, parseNote("E"))
        assertEquals(11, parseNote("B"))
    }

    @Test
    fun parseNoteParsesSharpsAndFlatsEquivalently() {
        assertEquals(1, parseNote("C#"))
        assertEquals(1, parseNote("Db"))
        assertEquals(6, parseNote("F#"))
        assertEquals(6, parseNote("Gb"))
    }

    @Test
    fun parseNoteAcceptsUnicodeAndMixedCase() {
        assertEquals(3, parseNote("e♭"))
        assertEquals(6, parseNote("F♯"))
        assertEquals(3, parseNote("  d# "))
    }

    @Test
    fun parseNoteIgnoresOctaveDigits() {
        assertEquals(4, parseNote("E2"))
        assertEquals(10, parseNote("Bb3"))
    }

    @Test
    fun parseNoteRejectsInvalidInput() {
        assertNull(parseNote(""))
        assertNull(parseNote("H"))
        assertNull(parseNote("Z#"))
        assertNull(parseNote("xyz"))
        assertNull(parseNote("123"))
    }

    @Test
    fun noteNameSpellsSharpsAndFlats() {
        assertEquals("C", noteName(0, false))
        assertEquals("C#", noteName(1, false))
        assertEquals("F#", noteName(6, false))
        assertEquals("Db", noteName(1, true))
        assertEquals("Eb", noteName(3, true))
        assertEquals("Gb", noteName(6, true))
        assertEquals("C", noteName(0, true))
    }

    @Test
    fun noteNameWrapsOutOfRangePitchClasses() {
        assertEquals("C", noteName(12, false))
        assertEquals("B", noteName(-1, false))
    }

    @Test
    fun intervalByPitchClassMaps() {
        val intervals = listOf(
            ScaleInterval(0, "R"),
            ScaleInterval(4, "3"),
            ScaleInterval(7, "5"),
        )
        val map = intervalByPitchClass(intervals)
        assertEquals("R", map[0]?.label)
        assertEquals("3", map[4]?.label)
        assertEquals("5", map[7]?.label)
    }

    @Test
    fun intervalByPitchClassLaterWinsOnCollision() {
        val map = intervalByPitchClass(
            listOf(ScaleInterval(6, "b5"), ScaleInterval(6, "#11")),
        )
        assertEquals("#11", map[6]?.label)
    }

    @Test
    fun intervalByPitchClassFoldsBeyondOctave() {
        val map = intervalByPitchClass(listOf(ScaleInterval(14, "9")))
        assertEquals("9", map[2]?.label)
    }

    private val majorIntervals = listOf(
        ScaleInterval(0, "R"),
        ScaleInterval(2, "9"),
        ScaleInterval(4, "3"),
        ScaleInterval(5, "11"),
        ScaleInterval(7, "5"),
        ScaleInterval(9, "6"),
        ScaleInterval(11, "maj7"),
    )

    @Test
    fun computeFretboardReturnsFullMatrix() {
        val board = computeFretboard(listOf(4, 11, 7, 2, 9, 4), 15, majorIntervals, false)
        assertEquals(6, board.size)
        for (row in board) assertEquals(16, row.size)
    }

    @Test
    fun computeFretboardStandardTuningPitchClasses() {
        val board = computeFretboard(listOf(4, 11, 7, 2, 9, 4), 12, majorIntervals, false)
        val lowE = board[5]
        assertEquals(4, lowE[0].pitchClass)
        assertEquals(9, lowE[5].pitchClass)
        assertEquals(4, lowE[12].pitchClass)
    }

    @Test
    fun computeFretboardMarksRootAndColorsIt() {
        val board = computeFretboard(listOf(4, 11, 7, 2, 9, 4), 12, majorIntervals, false)
        val lowE8 = board[5][8]
        assertEquals(0, lowE8.pitchClass)
        assertTrue(lowE8.isRoot)
        assertEquals("R", lowE8.interval?.label)
        assertEquals("#779900", lowE8.color)
    }

    @Test
    fun computeFretboardUsesFlatSpellingWhenPreferred() {
        val board = computeFretboard(listOf(4, 11, 7, 2, 9, 4), 12, majorIntervals, true)
        val pc3 = board[5].first { it.pitchClass == 3 }
        assertEquals("Eb", pc3.noteName)
    }

    @Test
    fun computeFretboardNonScaleCells() {
        val board = computeFretboard(listOf(4, 11, 7, 2, 9, 4), 12, majorIntervals, false)
        val openLowE = board[5][0]
        assertTrue(openLowE.interval != null)

        val pc6 = board[5].first { it.pitchClass == 6 }
        assertNull(pc6.interval)
        assertEquals("", pc6.color)
        assertFalse(pc6.isRoot)
    }

    @Test
    fun computeFretboardHandlesArbitraryTuning() {
        val board = computeFretboard(listOf(0, 0, 0, 0, 0, 0), 12, majorIntervals, false)
        assertEquals(6, board.size)
        assertEquals(0, board[0][0].pitchClass)
    }

    @Test
    fun computeFretboardMidiNotes() {
        val board = computeFretboard(
            listOf(4, 11, 7, 2, 9, 4),
            12,
            majorIntervals,
            false,
            openMidiNotes = listOf(64, 59, 55, 50, 45, 40),
        )
        assertEquals(64, board[0][0].midi)
        assertEquals(66, board[0][2].midi)
        assertEquals(40, board[5][0].midi)
    }
}
