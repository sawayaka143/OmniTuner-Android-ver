package com.omnituner.android.ui.theme

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every Switch and Slider in the app is an accent control; pass these instead of the defaults.

@Composable
fun accentSwitchColors(): SwitchColors {
    val accent = LocalAccentColors.current
    return SwitchDefaults.colors(
        checkedTrackColor = accent.accent,
        // White on every accent and in both themes, like iOS; Yellow/Lime trade some contrast for it.
        checkedThumbColor = Color.White,
        checkedBorderColor = accent.accent,
        checkedIconColor = accent.accent,
    )
}

// The inactive track is a tint of the accent rather than the theme grey: in dark the grey equals
// the card color, which made the unfilled part of the track disappear.
@Composable
fun accentSliderColors(): SliderColors {
    val accent = LocalAccentColors.current
    return SliderDefaults.colors(
        thumbColor = accent.accent,
        activeTrackColor = accent.accent,
        activeTickColor = accent.onAccent,
        inactiveTrackColor = accent.accent.copy(alpha = 0.24f),
        inactiveTickColor = accent.accent,
    )
}
