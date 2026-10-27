package com.omnituner.core.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchTrackerTest {

    private val gate = 0.004

    private fun estimate(
        hz: Double?,
        level: Double = 0.05,
        tracked: Double? = null,
        trackedClarity: Double = 0.0,
    ) = PitchEstimate(
        frequency = hz,
        confidence = if (hz != null) 0.9 else 0.0,
        inputLevel = level,
        clarity = 0.9,
        trackedFrequency = tracked,
        trackedClarity = trackedClarity,
    )

    private fun detect(tracker: PitchTracker, hz: Double, level: Double = 0.05) =
        tracker.update(estimate(hz, level), gate)

    private fun dropout(tracker: PitchTracker, level: Double = 0.0) =
        tracker.update(estimate(null, level), gate)

    private fun lockedOn(hz: Double): PitchTracker {
        val tracker = PitchTracker()
        repeat(3) { detect(tracker, hz) }
        return tracker
    }

    @Test
    fun startsIdleAndLocksAfterThreeConsistentSamples() {
        val tracker = PitchTracker()
        assertEquals(PitchTrackingState.IDLE, tracker.trackingState)

        detect(tracker, 440.0)
        assertEquals(PitchTrackingState.LISTENING, tracker.trackingState)
        assertNotNull(tracker.frequency)

        detect(tracker, 440.0)
        assertEquals(PitchTrackingState.LISTENING, tracker.trackingState)
        assertFalse(tracker.pitched)

        detect(tracker, 440.0)
        assertEquals(PitchTrackingState.LOCKED, tracker.trackingState)
        assertTrue(tracker.pitched)
        assertEquals(440.0, tracker.frequency!!, 0.01)
    }

    @Test
    fun singleOutlierMedianDoesNotMoveEma() {
        val tracker = lockedOn(440.0)
        detect(tracker, 460.0)
        assertEquals(440.0, tracker.frequency!!, 1e-6)
    }

    @Test
    fun adaptiveAlphaTracksFastOnceMedianShifts() {
        val tracker = lockedOn(440.0)
        detect(tracker, 460.0)
        detect(tracker, 460.0)

        val l440 = log2(440.0)
        val l460 = log2(460.0)
        val innovation = abs((l460 - l440) * 1200)
        val alpha = min(1.0, PitchTracker.EMA_ALPHA + innovation / PitchTracker.ADAPTIVE_ALPHA_CENTS)
        val expected = 2.0.pow(alpha * l460 + (1 - alpha) * l440)

        assertEquals(expected, tracker.frequency!!, 0.01)
    }

    @Test
    fun jumpGuardRejectsSingleIncoherentOutlier() {
        val tracker = lockedOn(440.0)
        detect(tracker, 700.0)
        assertEquals(440.0, tracker.frequency!!, 0.01)
        assertEquals(PitchTrackingState.LOCKED, tracker.trackingState)
    }

    @Test
    fun twoCoherentUnrelatedFramesSnapToTheNewNote() {
        val tracker = lockedOn(440.0)
        detect(tracker, 700.0)
        detect(tracker, 700.0)

        assertEquals(700.0, tracker.frequency!!, 1e-6)
        assertEquals(PitchTrackingState.LOCKED, tracker.trackingState)

        detect(tracker, 702.0)
        assertEquals(700.0, tracker.frequency!!, 3.0)
    }

    @Test
    fun harmonicJumpNeedsThreeFramesNotTwo() {
        val tracker = lockedOn(110.0)
        detect(tracker, 220.0)
        detect(tracker, 220.0)
        assertEquals(110.0, tracker.frequency!!, 0.5)

        detect(tracker, 220.0)
        assertEquals(220.0, tracker.frequency!!, 1e-6)
    }

    @Test
    fun subharmonicJumpAlsoNeedsThreeFrames() {
        val tracker = lockedOn(220.0)
        detect(tracker, 110.0)
        detect(tracker, 110.0)
        assertEquals(220.0, tracker.frequency!!, 0.5)

        detect(tracker, 110.0)
        assertEquals(110.0, tracker.frequency!!, 1e-6)
    }

    @Test
    fun returningToMedianClearsPendingJump() {
        val tracker = lockedOn(440.0)
        detect(tracker, 700.0)
        detect(tracker, 450.0)
        detect(tracker, 700.0)
        assertEquals(440.0, tracker.frequency!!, 5.0)
    }

    @Test
    fun silentDropoutsHoldSixFramesThenRelease() {
        val tracker = lockedOn(440.0)

        repeat(PitchTracker.MAX_DROPOUT_HOLD_FRAMES) {
            dropout(tracker, level = 0.0)
            assertEquals(440.0, tracker.frequency!!, 0.01)
            assertEquals(PitchTrackingState.HOLDING, tracker.trackingState)
            assertFalse(tracker.pitched)
        }

        dropout(tracker, level = 0.0)
        assertNull(tracker.frequency)
        assertEquals(PitchTrackingState.LISTENING, tracker.trackingState)
    }

    @Test
    fun audibleDropoutsHoldTwentyTwoFramesThenRelease() {
        val tracker = lockedOn(440.0)

        repeat(PitchTracker.AUDIBLE_HOLD_FRAMES) {
            dropout(tracker, level = 0.01)
            assertNotNull(tracker.frequency)
        }

        dropout(tracker, level = 0.01)
        assertNull(tracker.frequency)
    }

    @Test
    fun audibleDropoutHoldStillEndsEvenIfAudiblePersists() {
        val tracker = lockedOn(440.0)
        repeat(40) { dropout(tracker, level = 0.02) }
        assertNull(tracker.frequency)
    }

    @Test
    fun detectionAfterHoldingLocksAgainAndResetsTheMissedCount() {
        val tracker = lockedOn(440.0)

        repeat(5) { dropout(tracker, level = 0.0) }
        detect(tracker, 441.0)
        assertEquals(PitchTrackingState.LOCKED, tracker.trackingState)
        repeat(5) { dropout(tracker, level = 0.0) }
        assertNotNull(tracker.frequency)
    }

    @Test
    fun dropoutWhileStillListeningStaysListening() {
        val tracker = PitchTracker()
        detect(tracker, 440.0)
        dropout(tracker)
        assertEquals(PitchTrackingState.LISTENING, tracker.trackingState)
    }

    @Test
    fun trackedFrequencyIsOnlyOfferedOnceTheWindowIsFull() {
        val tracker = PitchTracker()
        detect(tracker, 440.0)
        assertNull(tracker.trackedFrequency)
        repeat(2) { detect(tracker, 440.0) }
        assertEquals(440.0, tracker.trackedFrequency!!, 0.01)
    }

    @Test
    fun trackedContinuationCarriesTheNoteWhenTheRawFrameIsRejected() {
        val tracker = lockedOn(110.0)
        tracker.update(estimate(null, tracked = 110.5, trackedClarity = 0.8), gate)

        assertEquals(PitchTrackingState.LOCKED, tracker.trackingState)
        assertEquals(110.0, tracker.frequency!!, 1.0)
    }

    @Test
    fun trackedContinuationBeatsARawSubharmonic() {
        val tracker = lockedOn(110.0)
        // The decaying pluck: the raw pick falls to the octave below, the tracked lag still sees 110.
        repeat(5) {
            tracker.update(estimate(55.0, tracked = 110.0, trackedClarity = 0.8), gate)
        }
        assertEquals(110.0, tracker.frequency!!, 1.0)
        assertEquals(PitchTrackingState.LOCKED, tracker.trackingState)
    }

    @Test
    fun weakTrackedClarityIsIgnored() {
        val tracker = lockedOn(110.0)
        tracker.update(estimate(null, tracked = 110.0, trackedClarity = 0.3), gate)
        assertEquals(PitchTrackingState.HOLDING, tracker.trackingState)
    }

    @Test
    fun trackedContinuationIsIgnoredRightAfterAnOnset() {
        val tracker = PitchTracker()
        repeat(3) { tracker.update(estimate(110.0, level = 0.01), gate) }
        // A fresh pluck: the level jumps well over 3 dB.
        tracker.update(estimate(null, level = 0.08, tracked = 110.0, trackedClarity = 0.8), gate)
        assertEquals(PitchTrackingState.HOLDING, tracker.trackingState)
    }

    @Test
    fun resetTrackingPreservesDisplayedFrequency() {
        val tracker = lockedOn(440.0)
        tracker.resetTracking()
        assertEquals(440.0, tracker.frequency!!, 1e-6)
    }

    @Test
    fun fullResetClearsEverything() {
        val tracker = lockedOn(440.0)
        tracker.reset()
        assertNull(tracker.frequency)
        assertEquals(PitchTrackingState.IDLE, tracker.trackingState)

        dropout(tracker, level = 0.0)
        assertNull(tracker.frequency)
        assertEquals(PitchTrackingState.LISTENING, tracker.trackingState)
    }
}
