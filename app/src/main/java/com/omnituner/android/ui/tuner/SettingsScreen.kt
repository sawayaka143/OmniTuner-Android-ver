package com.omnituner.android.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.omnituner.android.R
import com.omnituner.android.ui.common.RepeatStepperRow
import com.omnituner.android.ui.common.WebSelectOption
import com.omnituner.android.ui.common.WebSelectRow
import com.omnituner.android.ui.common.WebSettingDivider
import com.omnituner.android.ui.common.WebSettingGroup
import com.omnituner.android.ui.common.WebToggleRow
import com.omnituner.android.ui.theme.THEME_DARK
import com.omnituner.android.ui.theme.THEME_LIGHT
import com.omnituner.android.ui.theme.THEME_SYSTEM
import com.omnituner.core.prefs.TUNER_MODE_AUTO
import com.omnituner.core.prefs.TUNER_MODE_MANUAL
import com.omnituner.core.prefs.TUNER_STARTUP_REMEMBER
import kotlin.math.roundToInt

private val IN_TUNE_SWATCHES = listOf(
    "#7ecba8" to "Sage",
    "#4cc38a" to "Green",
    "#58c4dd" to "Sky",
    "#b18cf0" to "Violet",
    "#f2c14e" to "Amber",
    "#e5484d" to "Red",
)
private val OUT_OF_TUNE_SWATCHES = listOf(
    "#ff8aab" to "Pink",
    "#e5484d" to "Red",
    "#f97316" to "Orange",
    "#f2c14e" to "Amber",
    "#a3a3a3" to "Gray",
    "#7ecba8" to "Sage",
)

/** Full-screen settings page (ChatGPT pattern): pushed as a nav route that
 *  slides in from the right; a floating back button overlaps the content and
 *  everything scrolls away under a top fade. Rows are flat and grouped; an
 *  expanded select fades the other groups. */
@Composable
internal fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    state: TunerUiState,
    onBack: () -> Unit,
    viewModel: TunerViewModel,
) {
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSection(label = "Appearance") {
                WebSettingGroup(dimmed = expandedGroup != null && expandedGroup != "appearance") {
                    WebSelectRow(
                        label = "Theme",
                        value = themeOptionLabel(themeMode),
                        options = THEME_OPTIONS.map { (value, label) ->
                            WebSelectOption(value, label)
                        },
                        selected = themeMode,
                        onSelect = onThemeModeChange,
                        onExpandedChange = { open ->
                            expandedGroup = if (open) "appearance" else null
                        },
                    )
                }
            }

            SettingsSection(label = "Tuner") {
                WebSettingGroup(dimmed = expandedGroup != null && expandedGroup != "tuner") {
                    WebSelectRow(
                        label = "Open tuner in",
                        value = startupOptionLabel(state.startupMode),
                        options = STARTUP_OPTIONS.map { (value, label) ->
                            WebSelectOption(value, label)
                        },
                        selected = state.startupMode,
                        onSelect = viewModel::setStartupMode,
                        onExpandedChange = { open ->
                            expandedGroup = if (open) "tuner" else null
                        },
                    )
                    WebSettingDivider()
                    RepeatStepperRow(
                        label = "Reference pitch",
                        valueText = "${state.referencePitch} Hz",
                        onDelta = viewModel::changeReferencePitch,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    WebSettingDivider()
                    RepeatStepperRow(
                        label = "In-tune tolerance",
                        valueText = "±${state.inTune.tolerance} ¢",
                        onDelta = viewModel::changeInTuneTolerance,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    WebSettingDivider()
                    // Hold time: smooth track, snapped to the 50 ms step in code
                    SettingSliderRow(
                        label = "Hold to confirm",
                        valueText = "${state.inTune.holdMs} ms",
                        value = state.inTune.holdMs.toFloat(),
                        valueRange = 0f..1500f,
                        onValueChange = { raw ->
                            viewModel.setInTuneHoldMs(((raw / 50f).roundToInt() * 50).toDouble())
                        },
                    )
                }
            }

            SettingsSection(label = "In-tune feedback") {
                WebSettingGroup(dimmed = expandedGroup != null && expandedGroup != "feedback") {
                    WebToggleRow(
                        label = "Show in-tune feedback",
                        checked = state.inTune.enabled,
                        onCheckedChange = viewModel::setInTuneEnabled,
                    )
                    WebSettingDivider()
                    WebToggleRow(
                        label = "Play chime",
                        checked = state.inTune.sound,
                        onCheckedChange = viewModel::setInTuneSound,
                    )
                    WebSettingDivider()
                    WebToggleRow(
                        label = "Glow pulse",
                        checked = state.inTune.glow,
                        onCheckedChange = viewModel::setInTuneGlow,
                    )
                }
            }

            SettingsSection(label = "Colors") {
                WebSettingGroup(dimmed = expandedGroup != null && expandedGroup != "colors") {
                    ColorSelectRow(
                        label = "In-tune color",
                        swatches = IN_TUNE_SWATCHES,
                        selected = state.inTune.color,
                        onSelect = viewModel::setInTuneColor,
                        onExpandedChange = { open ->
                            expandedGroup = if (open) "colors" else null
                        },
                    )
                    WebSettingDivider()
                    ColorSelectRow(
                        label = "Out-of-tune color",
                        swatches = OUT_OF_TUNE_SWATCHES,
                        selected = state.inTune.outOfTuneColor,
                        onSelect = viewModel::setOutOfTuneColor,
                        onExpandedChange = { open ->
                            expandedGroup = if (open) "colors" else null
                        },
                    )
                }
            }
        }

        // Content scrolling out the top fades away under the floating back button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(background, Color.Transparent),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.tabler_arrow_left),
                contentDescription = "Back",
            )
        }
    }
}

private val THEME_OPTIONS = listOf(
    THEME_SYSTEM to "System",
    THEME_LIGHT to "Light",
    THEME_DARK to "Dark",
)

private val STARTUP_OPTIONS = listOf(
    TUNER_STARTUP_REMEMBER to "Remember",
    TUNER_MODE_AUTO to "Auto",
    TUNER_MODE_MANUAL to "Manual",
)

private fun themeOptionLabel(themeMode: String) =
    THEME_OPTIONS.firstOrNull { it.first == themeMode }?.second ?: themeMode

private fun startupOptionLabel(startupMode: String) =
    STARTUP_OPTIONS.firstOrNull { it.first == startupMode }?.second ?: startupMode

@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
        content()
    }
}

private fun parseHex(hex: String): Color? {
    if (!hex.startsWith("#") || hex.length != 7) return null
    return try {
        Color(hex.toColorInt())
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun ColorSelectRow(
    label: String,
    swatches: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    val selectedEntry = swatches.firstOrNull { it.first.equals(selected, ignoreCase = true) }
    WebSelectRow(
        label = label,
        value = selectedEntry?.second ?: selected,
        valueDotColor = selectedEntry?.let { parseHex(it.first) },
        options = swatches.mapNotNull { (hex, name) ->
            val color = parseHex(hex) ?: return@mapNotNull null
            WebSelectOption(hex, name, dotColor = color)
        },
        selected = selectedEntry?.first,
        onSelect = onSelect,
        onExpandedChange = onExpandedChange,
    )
}
