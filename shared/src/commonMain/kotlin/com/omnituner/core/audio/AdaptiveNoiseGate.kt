package com.omnituner.core.audio

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow

/**
 * Silence gate that follows the room's noise floor: it sits a few dB above the floor, never
 * looser than the detector's fixed RMS gate and never tighter than -70 dBFS. The floor falls
 * fast, rises slowly, and freezes while a pitch is being tracked so the note itself is never
 * learned as noise.
 */
class AdaptiveNoiseGate {

    private var floorDb: Double? = null

    var gate: Double = PitchDetector.SILENCE_RMS
        private set

    fun reset() {
        floorDb = null
        gate = PitchDetector.SILENCE_RMS
    }

    /** Feeds one frame's RMS level and whether the tracker was pitched; returns the new gate. */
    fun update(rms: Double, pitched: Boolean): Double {
        val levelDb = if (rms > 0) max(MIN_LEVEL_DB, 20 * log10(rms)) else MIN_LEVEL_DB
        val floor = floorDb
        val next = when {
            floor == null -> levelDb
            levelDb < floor -> floor + FLOOR_FALL_RATE * (levelDb - floor)
            !pitched -> min(levelDb, floor + FLOOR_RISE_DB_PER_FRAME)
            else -> floor
        }
        floorDb = next
        gate = 10.0.pow((next + GATE_MARGIN_DB).coerceIn(MIN_GATE_DB, MAX_GATE_DB) / 20)
        return gate
    }

    companion object {
        const val MIN_GATE_DB = -70.0
        const val MAX_GATE_DB = -47.96
        const val GATE_MARGIN_DB = 6.0
        const val FLOOR_RISE_DB_PER_FRAME = 0.07
        const val FLOOR_FALL_RATE = 0.3
        private const val MIN_LEVEL_DB = -120.0
    }
}
