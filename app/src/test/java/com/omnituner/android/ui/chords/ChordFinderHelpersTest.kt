package com.omnituner.android.ui.chords

import com.omnituner.core.theory.ChordParseResult
import com.omnituner.core.theory.ParsedChord
import com.omnituner.core.theory.TuningParseResult
import com.omnituner.core.theory.parseTuning
import com.omnituner.core.theory.searchChord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordFinderHelpersTest {

    private fun resolved(token: String, tonicPc: Int): ParsedChord =
        (resolveToken(token, tonicPc) as ChordParseResult.Ok).chord

    @Test
    fun chordSymbolsWinOverNumerals() {
        assertEquals("C", resolved("C", 7).symbol)
        assertEquals("Bm", resolved("Bm", 7).symbol)
    }

    @Test
    fun numeralsAreReadInTheTonic() {
        assertEquals("Am", resolved("vi", 0).symbol)
        assertEquals("Em", resolved("vi", 7).symbol)
        assertEquals("Bb7", resolved("bVII7", 0).symbol)
    }

    @Test
    fun unreadableTokensKeepTheChordError() {
        assertTrue(resolveToken("Cfoo", 0) is ChordParseResult.Error)
    }

    @Test
    fun chordTonesMarkOptionalTones() {
        assertEquals("min · B D F#", chordTonesLabel(resolved("Bm", 0), flats = false))
        assertEquals("13 · C E G Bb D (A)", chordTonesLabel(resolved("C13", 0), flats = true))
    }

    @Test
    fun tabRowsRunHighStringFirstWithCentredFrets() {
        val tuning = (parseTuning("E2 A2 D3 G3 B3 E4") as TuningParseResult.Ok).tuning
        val shape = searchChord(tuning, resolved("C", 0)).first()
        val rows = tabRows(shape, tuning)
        assertEquals("E4   ", rows.first().label)
        assertEquals("E2   ", rows.last().label)
        assertTrue(rows.all { it.padLeft + it.symbol.length + it.padRight == 7 })
        assertEquals(shape.frets[0] == null, rows.last().muted)
    }
}
