package com.omnituner.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.omnituner.android.data.AccentColor
import com.omnituner.android.data.ThemeMode

// The whole Material scheme is neutral grey on purpose: stock components render black/white,
// and the accent is reserved for the few places that read LocalAccentColors. Page surfaces are
// pure white / pure black (AMOLED); every grey fill (cards, panels, chips, pills, sheets) is one
// single grey per theme, so the UI never mixes near-identical shades.
private val NeutralDark = darkColorScheme(
    primary = Color(0xFFECECEC),
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF1C1C1C),
    onPrimaryContainer = Color(0xFFECECEC),
    inversePrimary = Color(0xFF3A3A3A),
    secondary = Color(0xFFC4C4C4),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF1C1C1C),
    onSecondaryContainer = Color(0xFFECECEC),
    tertiary = Color(0xFF8FD3A8),
    onTertiary = Color(0xFF0B2615),
    tertiaryContainer = Color(0xFF1F3A29),
    onTertiaryContainer = Color(0xFFB5ECC8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFF868688),
    surfaceTint = Color(0xFF000000),
    inverseSurface = Color(0xFFECECEC),
    inverseOnSurface = Color(0xFF1C1C1C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF6B6B6B),
    outlineVariant = Color(0xFF333333),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383838),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF1C1C1C),
)

private val NeutralLight = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3F3F2),
    onPrimaryContainer = Color(0xFF171717),
    inversePrimary = Color(0xFFD4D4D4),
    secondary = Color(0xFF3D3D3D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3F3F2),
    onSecondaryContainer = Color(0xFF171717),
    tertiary = Color(0xFF1E7A45),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3F0DD),
    onTertiaryContainer = Color(0xFF0B2615),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF3F3F2),
    onSurfaceVariant = Color(0xFF868688),
    surfaceTint = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2B2B2B),
    inverseOnSurface = Color(0xFFF2F2F2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8F8F8F),
    outlineVariant = Color(0xFFDADADA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDDDDD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F2),
    surfaceContainer = Color(0xFFF3F3F2),
    surfaceContainerHigh = Color(0xFFF3F3F2),
    surfaceContainerHighest = Color(0xFFF3F3F2),
)

// Material's default corner sizes, drawn as continuous (squircle) corners. Cards, dialogs, sheets,
// menus, text fields and chips read these; fully rounded buttons take SmoothShape.Full explicitly.
private val SmoothShapes = Shapes(
    extraSmall = SmoothShape(4.dp),
    small = SmoothShape(8.dp),
    medium = SmoothShape(12.dp),
    large = SmoothShape(16.dp),
    extraLarge = SmoothShape(28.dp),
)

@Immutable
data class AccentColors(
    val accent: Color,
    val onAccent: Color,
)

private data class AccentPreset(val dark: Color, val light: Color = dark)

private val accentPresets = mapOf(
    AccentColor.Blue to AccentPreset(Color(0xFF2B67FE)),
    AccentColor.Cyan to AccentPreset(Color(0xFF00B2FF)),
    AccentColor.Green to AccentPreset(Color(0xFF0EB4A0)),
    AccentColor.Lime to AccentPreset(Color(0xFFB2D20C)),
    AccentColor.Yellow to AccentPreset(Color(0xFFFFCC32)),
    AccentColor.Orange to AccentPreset(Color(0xFFFF7F65)),
    AccentColor.Pink to AccentPreset(Color(0xFFFC48B3)),
    // Near-black would vanish on dark surfaces, so "Black" flips to near-white in dark mode.
    AccentColor.Black to AccentPreset(dark = Color(0xFFF2F2F2), light = Color(0xFF0E0E0E)),
)

private val FallbackAccent = AccentColor.Pink

val wallpaperAccentAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun accentColors(accent: AccentColor, dark: Boolean): AccentColors {
    val color = if (accent == AccentColor.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // system_accent1 tones: 300 ≈ tone 70 (reads on dark grey), 600 ≈ tone 40 (reads on white).
        colorResource(if (dark) android.R.color.system_accent1_300 else android.R.color.system_accent1_600)
    } else {
        val preset = accentPresets[accent] ?: accentPresets.getValue(FallbackAccent)
        if (dark) preset.dark else preset.light
    }
    return AccentColors(color, onAccentFor(color))
}

// Black or white, whichever contrasts more (WCAG crossover at relative luminance ≈ 0.179).
private fun onAccentFor(color: Color): Color = if (color.luminance() > 0.179f) Color.Black else Color.White

val LocalAccentColors = staticCompositionLocalOf {
    AccentColors(accent = Color.Unspecified, onAccent = Color.Unspecified)
}

val LocalDarkTheme = staticCompositionLocalOf { false }

// Bottom navigation sits on the page surface; the selection pill is a neutral grey step above it,
// so only the selected tab's icon and label carry the accent.
@Immutable
data class NavBarColors(
    val indicator: Color,
)

private val NavBarLight = NavBarColors(indicator = Color(0xFFF3F3F2))
private val NavBarDark = NavBarColors(indicator = Color(0xFF1C1C1C))

val LocalNavBarColors = staticCompositionLocalOf { NavBarLight }

@Composable
fun OmniTunerTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = if (darkTheme) NeutralDark else NeutralLight

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

    CompositionLocalProvider(
        LocalAccentColors provides accentColors(accent, darkTheme),
        LocalDarkTheme provides darkTheme,
        LocalNavBarColors provides if (darkTheme) NavBarDark else NavBarLight,
    ) {
        MaterialTheme(colorScheme = colorScheme, shapes = SmoothShapes, content = content)
    }
}
