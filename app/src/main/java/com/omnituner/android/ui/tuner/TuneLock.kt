package com.omnituner.android.ui.tuner

import com.omnituner.core.audio.PitchTrackingState
import kotlin.math.abs

const val RELEASE_HYSTERESIS_MS = 135L
const val HOLD_HYSTERESIS_CENTS = 1.5

class TuneLock {

    var confirmed = false
        private set

    private var holdStartedAt: Long? = null
    private var outOfRangeSince: Long? = null

    fun reset() {
        confirmed = false
        holdStartedAt = null
        outOfRangeSince = null
    }

    fun step(
        now: Long,
        cents: Double?,
        tracking: PitchTrackingState,
        tolerance: Double,
        holdMs: Long,
    ): Boolean {
        if (tracking == PitchTrackingState.IDLE) {
            reset()
            return false
        }

        val locked = tracking == PitchTrackingState.LOCKED
        val inRange = locked && cents != null && abs(cents) <= tolerance

        if (confirmed) {
            if (inRange) {
                outOfRangeSince = null
            } else {
                val since = outOfRangeSince ?: now.also { outOfRangeSince = it }
                if (now - since >= RELEASE_HYSTERESIS_MS) reset()
            }
            return false
        }

        if (!inRange) {
            val withinHysteresis = locked && holdStartedAt != null && cents != null &&
                abs(cents) <= tolerance + HOLD_HYSTERESIS_CENTS
            if (!withinHysteresis) {
                holdStartedAt = null
                return false
            }
        }

        val start = holdStartedAt ?: now.also { holdStartedAt = it }
        if (now - start < holdMs) return false

        confirmed = true
        holdStartedAt = null
        outOfRangeSince = null
        return true
    }
}
