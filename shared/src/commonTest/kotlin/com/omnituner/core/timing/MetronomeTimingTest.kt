package com.omnituner.core.timing

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetronomeTimingTest {

    @Test
    fun meterModelSimpleMeters() {
        val model = meterModel(4, 4)
        assertFalse(model.compound)
        assertEquals(4, model.beatsPerBar)
        assertEquals(1, model.divisionsPerBeat)
        assertEquals(1.0, model.beatQuarters, 1e-12)
        assertEquals(4.0, model.barQuarters, 1e-12)
    }

    @Test
    fun meterModelDetectsCompoundMeters() {
        val sixEight = meterModel(6, 8)
        assertTrue(sixEight.compound)
        assertEquals(2, sixEight.beatsPerBar)
        assertEquals(3, sixEight.divisionsPerBeat)
        assertEquals(1.5, sixEight.beatQuarters, 1e-12)

        val nineEight = meterModel(9, 8)
        assertTrue(nineEight.compound)
        assertEquals(3, nineEight.beatsPerBar)

        assertFalse(meterModel(3, 8).compound)

        assertFalse(meterModel(6, 4).compound)
    }

    @Test
    fun describeMeter() {
        assertTrue(describeMeter(meterModel(6, 8)).startsWith("Compound 6/8"))
        assertTrue(describeMeter(meterModel(4, 4)).startsWith("4/4 — 4 quarter-note beats per bar"))
    }

    @Test
    fun buildBarEventsMergesLayersSorted() {
        val model = meterModel(2, 4)
        val events = buildBarEvents(
            model,
            subdivision = 2,
            poly = PolyEvents(enabled = true, events = 3, accentFirst = true),
        )
        val beats = events.map { it.beats }
        assertEquals(beats, beats.sorted())
        assertTrue(events.any { it.layer == "poly" && abs(it.beats - 2.0 / 3.0) < 1e-9 })
        assertEquals("downbeat", events.first { it.beats == 0.0 && it.layer == "meter" }.role)
        assertTrue(events.any { it.layer == "poly" && it.role == "polyAccent" })
    }

    @Test
    fun buildBarEventsRoles() {
        val events = buildBarEvents(meterModel(3, 4), subdivision = 2)
        assertEquals("downbeat", events[0].role)
        assertTrue(events.any { it.role == "subdivision" && it.beats == 0.5 })
        assertTrue(events.any { it.role == "beat" && it.beats == 1.0 })
    }

    @Test
    fun buildBarEventsWithoutPoly() {
        val events = buildBarEvents(meterModel(4, 4), subdivision = 1, poly = null)
        assertEquals(4, events.size)
    }

    @Test
    fun durations() {
        val model = meterModel(4, 4)
        assertEquals(0.5, quarterDuration(120.0), 1e-12)
        assertEquals(0.5, beatDuration(120.0, model), 1e-12)
        assertEquals(2.0, barDuration(120.0, model), 1e-12)
        assertEquals(0.25, subdivisionInterval(120.0, model, 2), 1e-12)
        assertEquals(4, ticksPerBar(model, 1))
    }

    @Test
    fun tickKindRoles() {
        assertEquals("downbeat", tickKind(0, 3))
        assertEquals("beat", tickKind(3, 3))
        assertEquals("subdivision", tickKind(1, 3))
        assertEquals("subdivision", tickKind(2, 3))
    }

    @Test
    fun polyTimesEvenlySpaced() {
        val times = polyTimes(10.0, 2.0, 4)
        assertEquals(listOf(10.0, 10.5, 11.0, 11.5), times)
        assertTrue(polyTimes(0.0, 1.0, 0).isEmpty())
    }

    @Test
    fun isBarAudibleWrapsPattern() {
        assertTrue(isBarAudible(0, listOf(1, 0)))
        assertFalse(isBarAudible(1, listOf(1, 0)))
        assertTrue(isBarAudible(2, listOf(1, 0)))
        assertTrue(isBarAudible(0, emptyList()))
        assertTrue(isBarAudible(0, listOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun tapBpmAveragesValidIntervals() {
        assertNull(tapBpm(emptyList()))
        assertNull(tapBpm(listOf(50.0, 3000.0)))
        assertEquals(120.0, tapBpm(listOf(500.0, 500.0, 500.0))!!, 1e-9)
        assertEquals(150.0, tapBpm(listOf(400.0))!!, 1e-9)
        assertEquals(120.0, tapBpm(listOf(119.0, 500.0, 2500.1, 500.0))!!, 1e-9)
    }

    @Test
    fun formatBarDuration() {
        assertEquals("500 ms", formatBarDuration(500.0))
        assertEquals("2.00 s", formatBarDuration(2000.0))
        assertEquals("1.50 s", formatBarDuration(1500.0))
    }

    @Test
    fun tempoMarkings() {
        assertEquals("Grave", getTempoMarking(30.0))
        assertEquals("Lento", getTempoMarking(50.0))
        assertEquals("Adagio", getTempoMarking(70.0))
        assertEquals("Andante", getTempoMarking(90.0))
        assertEquals("Moderato", getTempoMarking(115.0))
        assertEquals("Allegro", getTempoMarking(140.0))
        assertEquals("Presto", getTempoMarking(180.0))
        assertEquals("Prestissimo", getTempoMarking(220.0))
    }
}
