package com.omnituner.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens ported 1:1 from the web reference (tests/styles/styles.scss):
 * dark values from :root, light values from html[data-theme='light'].
 */
data class WebPalette(
    val canvas: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val scaleAccent: Color,
    val scaleAccentInk: Color,
    val good: Color,
    val warn: Color,
    val info: Color,
    val danger: Color,
    val borderSubtle: Color,
    val borderMedium: Color,
    val borderActive: Color,
)

object WebColors {

    // :root (dark)
    val Dark = WebPalette(
        canvas = Color(0xFF121211),
        surfaceLow = Color(0xFF181817),
        surface = Color(0xFF222220),
        surfaceHigh = Color(0xFF2C2C29),
        text = Color(0xFFF5F5F3),
        muted = Color(0xFF9A9A94),
        dim = Color(0xFF94948E),
        scaleAccent = Color(0xFFEDE8D0),
        scaleAccentInk = Color(0xFF121211),
        good = Color(0xFF7ECBA8),
        warn = Color(0xFFC07A5E),
        info = Color(0xFF9A8FB8),
        danger = Color(0xFFFF8AAB),
        borderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.08f),
        borderMedium = Color(0xFFFFFFFF).copy(alpha = 0.16f),
        borderActive = Color(0xFFFFFFFF).copy(alpha = 0.40f),
    )

    // html[data-theme='light']
    val Light = WebPalette(
        canvas = Color(0xFFF1F0EC),
        surfaceLow = Color(0xFFFBFAF8),
        surface = Color(0xFFE9E7E2),
        surfaceHigh = Color(0xFFDEDBD4),
        text = Color(0xFF1A1A18),
        muted = Color(0xFF54544F),
        dim = Color(0xFF5F5F5B),
        // --scale-accent is not overridden in the light block
        scaleAccent = Color(0xFFEDE8D0),
        scaleAccentInk = Color(0xFF121211),
        good = Color(0xFF11603C),
        warn = Color(0xFF8A4D00),
        info = Color(0xFF544A8F),
        danger = Color(0xFFB01645),
        borderSubtle = Color(0xFF000000).copy(alpha = 0.10f),
        borderMedium = Color(0xFF000000).copy(alpha = 0.17f),
        borderActive = Color(0xFF000000).copy(alpha = 0.32f),
    )
}

val LocalWebPalette = staticCompositionLocalOf { WebColors.Dark }

@Composable
fun currentWebPalette(): WebPalette = LocalWebPalette.current

/** Web quality colors (--good/--warn/--info/--danger), theme-aware. */
@Composable
fun webQualityColor(kind: String): Color {
    val palette = currentWebPalette()
    return when (kind) {
        "good" -> palette.good
        "warn" -> palette.warn
        "info" -> palette.info
        else -> palette.danger
    }
}
