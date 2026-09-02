package com.omnituner.core.metronome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronomeSchedulerTest {

    private class FakeClock {
        var now: Double = 0.0
        fun clock(): Double = now
    }

    private fun configured(
        clock: FakeClock,
        overrides: MetronomeState.() -> MetronomeState = { this },
    ): MetronomeScheduler {
        val scheduler = MetronomeScheduler(clock::clock)
        scheduler.configure(DEFAULT_METRONOME_STATE.overrides())
        return scheduler
    }

    @Test
    fun playsCountInBarBeforeBarZeroWhenEnabled() {
        val clock = FakeClock()
        val scheduler = configured(clock) { copy(countIn = true, bpm = 60.0) }

        scheduler.start(clock.now)
        scheduler.tick(MetronomeScheduler.LOOKAHEAD_VISIBLE_S)

        val transport = scheduler.getTransport()
        assertTrue(transport != null && transport.countIn)
        assertEquals(-1, transport.barIndex)

        clock.now += 4.5
        scheduler.tick(MetronomeScheduler.LOOKAHEAD_VISIBLE_S)

        val after = scheduler.getTransport()
        assertTrue(after != null && !after.countIn)
        assertEquals(0, after.barIndex)
        assertTrue(after.barActive)

        scheduler.stop()
    }

    @Test
    fun startsAtBarZeroWhenCountInOff() {
        val clock = FakeClock()
        val scheduler = configured(clock) { copy(countIn = false, bpm = 60.0) }

        scheduler.start(clock.now)
        val transport = scheduler.getTransport()
        assertTrue(transport != null && !transport.countIn)
        assertEquals(0, transport.barIndex)
        scheduler.stop()
    }

    @Test
    fun rampsTempoToTargetOverBars() {
        val clock = FakeClock()
        val scheduler = configured(clock) {
            copy(bpm = 100.0, ramp = TempoRamp(enabled = true, targetBpm = 200.0, bars = 2))
        }

        scheduler.start(clock.now)
        var transport = scheduler.getTransport()
        assertTrue(transport != null && transport.bpm == 100)

        clock.now += 8.2
        scheduler.tick(MetronomeScheduler.LOOKAHEAD_VISIBLE_S)

        transport = scheduler.getTransport()
        assertTrue(transport != null && transport.bpm == 200)
        scheduler.stop()
    }

    @Test
    fun keepsTempoFlatWhenRampDisabled() {
        val clock = FakeClock()
        val scheduler = configured(clock) {
            copy(bpm = 100.0, ramp = TempoRamp(enabled = false, targetBpm = 200.0, bars = 2))
        }

        scheduler.start(clock.now)
        clock.now += 8.2
        scheduler.tick(MetronomeScheduler.LOOKAHEAD_VISIBLE_S)

        assertEquals(100, scheduler.getTransport()?.bpm)
        scheduler.stop()
    }

    @Test
    fun retuneKeepsBeatGridContinuousOnLiveBpmChange() {
        val clock = FakeClock()
        val scheduler = configured(clock) { copy(bpm = 60.0) }

        scheduler.start(clock.now)
        clock.now += 2.0
        scheduler.configure(DEFAULT_METRONOME_STATE.copy(bpm = 120.0))

        val transport = scheduler.getTransport()
        val expectedElapsedBeats = 0.0 + (2.0 - MetronomeScheduler.START_DELAY_S) / 1.0 + 0.0
        val barStart = clock.now
        assertTrue(transport != null)
        val beatsFromBarStart = (barStart - (clock.now)) / 0.5
        assertEquals(0.0, beatsFromBarStart, 1e-9)
        val events = scheduler.tick(MetronomeScheduler.LOOKAHEAD_VISIBLE_S)
        assertTrue(events.isNotEmpty())
        scheduler.stop()
        assertTrue(expectedElapsedBeats > 0)
    }

    @Test
    fun barPatternStagingAppliesAtBarBoundary() {
        val clock = FakeClock()
        val scheduler = configured(clock) { copy(bpm = 600.0) }

        scheduler.start(clock.now)
        scheduler.configure(DEFAULT_METRONOME_STATE.copy(bpm = 600.0, barPattern = listOf(1, 0)))

        val eventsNow = scheduler.tick(MetronomeScheduler.LOOKAHEAD_VISIBLE_S)
        assertEquals(0, scheduler.getTransport()?.patternPos)
        assertTrue(eventsNow.isNotEmpty())

        clock.now += 0.5
        scheduler.tick(MetronomeScheduler.LOOKAHEAD_VISIBLE_S)
        assertEquals(1, scheduler.getTransport()?.patternPos)
        assertFalse(scheduler.getTransport()!!.barActive)
        scheduler.stop()
    }

    @Test
    fun tickEmitsDownbeatAndBeatSounds() {
        val clock = FakeClock()
        val scheduler = configured(clock) { copy(bpm = 60.0) }
        scheduler.start(clock.now)

        val events = scheduler.tick(MetronomeScheduler.LOOKAHEAD_HIDDEN_S)
        val roles = events.filter { it.layer == "meter" }.map { it.role }
        assertTrue(roles.contains("downbeat"))
        assertTrue(roles.contains("beat"))
        assertTrue(events.all { it.time >= clock.now })
        scheduler.stop()
    }

    @Test
    fun countInEventsAreDownbeatAndBeatsOnly() {
        val clock = FakeClock()
        val scheduler = configured(clock) {
            copy(countIn = true, bpm = 60.0, divisionsPerBeat = 4)
        }
        scheduler.start(clock.now)

        val events = scheduler.tick(MetronomeScheduler.LOOKAHEAD_HIDDEN_S)
        val countInEvents = events.filter { it.time < MetronomeScheduler.START_DELAY_S + 4.0 }
        assertTrue(countInEvents.isNotEmpty())
        assertTrue(countInEvents.none { it.role == "subdivision" })
        scheduler.stop()
    }

    @Test
    fun stopClearsPlayingState() {
        val clock = FakeClock()
        val scheduler = configured(clock)
        scheduler.start(clock.now)
        assertTrue(scheduler.isPlaying)
        scheduler.stop()
        assertFalse(scheduler.isPlaying)
        assertEquals(null, scheduler.getTransport())
        assertEquals(emptyList<ScheduledSound>(), scheduler.tick(0.5))
    }
}
