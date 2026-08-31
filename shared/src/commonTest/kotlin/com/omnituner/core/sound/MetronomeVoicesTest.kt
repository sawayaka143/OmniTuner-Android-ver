package com.omnituner.core.sound

import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.random.Random

class MetronomeVoicesTest {

    private val sampleRate = 48000.0

    @Test
    fun optionsCoverTheTenVoices() {
        assertEquals(10, MetronomeVoices.options().size)
        for (voice in MetronomeVoices.options()) {
            assertTrue(MetronomeVoices.has(voice.id))
        }
    }

    @Test
    fun unknownIdFallsBackToBeepMid() {
        val fallback = MetronomeVoices.render("nope", sampleRate, 1.0, Random(1))
        val beepMid = MetronomeVoices.render("beep-mid", sampleRate, 1.0, Random(1))
        assertEquals(beepMid.size, fallback.size)
        for (i in fallback.indices) {
            assertEquals(beepMid[i], fallback[i], 1e-6f)
        }
    }

    @Test
    fun velocityClampedToWebBounds() {
        assertEquals(0.0001, MetronomeVoices.clampVelocity(0.0), 1e-12)
        assertEquals(1.2, MetronomeVoices.clampVelocity(5.0), 1e-12)
        assertEquals(0.5, MetronomeVoices.clampVelocity(0.5), 1e-12)
    }

    @Test
    fun beepHiRingsAtExpectedFrequency() {
        val buffer = MetronomeVoices.render("beep-hi", sampleRate, 1.0, Random(2))
        // 1760 Hz for 0.05s = 2400 cycles/s * 0.05 = 88 cycles; count sign changes ~ 176 per direction
        var crossings = 0
        for (i in 1 until buffer.size) {
            if ((buffer[i - 1] < 0 && buffer[i] >= 0)) crossings++
        }
        val duration = buffer.size / sampleRate
        val estimatedFreq = crossings / duration
        assertTrue(abs(estimatedFreq - 1760.0) < 60.0, "estimated $estimatedFreq Hz")
    }

    @Test
    fun envelopesRespectPeakAndFloor() {
        val buffer = MetronomeVoices.render("beep-hi", sampleRate, 1.0, Random(3))
        val peak = buffer.max()
        assertTrue(peak <= 1.0 + 1e-3, "peak $peak")
        assertTrue(peak > 0.9, "peak $peak")
        // tail is at the envelope floor
        val tail = buffer[buffer.size - 1].toDouble()
        assertTrue(abs(tail) <= 0.001, "tail $tail")
    }

    @Test
    fun noiseVoicesAreDeterministicWithSeed() {
        val a = MetronomeVoices.render("snare", sampleRate, 0.9, Random(42))
        val b = MetronomeVoices.render("snare", sampleRate, 0.9, Random(42))
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i], b[i], 1e-9f)
        }
    }

    @Test
    fun cowbellIsLongerThanBeeps() {
        val cowbell = MetronomeVoices.render("cowbell", sampleRate, 1.0, Random(4))
        val beep = MetronomeVoices.render("beep-hi", sampleRate, 1.0, Random(4))
        assertTrue(cowbell.size > beep.size * 2)
        // 0.22s stop vs 0.05 + tail
        assertTrue(cowbell.size / sampleRate > 0.22)
    }

    @Test
    fun allVoicesRenderWithoutNaN() {
        for (voice in MetronomeVoices.options()) {
            val buffer = MetronomeVoices.render(voice.id, sampleRate, 1.0, Random(5))
            assertTrue(buffer.isNotEmpty(), voice.id)
            assertTrue(buffer.all { it.isFinite() }, voice.id)
        }
    }

    @Test
    fun lowVelocityRendersQuietly() {
        val loud = MetronomeVoices.render("click", sampleRate, 1.0, Random(6))
        val quiet = MetronomeVoices.render("click", sampleRate, 0.1, Random(6))
        // envelope scales with velocity: max ratio ~ 0.1 (clamped floor aside)
        val loudPeak = loud.map { abs(it) }.max()
        val quietPeak = quiet.map { abs(it) }.max()
        assertTrue(quietPeak < loudPeak * 0.25)
    }
}

class NoteSynthTest {

    private val sampleRate = 48000.0

    @Test
    fun noteLengthRespectsMinimumDuration() {
        assertEquals(0.08 + NoteSynth.STOP_TAIL_SECONDS, NoteSynth.noteLengthSeconds(0.01), 1e-12)
        assertEquals(0.55 + NoteSynth.STOP_TAIL_SECONDS, NoteSynth.noteLengthSeconds(0.55), 1e-12)
    }

    @Test
    fun renderNoteMatchesEnvelopeSpec() {
        val buffer = NoteSynth.renderNote(69, sampleRate, 0.3)
        val peak = buffer.map { abs(it) }.max()
        // 12 ms attack to 0.22, then decay; lowpass only reduces amplitude slightly at A4
        assertTrue(peak in 0.15..0.23, "peak $peak")

        // attack ramp: first sample ~ 0
        assertTrue(abs(buffer[0].toDouble()) < 0.01)
    }

    @Test
    fun chimeTonesMatchWebConstants() {
        val tones = NoteSynth.chimeTones()
        assertEquals(2, tones.size)
        // A4 then E5 60ms later
        assertEquals(NoteSynth.midiToFrequency(69), tones[0].frequency, 1e-9)
        assertEquals(NoteSynth.midiToFrequency(76), tones[1].frequency, 1e-9)
        assertEquals(0.06, tones[1].startSeconds, 1e-12)
        assertEquals(0.12, tones[0].duration, 1e-12)
        assertEquals(0.08, tones[1].duration, 1e-12)
        assertEquals(0.65, tones[0].peak, 1e-12)
        assertEquals(0.7, tones[1].peak, 1e-12)
    }

    @Test
    fun renderChimeFitsDeclaredLength() {
        val buffer = NoteSynth.renderChime(sampleRate)
        assertEquals((NoteSynth.chimeLengthSeconds() * sampleRate).toInt(), buffer.size)
        assertTrue(buffer.all { it.isFinite() })
    }

    @Test
    fun referencePitchShiftsChime() {
        val tones = NoteSynth.chimeTones(ref = 442.0)
        assertEquals(442.0, tones[0].frequency, 1e-9)
    }

    @Test
    fun midiToFrequencyMatchesPitchUtils() {
        assertEquals(440.0, NoteSynth.midiToFrequency(69), 1e-9)
        assertEquals(82.41, NoteSynth.midiToFrequency(40), 0.005)
    }
}
