package com.omnituner.android.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.omnituner.android.ui.common.RepeatStepperRow
import com.omnituner.android.ui.common.WebSelectOption
import com.omnituner.android.ui.common.WebSelectRow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSettingsSheet(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    state: TunerUiState,
    onDismiss: () -> Unit,
    viewModel: TunerViewModel,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
            )

            // Appearance
            Text("Appearance", style = MaterialTheme.typography.labelLarge)
            val themeOptions = listOf(
                THEME_SYSTEM to "System",
                THEME_LIGHT to "Light",
                THEME_DARK to "Dark",
            )
            WebSelectRow(
                label = "Theme",
                value = themeOptions.firstOrNull { it.first == themeMode }?.second ?: themeMode,
                options = themeOptions.map { (value, label) ->
                    WebSelectOption(value, label)
                },
                selected = themeMode,
                onSelect = onThemeModeChange,
            )

            HorizontalDivider()

            // Tuner
            Text("Tuner", style = MaterialTheme.typography.labelLarge)

            // Startup mode
            val startupOptions = listOf(
                TUNER_STARTUP_REMEMBER to "Remember",
                TUNER_MODE_AUTO to "Auto",
                TUNER_MODE_MANUAL to "Manual",
            )
            WebSelectRow(
                label = "Open tuner in",
                value = startupOptions.firstOrNull { it.first == state.startupMode }?.second
                    ?: state.startupMode,
                options = startupOptions.map { (value, label) ->
                    WebSelectOption(value, label)
                },
                selected = state.startupMode,
                onSelect = viewModel::setStartupMode,
            )

            // Reference pitch
            RepeatStepperRow(
                label = "Reference pitch",
                valueText = "${state.referencePitch} Hz",
                onDelta = viewModel::changeReferencePitch,
            )

            // Tolerance
            RepeatStepperRow(
                label = "In-tune tolerance",
                valueText = "±${state.inTune.tolerance} ¢",
                onDelta = viewModel::changeInTuneTolerance,
            )

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

            HorizontalDivider()

            // In-tune feedback
            Text("In-tune feedback", style = MaterialTheme.typography.labelLarge)
            WebToggleRow(
                label = "Show in-tune feedback",
                checked = state.inTune.enabled,
                onCheckedChange = viewModel::setInTuneEnabled,
            )
            WebToggleRow(
                label = "Play chime",
                checked = state.inTune.sound,
                onCheckedChange = viewModel::setInTuneSound,
            )
            WebToggleRow(
                label = "Glow pulse",
                checked = state.inTune.glow,
                onCheckedChange = viewModel::setInTuneGlow,
            )

            HorizontalDivider()

            // Colors
            ColorSelectRow(
                label = "In-tune color",
                swatches = IN_TUNE_SWATCHES,
                selected = state.inTune.color,
                onSelect = viewModel::setInTuneColor,
            )
            ColorSelectRow(
                label = "Out-of-tune color",
                swatches = OUT_OF_TUNE_SWATCHES,
                selected = state.inTune.outOfTuneColor,
                onSelect = viewModel::setOutOfTuneColor,
            )

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Done")
            }
        }
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
    Column {
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
) {
    WebSelectRow(
        label = label,
        value = swatches.firstOrNull { it.first.equals(selected, ignoreCase = true) }?.second
            ?: selected,
        options = swatches.mapNotNull { (hex, name) ->
            val color = parseHex(hex) ?: return@mapNotNull null
            WebSelectOption(hex, name, dotColor = color)
        },
        selected = swatches.firstOrNull { it.first.equals(selected, ignoreCase = true) }?.first,
        onSelect = onSelect,
    )
}
