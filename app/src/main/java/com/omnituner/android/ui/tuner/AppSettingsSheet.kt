package com.omnituner.android.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.omnituner.android.ui.common.RepeatStepperRow
import com.omnituner.android.ui.theme.THEME_DARK
import com.omnituner.android.ui.theme.THEME_LIGHT
import com.omnituner.android.ui.theme.THEME_SYSTEM
import com.omnituner.core.prefs.TUNER_MODE_AUTO
import com.omnituner.core.prefs.TUNER_MODE_MANUAL
import com.omnituner.core.prefs.TUNER_STARTUP_REMEMBER
import kotlin.math.roundToInt

private val IN_TUNE_SWATCHES = listOf(
    "#7ecba8", "#4cc38a", "#58c4dd", "#b18cf0", "#f2c14e", "#e5484d",
)
private val OUT_OF_TUNE_SWATCHES = listOf(
    "#ff8aab", "#e5484d", "#f97316", "#f2c14e", "#a3a3a3", "#7ecba8",
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
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            val themeOptions = listOf(
                THEME_SYSTEM to "System",
                THEME_LIGHT to "Light",
                THEME_DARK to "Dark",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = themeMode == value,
                        onClick = { onThemeModeChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = themeOptions.size,
                        ),
                    ) { Text(label) }
                }
            }

            HorizontalDivider()

            // Tuner
            Text("Tuner", style = MaterialTheme.typography.labelLarge)

            // Startup mode
            Text("Open tuner in", style = MaterialTheme.typography.labelLarge)
            val startupOptions = listOf(
                TUNER_STARTUP_REMEMBER to "Remember",
                TUNER_MODE_AUTO to "Auto",
                TUNER_MODE_MANUAL to "Manual",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                startupOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.startupMode == value,
                        onClick = { viewModel.setStartupMode(value) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = startupOptions.size,
                        ),
                    ) { Text(label) }
                }
            }

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
            SettingSwitchRow(
                label = "Show in-tune feedback",
                checked = state.inTune.enabled,
                onCheckedChange = viewModel::setInTuneEnabled,
            )
            SettingSwitchRow(
                label = "Play chime",
                checked = state.inTune.sound,
                onCheckedChange = viewModel::setInTuneSound,
            )
            SettingSwitchRow(
                label = "Glow pulse",
                checked = state.inTune.glow,
                onCheckedChange = viewModel::setInTuneGlow,
            )

            HorizontalDivider()

            // Colors
            ColorSwatchRow(
                label = "In-tune color",
                swatches = IN_TUNE_SWATCHES,
                selected = state.inTune.color,
                onSelect = viewModel::setInTuneColor,
            )
            ColorSwatchRow(
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
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColorSwatchRow(
    label: String,
    swatches: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (hex in swatches) {
                val color = parseHex(hex) ?: continue
                val isSelected = hex.equals(selected, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(hex) }
                        .semantics {
                            contentDescription = "$label ${hex}${if (isSelected) ", selected" else ""}"
                        },
                )
            }
        }
    }
}
