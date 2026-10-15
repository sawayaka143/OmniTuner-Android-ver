package com.omnituner.core.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordVoicingTest {

    private fun tuning(raw: String): ParsedTuning = when (val r = parseTuning(raw)) {
        is TuningParseResult.Ok -> r.tuning
        is TuningParseResult.Error -> error("parse failed: ${r.error}")
    }

    private val standard = tuning("E2 A2 D3 G3 B3 E4")

    private fun chord(symbol: String): ParsedChord = when (val r = parseChord(symbol)) {
        is ChordParseResult.Ok -> r.chord
        is ChordParseResult.Error -> error("failed to parse $symbol")
    }

    private fun key(frets: List<Int?>): String = frets.joinToString(",") { it?.toString() ?: "x" }

    private fun fretsOf(symbol: String, t: ParsedTuning = standard, count: Int = DEFAULT_VOICING_COUNT): List<String> =
        searchChord(t, chord(symbol), count).map { key(it.frets) }

    private fun pcsOf(shape: VoicingShape): Set<Int> = shape.sounding.map { mod12(it.midi) }.toSet()

    @Test
    fun dropsTheFifthOnceAThirdAndSeventhOrSixthArePresent() {
        for (symbol in listOf("Cmaj7", "Cm7", "C7", "Cm9", "Cmaj9", "Cm11", "CmMaj7", "C6", "Cm6", "C6/9", "Cmaj7#11")) {
            val parsed = chord(symbol)
            assertTrue(7 in parsed.intervals, symbol)
            assertFalse(mod12(parsed.rootPc + 7) in requiredPitchClasses(parsed), symbol)
        }
    }

    @Test
    fun keepsTheFifthWithoutAThirdOrSeventh() {
        for (symbol in listOf("C", "Cm", "Cadd9", "Cmadd9", "Csus2", "Csus4", "C7sus2", "C7sus4", "C5")) {
            val parsed = chord(symbol)
            assertTrue(7 in parsed.intervals, symbol)
            assertTrue(mod12(parsed.rootPc + 7) in requiredPitchClasses(parsed), symbol)
        }
    }

    @Test
    fun keepsGuideTonesAndAlteredFifths() {
        val required = requiredPitchClasses(chord("C13"))
        for (pc in listOf(0, 4, 10)) assertTrue(pc in required)
        assertFalse(7 in required)
        assertFalse(2 in required)

        val altered = requiredPitchClasses(chord("C7#5"))
        assertTrue(8 in altered)
        assertFalse(7 in altered)
    }

    @Test
    fun noRequiredExtensionsWithoutTensions() {
        for (symbol in listOf("C", "Cm", "Caug", "C7", "Cmaj7")) {
            assertTrue(requiredExtensions(chord(symbol)).isEmpty(), symbol)
        }
    }

    @Test
    fun requiresTheHighestNaturalTension() {
        val cases = mapOf("C9" to listOf(2), "C11" to listOf(5), "C13" to listOf(9), "Cmaj13" to listOf(9), "Cm11" to listOf(5), "C6/9" to listOf(2))
        for ((symbol, expected) in cases) assertEquals(expected, requiredExtensions(chord(symbol)).sorted(), symbol)
    }

    @Test
    fun requiresEveryAlteredTension() {
        val cases = mapOf("C7b9" to listOf(1), "C7#9" to listOf(3), "C13b9" to listOf(1, 9), "C7#9b13" to listOf(3, 8), "C9#11" to listOf(6))
        for ((symbol, expected) in cases) assertEquals(expected, requiredExtensions(chord(symbol)).sorted(), symbol)
    }

    // symbol, frets (low string first), worst acceptable rank
    private val grips = listOf(
        Triple("C", "x,3,2,0,1,0", 1), Triple("C", "x,3,5,5,5,3", 3),
        Triple("D", "x,x,0,2,3,2", 1), Triple("D", "x,5,7,7,7,5", 2),
        Triple("E", "0,2,2,1,0,0", 1), Triple("F", "1,3,3,2,1,1", 1),
        Triple("G", "3,2,0,0,0,3", 1), Triple("A", "x,0,2,2,2,0", 1),
        Triple("A", "5,7,7,6,5,5", 2), Triple("B", "x,2,4,4,4,2", 1),
        Triple("B", "7,9,9,8,7,7", 2), Triple("Bb", "x,1,3,3,3,1", 2),
        Triple("Bb", "x,1,0,3,3,1", 2), Triple("Eb", "x,6,8,8,8,6", 2),
        Triple("Ab", "4,6,6,5,4,4", 1), Triple("C#", "x,4,6,6,6,4", 1),
        Triple("F#", "2,4,4,3,2,2", 1), Triple("Am", "x,0,2,2,1,0", 1),
        Triple("Am", "5,7,7,5,5,5", 2), Triple("Em", "0,2,2,0,0,0", 1),
        Triple("Dm", "x,x,0,2,3,1", 1), Triple("Bm", "x,2,4,4,3,2", 1),
        Triple("Bm", "7,9,9,7,7,7", 2), Triple("C#m", "x,4,6,6,5,4", 1),
        Triple("F#m", "2,4,4,2,2,2", 1), Triple("Fm", "1,3,3,1,1,1", 1),
        Triple("Cm", "x,3,5,5,4,3", 2), Triple("Gm", "3,5,5,3,3,3", 2),
        Triple("C7", "x,3,2,3,1,0", 1), Triple("G7", "3,2,0,0,0,1", 1),
        Triple("D7", "x,x,0,2,1,2", 1), Triple("E7", "0,2,0,1,0,0", 1),
        Triple("A7", "x,0,2,0,2,0", 1), Triple("B7", "x,2,1,2,0,2", 1),
        Triple("F7", "1,3,1,2,1,1", 1), Triple("Am7", "x,0,2,0,1,0", 1),
        Triple("Em7", "0,2,0,0,0,0", 1), Triple("Dm7", "x,x,0,2,1,1", 1),
        Triple("Bm7", "x,2,0,2,0,2", 1), Triple("Cmaj7", "x,3,2,0,0,0", 1),
        Triple("Dmaj7", "x,x,0,2,2,2", 1), Triple("Amaj7", "x,0,2,1,2,0", 1),
        Triple("Gmaj7", "3,x,4,4,3,x", 3), Triple("F#maj7", "2,x,3,3,2,x", 1),
        Triple("Ebmaj7", "x,x,1,3,3,3", 2), Triple("Dsus4", "x,x,0,2,3,3", 1),
        Triple("Asus2", "x,0,2,2,0,0", 1), Triple("Caug", "x,3,2,1,1,0", 1),
        Triple("Cadd9", "x,3,2,0,3,0", 3), Triple("Cmaj9", "x,3,2,4,3,x", 3),
        Triple("Cm9", "x,3,1,3,3,x", 1), Triple("Dm9", "x,5,3,5,5,x", 1),
        Triple("B7b13", "7,x,7,8,8,x", 2),
    )

    @Test
    fun ranksStandardGripsNearTheTop() {
        val failures = grips.mapNotNull { (symbol, frets, maxRank) ->
            val ranked = fretsOf(symbol)
            val rank = ranked.indexOf(frets) + 1
            if (rank in 1..maxRank) null else "$symbol $frets at rank $rank in $ranked"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun neverListsAShapeTwiceWithOnlyStringsDropped() {
        for (symbol in listOf("C", "G", "D", "A", "E", "F", "B", "Bb", "Am", "Bm", "F#m", "C7", "Cmaj7", "Cm9")) {
            val shapes = searchChord(standard, chord(symbol), MAX_VOICING_COUNT)
            for (a in shapes) for (b in shapes) {
                if (a !== b) assertFalse(isNarrowingOf(a.frets, b.frets), "$symbol ${a.frets} vs ${b.frets}")
            }
        }
    }

    @Test
    fun keepsTheFullGripAndHidesItWithAStringDropped() {
        val g = fretsOf("G")
        assertEquals("3,2,0,0,0,3", g[0])
        assertFalse("3,2,0,0,0,x" in g)
        assertFalse("x,2,x,4,4,2" in fretsOf("B"))
    }

    @Test
    fun returnsTheRequestedNumberOfFingerings() {
        assertEquals(1, searchChord(standard, chord("C"), 1).size)
        assertEquals(DEFAULT_VOICING_COUNT, searchChord(standard, chord("C")).size)
        assertEquals(MAX_VOICING_COUNT, searchChord(standard, chord("C"), MAX_VOICING_COUNT).size)
    }

    @Test
    fun longerListStartsWithTheShorterOne() {
        assertEquals(fretsOf("Bm", count = 3), fretsOf("Bm", count = MAX_VOICING_COUNT).take(3))
    }

    @Test
    fun isNarrowingOfMatchesMutedVersions() {
        assertTrue(isNarrowingOf(listOf(3, 2, 0, 0, 0, null), listOf(3, 2, 0, 0, 0, 3)))
        assertTrue(isNarrowingOf(listOf(3, 2, 0, 0, 0, 3), listOf(3, 2, 0, 0, 0, 3)))
        assertFalse(isNarrowingOf(listOf(3, 2, 0, 0, 3, 3), listOf(3, 2, 0, 0, 0, 3)))
        assertFalse(isNarrowingOf(listOf(3, 2, 0, 0, 0, 3), listOf(3, 2, 0, 0, 0, null)))
    }

    @Test
    fun analyseFingeringCountsFingersAndBarres() {
        assertEquals(Fingering(3, 0), analyseFingering(listOf(null, 3, 2, 0, 1, 0)))
        assertEquals(Fingering(3, 0), analyseFingering(listOf(null, 0, 2, 2, 2, 0)))
        assertEquals(Fingering(4, 1), analyseFingering(listOf(1, 3, 3, 2, 1, 1)))
        assertEquals(Fingering(3, 1), analyseFingering(listOf(null, 2, 4, 4, 4, 2)))
        // A barre can't run across an open string or a lower fret.
        assertNull(analyseFingering(listOf(2, 3, 0, 4, 3, 2)))
    }

    @Test
    fun ranksStandardUkuleleShapesFirst() {
        val ukulele = tuning("G4 C4 E4 A4")
        val cases = mapOf("C" to "0,0,0,3", "G" to "0,2,3,2", "F" to "2,0,1,0", "Am" to "2,0,0,0", "D" to "2,2,2,0")
        for ((symbol, frets) in cases) assertEquals(frets, fretsOf(symbol, ukulele)[0], symbol)
    }

    @Test
    fun coversEveryRequiredChordToneInEveryShape() {
        val c = chord("C")
        for (shape in searchChord(standard, c)) assertTrue(pcsOf(shape).containsAll(listOf(0, 4, 7)), key(shape.frets))
    }

    @Test
    fun respectsTheSpanOrThumbReach() {
        for (shape in searchChord(standard, chord("Em7"))) {
            if (shape.span <= 4) continue
            val thumb = assertNotNull(shape.frets[0], key(shape.frets))
            val others = shape.frets.drop(1).filterNotNull().filter { it > 0 }
            assertTrue(thumb > 0 && others.isNotEmpty(), key(shape.frets))
            assertTrue(others.max() - others.min() <= 4, key(shape.frets))
            assertTrue(thumb <= others.min() && others.min() - thumb <= 4, key(shape.frets))
        }
    }

    @Test
    fun supportsOtherTunings() {
        assertTrue(searchChord(tuning("E3 A3 D4 G4 B3 E4"), chord("C")).isNotEmpty())
        assertTrue(searchChord(tuning("G4 C4 E4 A4"), chord("C")).isNotEmpty())
        assertTrue(searchChord(tuning("B1 E2 A2 D3 G3 B3 E4"), chord("C")).isNotEmpty())
        val dadgad = searchChord(tuning("D2 A2 D3 G3 A3 D4"), chord("G"))
        assertTrue(dadgad.isNotEmpty())
        for (shape in dadgad) assertEquals(6, shape.frets.size)
    }

    @Test
    fun prefersOpenPositionC() {
        val first = searchChord(standard, chord("C")).first()
        assertTrue(first.position < 3)
        assertTrue(first.openCount > 0)
        assertTrue(first.span <= 2)
    }

    @Test
    fun voicesA13ChordWithGuideTonesAndThe13th() {
        val shapes = searchChord(standard, chord("C13"), MAX_VOICING_COUNT)
        assertTrue(shapes.isNotEmpty())
        for (shape in shapes) assertTrue(pcsOf(shape).containsAll(listOf(0, 4, 10, 9)), key(shape.frets))
    }

    @Test
    fun voicesTwoNotePowerChords() {
        for (symbol in listOf("C5", "G5", "E5")) {
            val shapes = searchChord(standard, chord(symbol))
            assertTrue(shapes.isNotEmpty(), symbol)
            for (shape in shapes) assertEquals(2, pcsOf(shape).size, "$symbol ${key(shape.frets)}")
        }
    }

    @Test
    fun onlyReturnsShapesFourFingersCanHold() {
        for (symbol in listOf("Cmaj7", "F", "Bm", "C13", "Ebmaj7")) {
            for (shape in searchChord(standard, chord(symbol), MAX_VOICING_COUNT)) {
                val fingering = assertNotNull(analyseFingering(shape.frets), "$symbol ${key(shape.frets)}")
                assertTrue(fingering.fingers <= 4)
            }
        }
    }

    @Test
    fun putsTheRequestedBassUnderSlashChords() {
        val cases = mapOf("C/E" to 4, "C/G" to 7, "D/F#" to 6, "Am/G" to 7, "C#dim/E" to 4, "A7#5/C#" to 1, "Cmaj7/B" to 11)
        for ((symbol, bass) in cases) {
            val shapes = searchChord(standard, chord(symbol))
            assertTrue(shapes.isNotEmpty(), symbol)
            for (shape in shapes) assertEquals(bass, mod12(shape.bassMidi), "$symbol ${key(shape.frets)}")
        }
    }

    @Test
    fun leavesPlainChordsUnconstrained() {
        assertTrue(searchChord(standard, chord("C"))[0].bassIsRoot)
    }

    @Test
    fun fallsBackWhenTheBassIsUnreachable() {
        assertTrue(searchChord(standard, chord("C/D")).isNotEmpty())
        val constrained = searchChord(standard, chord("Am/G"))
        assertEquals(3, constrained[0].frets[0], key(constrained[0].frets))
    }
}
