package com.omnituner.core.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchSmootherTest {

    private fun detect(smoother: PitchSmoother, hz: Double) =
        smoother.onAnalysisResult(hz, confidence = 0.9, inputLevel = 0.05)

    private fun dropout(smoother: PitchSmoother, inputLevel: Double = 0.0) =
        smoother.onAnalysisResult(null, confidence = 0.0, inputLevel = inputLevel)

    @Test
    fun startsIdleAndLocksAfterThreeConsistentSamples() {
        val smoother = PitchSmoother()
        assertEquals(PitchTrackingState.IDLE, smoother.trackingState)

        detect(smoother, 440.0)
        assertEquals(PitchTrackingState.LISTENING, smoother.trackingState)
        assertNotNull(smoother.frequency)

        detect(smoother, 440.0)
        assertEquals(PitchTrackingState.LISTENING, smoother.trackingState)

        detect(smoother, 440.0)
        assertEquals(PitchTrackingState.LOCKED, smoother.trackingState)
        assertEquals(440.0, smoother.frequency!!, 0.01)
    }

    @Test
    fun singleOutlierMedianDoesNotMoveEma() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        detect(smoother, 460.0)
        assertEquals(440.0, smoother.frequency!!, 1e-6)
    }

    @Test
    fun adaptiveAlphaTracksFastOnceMedianShifts() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        detect(smoother, 460.0)
        detect(smoother, 460.0)

        val l440 = log2(440.0)
        val l460 = log2(460.0)
        val innovation = abs((l460 - l440) * 1200)
        val alpha = min(1.0, PitchSmoother.EMA_ALPHA + innovation / PitchSmoother.ADAPTIVE_ALPHA_CENTS)
        val expected = 2.0.pow(alpha * l460 + (1 - alpha) * l440)

        assertEquals(expected, smoother.frequency!!, 0.01)
        assertTrue(abs(smoother.frequency!! - 460.0) < 5.0)
    }

    @Test
    fun jumpGuardRejectsSingleIncoherentOutlier() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        detect(smoother, 700.0)
        assertEquals(440.0, smoother.frequency!!, 0.01)
        assertEquals(PitchTrackingState.LOCKED, smoother.trackingState)
    }

    @Test
    fun twoConsecutiveCoherentOutliersSnapReseed() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        detect(smoother, 700.0)
        detect(smoother, 700.0)

        assertEquals(700.0, smoother.frequency!!, 1e-6)
        assertEquals(PitchTrackingState.LOCKED, smoother.trackingState)

        detect(smoother, 702.0)
        assertEquals(700.0, smoother.frequency!!, 3.0)
    }

    @Test
    fun returningToMedianClearsPendingJump() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        detect(smoother, 700.0)
        detect(smoother, 450.0)

        assertEquals(440.0, smoother.frequency!!, 3.0)
    }

    @Test
    fun silentDropoutsHoldSixFramesThenRelease() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        repeat(6) {
            dropout(smoother, inputLevel = 0.0)
            assertEquals(440.0, smoother.frequency!!, 0.01)
        }

        dropout(smoother, inputLevel = 0.0)
        assertNull(smoother.frequency)
        assertEquals(PitchTrackingState.LISTENING, smoother.trackingState)
    }

    @Test
    fun audibleDropoutsHoldSixtyFramesThenRelease() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        repeat(PitchSmoother.AUDIBLE_HOLD_FRAMES) {
            dropout(smoother, inputLevel = 0.01)
            assertNotNull(smoother.frequency)
        }

        dropout(smoother, inputLevel = 0.01)
        assertNull(smoother.frequency)
    }

    @Test
    fun audibleDropoutHoldStillEndsEvenIfAudiblePersists() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        repeat(70) { dropout(smoother, inputLevel = 0.02) }
        assertNull(smoother.frequency)
    }

    @Test
    fun detectionResetsMissedFrameCount() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        repeat(5) { dropout(smoother, inputLevel = 0.0) }
        detect(smoother, 441.0)
        repeat(5) { dropout(smoother, inputLevel = 0.0) }
        assertNotNull(smoother.frequency)
    }

    @Test
    fun resetTrackingPreservesDisplayedFrequency() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        smoother.resetTracking()
        assertEquals(440.0, smoother.frequency!!, 1e-6)
    }

    @Test
    fun fullResetClearsEverything() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        smoother.reset()
        assertNull(smoother.frequency)
        assertEquals(PitchTrackingState.IDLE, smoother.trackingState)

        dropout(smoother, inputLevel = 0.0)
        assertNull(smoother.frequency)
        assertEquals(PitchTrackingState.LISTENING, smoother.trackingState)
    }

    @Test
    fun invalidFrequencyFallsBackToEma() {
        val smoother = PitchSmoother()
        repeat(3) { detect(smoother, 440.0) }

        detect(smoother, Double.NaN)
        assertEquals(440.0, smoother.frequency!!, 0.01)
    }
}
