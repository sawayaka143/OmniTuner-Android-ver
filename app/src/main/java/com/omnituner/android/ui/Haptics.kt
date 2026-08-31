package com.omnituner.android.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Haptics: light tap for toggles, success waveform for in-tune confirmation. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun light() {
        vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun success() {
        vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 45, 70, 45),
                intArrayOf(0, 180, 0, 220),
                -1,
            ),
        )
    }

    private fun vibrate(effect: VibrationEffect) {
        try {
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }
}
