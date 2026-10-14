package com.omnituner.android.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.AccentColor
import com.omnituner.android.data.StartupMode
import com.omnituner.android.data.ThemeMode
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.MenuPickerRow
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.android.ui.theme.LocalDarkTheme
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.android.ui.theme.accentColors
import com.omnituner.android.ui.theme.wallpaperAccentAvailable

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
    MenuPickerRow(
        headline = headline,
        options = choices.map { PickerOption(it.first, it.second) },
        selected = selected,
        onSelect = onSelect,
    )
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
            .size(14.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun AccentRow(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val options = AccentColor.entries.filter { it != AccentColor.Wallpaper || wallpaperAccentAvailable }
    MenuPickerRow(
        headline = "Accent color",
        options = options.map { PickerOption(it, it.label()) },
        selected = selected,
        onSelect = onSelect,
        leading = { accent -> AccentDot(accent) },
    )
}
