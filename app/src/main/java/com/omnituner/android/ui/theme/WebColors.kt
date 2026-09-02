package com.omnituner.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
    val dangerHover: Color,
    val backdrop: Color,
    val borderSubtle: Color,
    val borderMedium: Color,
    val borderActive: Color,
    val meterTickMinor: Color,
    val meterTickMajor: Color,
    val needleColor: Color,
    val inTuneColor: Color,
    val outOfTuneColor: Color,
    val dialWell: Color,
    val dialFace: Color,
    val dialSheen: Color,
    val dialShadow: Color,
    val chordStageGlow: Color,
    val accentText: Color,
    val inTuneText: Color,
    val fretboardNote: Color,
)

/** Compose equivalent of `color-mix(in srgb, from p%, to)`. */
internal fun colorMix(from: Color, percent: Int, to: Color): Color {
    val t = 1f - percent / 100f
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )
}

/**
 * Web audio-monitor LIGHT_TUNE_INK: blend target when adapting a tune color
 * onto light surfaces (happens to equal the light --text value).
 */
val LightTuneInk = Color(0xFF1A1A18)

/**
 * Web textColorOn: fixed-contrast label ink for arbitrary cell colors
 * (theme-independent by design — dark ink on light cells, light ink on dark).
 */
fun textColorOn(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF121211) else Color(0xFFF5F5F3)

object WebColors {

    private val DARK_ACCENT_TEXT = colorMix(Color(0xFFEDE8D0), 40, Color(0xFFF5F5F3))
    private val LIGHT_ACCENT_TEXT = colorMix(Color(0xFFEDE8D0), 30, Color(0xFF1A1A18))
    private val LIGHT_IN_TUNE_TEXT = colorMix(Color(0xFFEDE8D0), 70, Color(0xFF1A1A18))

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
        dangerHover = Color(0xFFFFA8C0),
        backdrop = Color(0xFF000000).copy(alpha = 0.70f),
        borderSubtle = Color(0xFFFFFFFF).copy(alpha = 0.08f),
        borderMedium = Color(0xFFFFFFFF).copy(alpha = 0.16f),
        borderActive = Color(0xFFFFFFFF).copy(alpha = 0.40f),
        meterTickMinor = Color(0xFFFFFFFF).copy(alpha = 0.15f),
        meterTickMajor = Color(0xFFFFFFFF).copy(alpha = 0.35f),
        needleColor = Color(0xFFF5F5F3), // var(--text)
        inTuneColor = Color(0xFFEDE8D0), // var(--scale-accent)
        outOfTuneColor = Color(0xFFFF8AAB),
        dialWell = Color(0xFF000000),
        dialFace = Color(0xFF1F1F1E), // hardware-knob body raised inside the well
        dialSheen = Color(0xFFFFFFFF), // top rim light on raised dial parts
        dialShadow = Color(0xFF000000), // recess/rim shadows on dial parts
        chordStageGlow = Color(0xFFEDE8D0).copy(alpha = 0.22f),
        accentText = DARK_ACCENT_TEXT,
        inTuneText = Color(0xFFEDE8D0), // var(--in-tune-color)
        fretboardNote = Color(0xFF3B3B3B), // scales fretboard non-root note cells
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
        dangerHover = Color(0xFF8F1138),
        backdrop = Color(0xFF121211).copy(alpha = 0.32f),
        borderSubtle = Color(0xFF000000).copy(alpha = 0.10f),
        borderMedium = Color(0xFF000000).copy(alpha = 0.17f),
        borderActive = Color(0xFF000000).copy(alpha = 0.32f),
        meterTickMinor = Color(0xFF000000).copy(alpha = 0.14f),
        meterTickMajor = Color(0xFF000000).copy(alpha = 0.30f),
        needleColor = Color(0xFF1A1A18), // var(--text)
        inTuneColor = Color(0xFFEDE8D0), // var(--scale-accent)
        outOfTuneColor = Color(0xFFB01645),
        dialWell = Color(0xFFDEDBD4), // var(--surface-container-high)
        dialFace = Color(0xFFFBFAF8), // var(--surface-container-low)
        dialSheen = Color(0xFFFFFFFF),
        dialShadow = Color(0xFF000000),
        chordStageGlow = Color(0xFFEDE8D0).copy(alpha = 0.18f),
        accentText = LIGHT_ACCENT_TEXT,
        inTuneText = LIGHT_IN_TUNE_TEXT,
        fretboardNote = Color(0xFF3B3B3B), // same in both themes
    )
}

val LocalWebPalette = staticCompositionLocalOf { WebColors.Dark }

@Composable
fun currentWebPalette(): WebPalette = LocalWebPalette.current

/**
 * Chords neck diagram degree colors (web neck-diagram tokens), keyed by
 * semitone distance from the chord root:
 * 0 -> root (accent-text), 3/4 -> third (good), 5/7 -> fifth (muted),
 * 6/8/10/11 -> altered (warn), everything else (1/2/9) -> other (info).
 */
fun WebPalette.neckDegreeColor(semitonesFromRoot: Int): Color {
    val d = ((semitonesFromRoot % 12) + 12) % 12
    return when (d) {
        0 -> accentText
        3, 4 -> good
        5, 7 -> muted
        6, 8, 10, 11 -> warn
        else -> info
    }
}

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
