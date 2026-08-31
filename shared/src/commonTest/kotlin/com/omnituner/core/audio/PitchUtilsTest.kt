package com.omnituner.core.audio

import com.omnituner.core.data.NamedFrequency
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val GUITAR_STANDARD = listOf(
    NamedFrequency("E2", 82.41),
    NamedFrequency("A2", 110.0),
    NamedFrequency("D3", 146.83),
    NamedFrequency("G3", 196.0),
    NamedFrequency("B3", 246.94),
    NamedFrequency("E4", 329.63),
)

class PitchUtilsTest {

    @Test
    fun nearestStringTargetPicksNearestAndMeasuresUnclampedCents() {
        val at = nearestStringTarget(40.0, GUITAR_STANDARD)!!
        assertEquals("E2", at.name)
        assertEquals(40, at.midi)
        assertEquals(0.0, at.cents, 1e-9)

        val sharp = nearestStringTarget(40.6, GUITAR_STANDARD)!!
        assertEquals("E2", sharp.name)
        assertEquals(40, sharp.midi)
        assertEquals(60.0, sharp.cents, 1e-6)
    }

    @Test
    fun nearestStringTargetIsOctaveAware() {
        val target = nearestStringTarget(64.0, GUITAR_STANDARD)!!
        assertEquals("E4", target.name)
        assertEquals(64, target.midi)
        assertEquals(0.0, target.cents, 1e-9)
    }

    @Test
    fun nearestStringTargetKeepsPreviousWithinHysteresis() {
        assertEquals("A2", nearestStringTarget(42.55, GUITAR_STANDARD)!!.name)
        assertEquals("E2", nearestStringTarget(42.55, GUITAR_STANDARD, "E2")!!.name)
        assertEquals("A2", nearestStringTarget(42.6, GUITAR_STANDARD, "E2")!!.name)
    }

    @Test
    fun nearestStringTargetIgnoresUnknownPreviousName() {
        assertEquals("A2", nearestStringTarget(45.0, GUITAR_STANDARD, "C3")!!.name)
    }

    @Test
    fun nearestStringTargetRejectsInvalidInput() {
        assertNull(nearestStringTarget(null, GUITAR_STANDARD))
        assertNull(nearestStringTarget(Double.NaN, GUITAR_STANDARD))
        assertNull(nearestStringTarget(40.0, emptyList()))
    }

    @Test
    fun midiNoteHelpers() {
        assertEquals("E2", midiNoteLabel(40))
        assertEquals("A4", midiNoteLabel(69))
        assertEquals(440.0, midiNoteToFrequency(69), 1e-9)
        assertEquals(82.41, midiNoteToFrequency(40), 0.005)
    }

    @Test
    fun frequencyToMidiNote() {
        assertEquals(69, frequencyToMidiNote(440.0))
        assertEquals(60, frequencyToMidiNote(261.63))
        assertNull(frequencyToMidiNote(0.0))
        assertNull(frequencyToMidiNote(Double.NaN))
    }

    @Test
    fun frequencyToMidiFloat() {
        assertEquals(69.0, frequencyToMidiFloat(440.0)!!, 1e-12)
        assertEquals(69.0392, frequencyToMidiFloat(441.0)!!, 1e-3)
        assertNull(frequencyToMidiFloat(0.0))
        assertNull(frequencyToMidiFloat(Double.POSITIVE_INFINITY))
    }

    @Test
    fun a4CalibrationShiftsWithReferencePitch() {
        assertEquals(442.0, midiNoteToFrequency(69, 442.0), 1e-12)
        assertEquals(415.0, midiNoteToFrequency(69, 415.0), 1e-12)
        assertEquals(466.0, midiNoteToFrequency(69, 466.0), 1e-12)
        assertEquals(440.0, midiNoteToFrequency(69), 1e-12)

        assertEquals(69, frequencyToMidiNote(442.0, 442.0))
        assertEquals(69, frequencyToMidiNote(440.0, 442.0))

        assertEquals(69.0, frequencyToMidiFloat(442.0, 442.0)!!, 1e-12)
        assertEquals(68.92, frequencyToMidiFloat(440.0, 442.0)!!, 0.01)
    }

    @Test
    fun centsFromMidiFloatIsUnclamped() {
        assertEquals(0.0, centsFromMidiFloat(69.0, 69)!!, 1e-12)
        assertEquals(10.0, centsFromMidiFloat(69.1, 69)!!, 1e-2)
        assertEquals(300.0, centsFromMidiFloat(67.0, 64)!!, 1e-2)
        assertNull(centsFromMidiFloat(null, 64))
    }

    @Test
    fun tuneDirectionText() {
        assertEquals("IN TUNE", tuneDirectionText(0.0))
        assertEquals("IN TUNE", tuneDirectionText(-3.2))
        assertEquals("TUNE DOWN", tuneDirectionText(12.4))
        assertEquals("TUNE UP", tuneDirectionText(-187.7))
        assertEquals("—", tuneDirectionText(null))
        assertEquals("—", tuneDirectionText(Double.NaN))
    }

    @Test
    fun tuneDirectionTreatsThresholdBoundaryAsInTune() {
        assertEquals("IN TUNE", tuneDirectionText(5.0))
        assertEquals("IN TUNE", tuneDirectionText(8.0, 8.0))
        assertEquals("IN TUNE", tuneDirectionText(-8.0, 8.0))
    }

    @Test
    fun tuneDirectionHonorsCustomThreshold() {
        assertEquals("IN TUNE", tuneDirectionText(6.0, 8.0))
        assertEquals("IN TUNE", tuneDirectionText(-6.0, 8.0))
        assertEquals("TUNE DOWN", tuneDirectionText(9.0, 8.0))
        assertEquals("TUNE UP", tuneDirectionText(-9.0, 8.0))
        assertEquals("TUNE DOWN", tuneDirectionText(6.0))
    }

    @Test
    fun tuneCentsText() {
        assertEquals("", tuneCentsText(0.0))
        assertEquals("", tuneCentsText(-3.2))
        assertEquals("12¢", tuneCentsText(12.4))
        assertEquals("188¢", tuneCentsText(-187.7))
        assertEquals("", tuneCentsText(null))
        assertEquals("", tuneCentsText(Double.NaN))
    }

    @Test
    fun tuneCentsTreatsThresholdBoundaryAsInTune() {
        assertEquals("", tuneCentsText(5.0))
        assertEquals("", tuneCentsText(8.0, 8.0))
        assertEquals("", tuneCentsText(-8.0, 8.0))
    }

    @Test
    fun tuneCentsHonorsCustomThreshold() {
        assertEquals("", tuneCentsText(6.0, 8.0))
        assertEquals("", tuneCentsText(-6.0, 8.0))
        assertEquals("9¢", tuneCentsText(9.0, 8.0))
        assertEquals("9¢", tuneCentsText(-9.0, 8.0))
        assertEquals("6¢", tuneCentsText(6.0))
    }

    @Test
    fun nearestSemitoneRounds() {
        assertEquals(69, nearestSemitone(69.0))
        assertEquals(69, nearestSemitone(69.1))
        assertEquals(70, nearestSemitone(69.6))
        assertNull(nearestSemitone(null))
        assertNull(nearestSemitone(Double.NaN))
    }

    @Test
    fun interpolateColorBlendsLinearly() {
        assertEquals("#808080", interpolateColor("#000000", "#ffffff", 0.5))
        assertEquals("#404040", interpolateColor("#000000", "#ffffff", 0.25))
        assertEquals("#808000", interpolateColor("#ff0000", "#00ff00", 0.5))
    }

    @Test
    fun interpolateColorClampsT() {
        assertEquals("#000000", interpolateColor("#000000", "#ffffff", -1.0))
        assertEquals("#ffffff", interpolateColor("#000000", "#ffffff", 2.0))
    }

    @Test
    fun interpolateColorRejectsInvalidColors() {
        assertNull(interpolateColor("red", "#ffffff", 0.5))
        assertNull(interpolateColor("#000000", "blue", 0.5))
        assertNull(interpolateColor("#123", "#ffffff", 0.5))
    }

    @Test
    fun tuneColorProgressMapsTheBlendWindow() {
        assertEquals(0.0, tuneColorProgress(50.0), 1e-12)
        assertEquals(0.0, tuneColorProgress(-50.0), 1e-12)
        assertEquals(1.0, tuneColorProgress(5.0), 1e-12)
        assertEquals(1.0, tuneColorProgress(-5.0), 1e-12)
        assertEquals(0.5, tuneColorProgress(27.5), 1e-5)
        assertEquals(1.0, tuneColorProgress(3.0), 1e-12)
        assertEquals(0.0, tuneColorProgress(null), 1e-12)
        assertEquals(0.0, tuneColorProgress(Double.NaN), 1e-12)
    }

    @Test
    fun tuneColorProgressEndsAtCustomThreshold() {
        assertEquals(1.0, tuneColorProgress(8.0, 8.0), 1e-12)
        assertEquals(1.0, tuneColorProgress(-8.0, 8.0), 1e-12)
        assertEquals(0.5, tuneColorProgress(29.0, 8.0), 1e-5)
        assertEquals(0.0, tuneColorProgress(50.0, 8.0), 1e-12)
        assertEquals(1.0, tuneColorProgress(3.0, 1.0), 1e-12)
    }

    @Test
    fun needlePercentClampsToRuler() {
        assertEquals(50.0, needlePercentFromCents(0.0), 1e-12)
        assertEquals(75.0, needlePercentFromCents(25.0), 1e-12)
        assertEquals(25.0, needlePercentFromCents(-25.0), 1e-12)
        assertEquals(100.0, needlePercentFromCents(200.0), 1e-12)
        assertEquals(0.0, needlePercentFromCents(-200.0), 1e-12)
        assertEquals(50.0, needlePercentFromCents(null), 1e-12)
        assertTrue(abs(needlePercentFromCents(50.0) - 100.0) < 1e-12)
    }

    @Test
    fun shouldConfirmRequiresFullHold() {
        assertEquals(false, shouldConfirm(inRange = true, elapsedMs = 499, holdMs = 500))
        assertEquals(true, shouldConfirm(inRange = true, elapsedMs = 500, holdMs = 500))
        assertEquals(true, shouldConfirm(inRange = true, elapsedMs = 0, holdMs = 0))
        assertEquals(false, shouldConfirm(inRange = false, elapsedMs = 5000, holdMs = 0))
    }
}
