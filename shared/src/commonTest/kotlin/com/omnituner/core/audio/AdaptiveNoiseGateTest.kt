package com.omnituner.core.audio

import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveNoiseGateTest {

    private fun db(gate: Double) = 20 * log10(gate)

    @Test
    fun startsAtTheFixedDetectorGate() {
        assertEquals(PitchDetector.SILENCE_RMS, AdaptiveNoiseGate().gate, 1e-12)
    }

    @Test
    fun quietRoomLowersTheGateToFloorPlusMargin() {
        val gate = AdaptiveNoiseGate()
        repeat(5) { gate.update(0.0005, pitched = false) } // -66 dBFS
        assertEquals(-66.02 + 6.0, db(gate.gate), 0.05)
    }

    @Test
    fun gateNeverGoesBelowMinimum() {
        val gate = AdaptiveNoiseGate()
        repeat(50) { gate.update(0.00001, pitched = false) } // -100 dBFS
        assertEquals(AdaptiveNoiseGate.MIN_GATE_DB, db(gate.gate), 1e-6)
    }

    @Test
    fun gateNeverRisesAboveTheFixedDetectorGate() {
        val gate = AdaptiveNoiseGate()
        repeat(400) { gate.update(0.05, pitched = false) } // loud room
        assertEquals(PitchDetector.SILENCE_RMS, gate.gate, 1e-6)
    }

    @Test
    fun floorRisesSlowlyWhileNotPitched() {
        val gate = AdaptiveNoiseGate()
        gate.update(0.0005, pitched = false)
        val before = db(gate.gate)
        gate.update(0.002, pitched = false)
        assertEquals(before + AdaptiveNoiseGate.FLOOR_RISE_DB_PER_FRAME, db(gate.gate), 1e-6)
    }

    @Test
    fun floorIsFrozenWhilePitched() {
        val gate = AdaptiveNoiseGate()
        gate.update(0.0005, pitched = false)
        val before = gate.gate
        repeat(100) { gate.update(0.05, pitched = true) }
        assertEquals(before, gate.gate, 1e-12)
    }

    @Test
    fun floorFallsFastWhenTheRoomGetsQuieter() {
        val gate = AdaptiveNoiseGate()
        gate.update(0.002, pitched = false)
        val before = db(gate.gate)
        gate.update(0.0002, pitched = false)
        assertTrue(before - db(gate.gate) > 5.0)
    }

    @Test
    fun resetReturnsToTheInitialGate() {
        val gate = AdaptiveNoiseGate()
        repeat(5) { gate.update(0.0005, pitched = false) }
        gate.reset()
        assertEquals(PitchDetector.SILENCE_RMS, gate.gate, 1e-12)
    }
}
