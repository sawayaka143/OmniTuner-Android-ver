package com.omnituner.android.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.AccentColor
import com.omnituner.android.data.HOLD_MS_RANGE
import com.omnituner.android.data.HOLD_MS_STEP
import com.omnituner.android.data.StartupMode
import com.omnituner.android.data.ThemeMode
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PickerRow
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.android.ui.theme.LocalDarkTheme
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.android.ui.theme.accentColors
import com.omnituner.android.ui.theme.wallpaperAccentAvailable
import kotlin.math.roundToInt

private val themeChoices = listOf(
    ThemeMode.System to "System",
    ThemeMode.Light to "Light",
    ThemeMode.Dark to "Dark",
)

private val startupChoices = listOf(
    StartupMode.Remember to "Remember",
    StartupMode.Auto to "Auto",
    StartupMode.Manual to "Manual",
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val appearance by viewModel.appearance.collectAsState()
    val tuner by viewModel.tuner.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SettingsSection(title = "Appearance") {
            ChoiceRow(
                headline = "Theme",
                selected = appearance.themeMode,
                choices = themeChoices,
                onSelect = viewModel::setTheme,
            )
            AccentRow(selected = appearance.accent, onSelect = viewModel::setAccent)
        }

        SettingsSection(title = "Tuner") {
            ChoiceRow(
                headline = "Open tuner in",
                selected = tuner.startupMode,
                choices = startupChoices,
                onSelect = viewModel::setStartupMode,
            )
            NumberStepper(
                headline = "Reference pitch",
                valueText = "${tuner.referencePitch} Hz",
                onDecrement = { viewModel.changeReferencePitch(-1) },
                onIncrement = { viewModel.changeReferencePitch(1) },
            )
            NumberStepper(
                headline = "In-tune tolerance",
                valueText = "±${tuner.tolerance} ¢",
                onDecrement = { viewModel.changeTolerance(-1) },
                onIncrement = { viewModel.changeTolerance(1) },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Hold to confirm") },
                supportingContent = {
                    Slider(
                        value = tuner.holdMs.toFloat(),
                        onValueChange = { raw ->
                            viewModel.setHoldMs((raw / HOLD_MS_STEP).roundToInt() * HOLD_MS_STEP)
                        },
                        valueRange = HOLD_MS_RANGE.first.toFloat()..HOLD_MS_RANGE.last.toFloat(),
                        steps = HOLD_MS_RANGE.last / HOLD_MS_STEP - 1,
                    )
                },
                trailingContent = { Text("${tuner.holdMs} ms") },
            )
        }

        SettingsSection(title = "In-tune feedback") {
            SwitchRow(
                headline = "Show in-tune feedback",
                checked = tuner.inTuneFeedback,
                onCheckedChange = viewModel::setInTuneFeedback,
            )
            SwitchRow(
                headline = "Play chime",
                checked = tuner.chime,
                onCheckedChange = viewModel::setChime,
            )
            SwitchRow(
                headline = "Vibrate",
                checked = tuner.haptics,
                onCheckedChange = viewModel::setHaptics,
            )
        }
    }
}

private val SectionShape = SmoothShape(20.dp)

// Inset grouped list: a secondary-grey title over a card in the group color, on the grey page
// AppRoot puts behind Settings.
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SectionShape,
            colors = CardDefaults.cardColors(containerColor = LocalGroupedListColors.current.group),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    headline: String,
    selected: T,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerRow(
        headline = headline,
        value = choices.firstOrNull { it.first == selected }?.second ?: "",
        onClick = { open = true },
    )
    if (open) {
        OptionSheet(
            title = headline,
            options = choices.map { PickerOption(it.first, it.second) },
            selected = selected,
            onSelect = { choice ->
                onSelect(choice)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

private fun AccentColor.label(): String = when (this) {
    AccentColor.Wallpaper -> "Match wallpaper"
    else -> name
}

@Composable
private fun AccentDot(accent: AccentColor) {
    val color = accentColors(accent, LocalDarkTheme.current).accent
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun AccentRow(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val options = AccentColor.entries.filter { it != AccentColor.Wallpaper || wallpaperAccentAvailable }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text("Accent color") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentDot(selected)
                Text(selected.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        OptionSheet(
            title = "Accent color",
            options = options.map { PickerOption(it, it.label()) },
            selected = selected,
            onSelect = { choice ->
                onSelect(choice)
                open = false
            },
            onDismiss = { open = false },
            trailing = { accent -> AccentDot(accent) },
        )
    }
}
