package com.omnituner.core.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordVoicingTest {

    private val tuning: ParsedTuning = when (val r = parseTuning("E2 A2 D3 G3 B3 E4")) {
        is TuningParseResult.Ok -> r.tuning
        is TuningParseResult.Error -> error("failed to parse standard tuning")
    }

    private fun chord(symbol: String): ParsedChord = when (val r = parseChord(symbol)) {
        is ChordParseResult.Ok -> r.chord
        is ChordParseResult.Error -> error("failed to parse $symbol")
    }

    private fun parseTuningOrError(raw: String): ParsedTuning = when (val r = parseTuning(raw)) {
        is TuningParseResult.Ok -> r.tuning
        is TuningParseResult.Error -> error("parse failed: ${r.error}")
    }

    @Test
    fun findsVoicingsForCMajorTriad() {
        val shapes = searchChord(tuning, chord("C"))
        assertTrue(shapes.isNotEmpty())
        assertTrue(shapes.size <= RESULTS_PER_CHORD)
    }

    @Test
    fun coversEveryRequiredChordTone() {
        val c = chord("C")
        val required = c.pcs.filter { it !in c.optionalPcs.toSet() }
        for (shape in searchChord(tuning, c)) {
            val pcs = shape.sounding.map { mod12(it.midi) }.toSet()
            for (pc in required) assertTrue(pc in pcs, "missing $pc in ${shape.frets}")
        }
    }

    @Test
    fun respectsBiomechanicalSpanOrThumbReach() {
        for (shape in searchChord(tuning, chord("Em7"))) {
            if (shape.span <= 4) continue
            val thumb = shape.frets.first()
            val others = shape.frets.drop(1).filterNotNull().filter { it > 0 }
            assertTrue(thumb != null && thumb > 0, "${shape.frets}")
            assertTrue(others.isNotEmpty(), "${shape.frets}")
            assertTrue(others.max() - others.min() <= 4, "${shape.frets}")
            assertTrue(thumb <= others.min(), "${shape.frets}")
            assertTrue(others.min() - thumb <= 4, "${shape.frets}")
        }
    }

    @Test
    fun supportsReentrantTunings() {
        val nashville = parseTuningOrError("E3 A3 D4 G4 B3 E4")
        val shapes = searchChord(nashville, chord("C"))
        assertTrue(shapes.isNotEmpty())
    }

    @Test
    fun supportsUkuleleTuning() {
        val uke = parseTuningOrError("G4 C4 E4 A4")
        val shapes = searchChord(uke, chord("C"))
        assertTrue(shapes.isNotEmpty())
    }

    @Test
    fun supportsSevenStringTuning() {
        val seven = parseTuningOrError("B1 E2 A2 D3 G3 B3 E4")
        val shapes = searchChord(seven, chord("C"))
        assertTrue(shapes.isNotEmpty())
    }

    @Test
    fun prefersOpenPositionCShape() {
        val shapes = searchChord(tuning, chord("C"))
        assertTrue(shapes.isNotEmpty())
        val first = shapes[0]
        assertTrue(first.position < 3)
        assertTrue(first.openCount > 0)
    }

    @Test
    fun ranksRootBassShapeAmongTopResults() {
        val shapes = searchChord(tuning, chord("C"))
        assertTrue(shapes.any { it.bassIsRoot })
        assertTrue(shapes[0].span <= 2)
    }

    @Test
    fun customTuningTabsMatchStringCount() {
        val custom = parseTuningOrError("D2 A2 D3 G3 A3 D4")
        val shapes = searchChord(custom, chord("G"))
        assertTrue(shapes.isNotEmpty())
        for (shape in shapes) assertEquals(6, shape.frets.size)
    }

    @Test
    fun voicesExtendedChordsWithRequiredTones() {
        val c13 = chord("C13")
        val requiredPcs = listOf(0, 4, 7, 10, 2)
        val shapes = searchChord(tuning, c13)
        assertTrue(shapes.isNotEmpty())
        for (shape in shapes) {
            val pcs = shape.sounding.map { mod12(it.midi) }.toSet()
            for (pc in requiredPcs) assertTrue(pc in pcs, "missing $pc in ${shape.frets}")
        }
    }

    @Test
    fun rejectsShapesNeedingFiveFingers() {
        val shapes = searchChord(tuning, chord("Cmaj7"))
        for (shape in shapes) {
            var runs = 0
            var prevFret: Int? = null
            for (fret in shape.frets) {
                if (fret == 0) {
                    prevFret = null
                    continue
                }
                if (fret == null) continue
                if (fret != prevFret) runs++
                prevFret = fret
            }
            assertTrue(runs <= 4, "needs $runs fingers: ${shape.frets}")
        }
    }

    @Test
    fun ranksOpenCInTop2() {
        val shapes = searchChord(tuning, chord("C"))
        val top2 = shapes.take(2)
        val canonical = listOf(null, 3, 2, 0, 1, 0)
        assertTrue(top2.any { it.frets == canonical }, "top2: ${top2.map { it.frets }}")
    }

    @Test
    fun ranksOpenGInTop2() {
        val shapes = searchChord(tuning, chord("G"))
        val top2 = shapes.take(2)
        val canonical = listOf(3, 2, 0, 0, 0, 3)
        assertTrue(top2.any { it.frets == canonical }, "top2: ${top2.map { it.frets }}")
    }

    @Test
    fun ranksOpenAmInTop2() {
        val shapes = searchChord(tuning, chord("Am"))
        val top2 = shapes.take(2)
        val a = listOf(0, 0, 2, 2, 1, 0)
        val b = listOf<Int?>(null, 0, 2, 2, 1, 0)
        assertTrue(top2.any { it.frets == a || it.frets == b }, "top2: ${top2.map { it.frets }}")
    }

    @Test
    fun coversRequiredPcsForC13Top2() {
        val shapes = searchChord(tuning, chord("C13"))
        val required = listOf(0, 4, 7, 10, 2)
        for (shape in shapes.take(2)) {
            val pcs = shape.sounding.map { mod12(it.midi) }.toSet()
            for (pc in required) assertTrue(pc in pcs, "missing $pc in ${shape.frets}")
        }
    }
}
