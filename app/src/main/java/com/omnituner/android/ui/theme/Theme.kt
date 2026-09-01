package com.omnituner.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun darkScheme(p: WebPalette) = darkColorScheme(
    primary = p.scaleAccent,
    onPrimary = p.scaleAccentInk,
    primaryContainer = p.scaleAccent,
    onPrimaryContainer = p.scaleAccentInk,
    secondary = p.info,
    onSecondary = p.scaleAccentInk,
    secondaryContainer = p.surfaceHigh,
    onSecondaryContainer = p.text,
    tertiary = p.good,
    onTertiary = p.scaleAccentInk,
    background = p.canvas,
    onBackground = p.text,
    surface = p.canvas,
    onSurface = p.text,
    surfaceVariant = p.surface,
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.canvas,
    surfaceContainerLow = p.surfaceLow,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surfaceHigh,
    outline = p.borderMedium,
    outlineVariant = p.borderSubtle,
    error = p.danger,
    onError = p.scaleAccentInk,
    errorContainer = p.surfaceHigh,
    onErrorContainer = p.danger,
    scrim = p.backdrop,
)

private fun lightScheme(p: WebPalette) = lightColorScheme(
    primary = p.scaleAccent,
    onPrimary = p.scaleAccentInk,
    primaryContainer = p.scaleAccent,
    onPrimaryContainer = p.scaleAccentInk,
    secondary = p.info,
    onSecondary = Color.White,
    secondaryContainer = p.surfaceHigh,
    onSecondaryContainer = p.text,
    tertiary = p.good,
    onTertiary = p.scaleAccentInk,
    background = p.canvas,
    onBackground = p.text,
    surface = p.canvas,
    onSurface = p.text,
    surfaceVariant = p.surface,
    onSurfaceVariant = p.muted,
    surfaceContainerLowest = p.canvas,
    surfaceContainerLow = p.surfaceLow,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surfaceHigh,
    outline = p.borderMedium,
    outlineVariant = p.borderSubtle,
    error = p.danger,
    onError = Color.White,
    errorContainer = p.surfaceHigh,
    onErrorContainer = p.danger,
    scrim = p.backdrop,
)

@Composable
fun OmniTunerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) WebColors.Dark else WebColors.Light
    val colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalWebPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
