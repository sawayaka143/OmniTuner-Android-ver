package com.omnituner.android.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.AccentColor
import com.omnituner.android.data.AppearanceSettings
import com.omnituner.android.data.StartupMode
import com.omnituner.android.data.ThemeMode
import com.omnituner.android.data.TunerSettings
import com.omnituner.android.ui.common.GroupedSection
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.MenuPickerRow
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.android.ui.theme.LocalDarkTheme
import com.omnituner.android.ui.theme.LocalGroupedListColors
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wide screens (landscape) keep the rows to a readable width instead of stretching them
        // edge to edge; the same cap as the bottom sheets.
        Column(modifier = Modifier.widthIn(max = MaxListWidth)) {
            SettingsSections(appearance, tuner, viewModel)
        }
    }
}

private val MaxListWidth = 640.dp

@Composable
private fun SettingsSections(appearance: AppearanceSettings, tuner: TunerSettings, viewModel: SettingsViewModel) {
    GroupedSection(title = "Appearance") {
        ChoiceRow(
            headline = "Theme",
            selected = appearance.themeMode,
            choices = themeChoices,
            onSelect = viewModel::setTheme,
        )
        AccentRow(selected = appearance.accent, onSelect = viewModel::setAccent)
    }

    GroupedSection(title = "Tuner") {
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

    GroupedSection(title = "In-tune feedback") {
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
        menuColor = LocalGroupedListColors.current.group,
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
        menuColor = LocalGroupedListColors.current.group,
        leading = { accent -> AccentDot(accent) },
    )
}
