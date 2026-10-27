package com.omnituner.android.ui.tuner

import com.omnituner.core.audio.PitchTrackingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneLockTest {

    @Test
    fun confirmsOnlyAfterTheHoldWindowInRange() {
        val lock = TuneLock()
        assertFalse(lock.step(0, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
        assertFalse(lock.step(499, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
        assertTrue(lock.step(500, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
        assertTrue(lock.confirmed)
    }

    @Test
    fun confirmedStateIsNotReportedTwice() {
        val lock = TuneLock()
        lock.step(0, 0.0, PitchTrackingState.LOCKED, 5.0, 0)
        assertFalse(lock.step(10, 0.0, PitchTrackingState.LOCKED, 5.0, 0))
        assertTrue(lock.confirmed)
    }

    @Test
    fun leavingRangeRestartsTheHold() {
        val lock = TuneLock()
        lock.step(0, 1.0, PitchTrackingState.LOCKED, 5.0, 500)
        lock.step(200, 30.0, PitchTrackingState.LOCKED, 5.0, 500)
        assertFalse(lock.step(600, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
        assertTrue(lock.step(1100, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
    }

    @Test
    fun smallWanderInsideHysteresisKeepsTheHold() {
        val lock = TuneLock()
        lock.step(0, 1.0, PitchTrackingState.LOCKED, 5.0, 500)
        lock.step(200, 6.0, PitchTrackingState.LOCKED, 5.0, 500)
        assertTrue(lock.step(500, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
    }

    @Test
    fun confirmedHoldSurvivesShortDropouts() {
        val lock = TuneLock()
        lock.step(0, 0.0, PitchTrackingState.LOCKED, 5.0, 0)
        lock.step(100, 30.0, PitchTrackingState.LOCKED, 5.0, 0)
        lock.step(200, 0.0, PitchTrackingState.LOCKED, 5.0, 0)
        assertTrue(lock.confirmed)
    }

    @Test
    fun confirmedHoldReleasesAfterHysteresis() {
        val lock = TuneLock()
        lock.step(0, 0.0, PitchTrackingState.LOCKED, 5.0, 0)
        lock.step(100, 30.0, PitchTrackingState.LOCKED, 5.0, 0)
        lock.step(100 + RELEASE_HYSTERESIS_MS, 30.0, PitchTrackingState.LOCKED, 5.0, 0)
        assertFalse(lock.confirmed)
    }

    @Test
    fun idleClearsEverything() {
        val lock = TuneLock()
        lock.step(0, 0.0, PitchTrackingState.LOCKED, 5.0, 0)
        assertTrue(lock.confirmed)
        lock.step(10, null, PitchTrackingState.IDLE, 5.0, 0)
        assertFalse(lock.confirmed)
    }

    @Test
    fun notLockedNeverConfirms() {
        val lock = TuneLock()
        assertFalse(lock.step(0, 0.0, PitchTrackingState.LISTENING, 5.0, 0))
        assertFalse(lock.step(1000, 0.0, PitchTrackingState.LISTENING, 5.0, 0))
        assertFalse(lock.confirmed)
    }

    @Test
    fun holdingKeepsAConfirmedLockInsteadOfReleasingIt() {
        val lock = TuneLock()
        lock.step(0, 1.0, PitchTrackingState.LOCKED, 5.0, 0)
        assertTrue(lock.confirmed)
        lock.step(100, 1.0, PitchTrackingState.HOLDING, 5.0, 0)
        lock.step(100 + RELEASE_HYSTERESIS_MS + 50, 1.0, PitchTrackingState.HOLDING, 5.0, 0)
        assertTrue(lock.confirmed)
    }

    @Test
    fun holdingRestartsAPendingHold() {
        val lock = TuneLock()
        lock.step(0, 1.0, PitchTrackingState.LOCKED, 5.0, 500)
        lock.step(300, 1.0, PitchTrackingState.HOLDING, 5.0, 500)
        assertFalse(lock.step(600, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
        assertTrue(lock.step(1100, 1.0, PitchTrackingState.LOCKED, 5.0, 500))
    }
}
