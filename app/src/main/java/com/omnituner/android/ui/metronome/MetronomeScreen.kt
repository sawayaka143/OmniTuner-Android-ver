package com.omnituner.android.ui.metronome

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PickerRow
import com.omnituner.android.ui.common.RepeatingIconButton
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.core.metronome.BPM_MAX
import com.omnituner.core.metronome.BPM_MIN
import com.omnituner.core.metronome.DENOMINATORS
import com.omnituner.core.metronome.METER_PRESETS
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.metronome.PATTERN_MAX_BARS
import com.omnituner.core.metronome.PATTERN_PRESETS
import com.omnituner.core.metronome.PRESETS_MAX
import com.omnituner.core.metronome.SUBDIVISIONS
import com.omnituner.core.sound.MetronomeVoices
import com.omnituner.core.timing.getTempoMarking
import com.omnituner.core.timing.tapBpm
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(viewModel: MetronomeViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    val metronome = ui.state
    val tapTempo = rememberTapTempo(viewModel::setBpm)
    var savingPreset by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TempoCard(ui = ui, viewModel = viewModel, onTap = tapTempo)
        MeterCard(metronome = metronome, viewModel = viewModel)
        PatternCard(metronome = metronome, viewModel = viewModel)
        PolyCard(metronome = metronome, viewModel = viewModel)
        SoundsCard(metronome = metronome, viewModel = viewModel)
        RampCard(metronome = metronome, viewModel = viewModel)
        PresetsCard(ui = ui, viewModel = viewModel, onSave = { savingPreset = true })
    }

    if (savingPreset) {
        SavePresetDialog(
            onDismiss = { savingPreset = false },
            onSave = { name ->
                viewModel.savePreset(name)
                savingPreset = false
            },
        )
    }
}

@Composable
private fun TempoCard(ui: MetronomeUiState, viewModel: MetronomeViewModel, onTap: () -> Unit) {
    val metronome = ui.state
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${metronome.bpm.roundToInt()}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "BPM · ${getTempoMarking(metronome.bpm)} · " +
                    "${metronome.timeSignature.numerator}/${metronome.timeSignature.denominator}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepeatingIconButton(label = "Decrease tempo", onStep = { viewModel.changeBpm(-1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = null)
                }
                Slider(
                    value = metronome.bpm.toFloat(),
                    onValueChange = { viewModel.setBpm(it.toDouble()) },
                    valueRange = BPM_MIN.toFloat()..BPM_MAX.toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = "Tempo" },
                )
                RepeatingIconButton(label = "Increase tempo", onStep = { viewModel.changeBpm(1) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onTap) { Text("Tap tempo") }
                FilledIconButton(
                    onClick = viewModel::toggle,
                    modifier = Modifier
                        .size(64.dp)
                        .semantics {
                            contentDescription = if (ui.isPlaying) "Stop metronome" else "Start metronome"
                        },
                ) {
                    Icon(
                        imageVector = if (ui.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { ui.progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (ui.inCountIn) {
                    "Count-in bar ${ui.barIndex}"
                } else {
                    "Bar ${ui.barIndex} · pattern ${ui.patternPos + 1}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeterCard(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    val meter = metronome.timeSignature
    var meterSheet by rememberSaveable { mutableStateOf(false) }
    var divisionsSheet by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Meter",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            PickerRow(
                headline = "Preset",
                value = "${meter.numerator}/${meter.denominator}",
                onClick = { meterSheet = true },
            )
            Column(modifier = Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DENOMINATORS.forEachIndexed { index, denominator ->
                        SegmentedButton(
                            selected = meter.denominator == denominator,
                            onClick = { viewModel.setTimeSignature(meter.numerator, denominator) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = DENOMINATORS.size),
                        ) {
                            Text("/$denominator")
                        }
                    }
                }
            }
            NumberStepper(
                headline = "Numerator",
                valueText = "${meter.numerator}",
                onDecrement = { viewModel.changeNumerator(-1) },
                onIncrement = { viewModel.changeNumerator(1) },
            )
            NumberStepper(
                headline = "Divisions per beat",
                valueText = "${metronome.divisionsPerBeat}",
                onDecrement = { viewModel.changeDivisionsPerBeat(-1) },
                onIncrement = { viewModel.changeDivisionsPerBeat(1) },
            )
            PickerRow(
                headline = "Subdivision",
                value = SUBDIVISIONS.firstOrNull { it.n == metronome.divisionsPerBeat }?.shortLabel
                    ?: "${metronome.divisionsPerBeat}",
                onClick = { divisionsSheet = true },
            )
        }
    }

    if (meterSheet) {
        OptionSheet(
            title = "Meter preset",
            options = METER_PRESETS.map { preset ->
                PickerOption(
                    key = preset,
                    label = "${preset.numerator}/${preset.denominator}",
                    supporting = when (preset.denominator) {
                        8 -> "compound feel"
                        16 -> "fast"
                        else -> "simple"
                    },
                )
            },
            selected = meter,
            onSelect = { preset ->
                viewModel.setTimeSignature(preset.numerator, preset.denominator)
                meterSheet = false
            },
            onDismiss = { meterSheet = false },
        )
    }

    if (divisionsSheet) {
        OptionSheet(
            title = "Subdivision",
            options = SUBDIVISIONS.map { PickerOption(it.n, it.label) },
            selected = metronome.divisionsPerBeat,
            onSelect = { n ->
                viewModel.setDivisionsPerBeat(n)
                divisionsSheet = false
            },
            onDismiss = { divisionsSheet = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternCard(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var presetSheet by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bar mute pattern", style = MaterialTheme.typography.titleMedium)
                Switch(checked = expanded, onCheckedChange = { expanded = it })
            }
            if (expanded) {
                PickerRow(
                    headline = "Preset",
                    value = PATTERN_PRESETS.firstOrNull { it.bars == metronome.barPattern }?.label ?: "Custom",
                    onClick = { presetSheet = true },
                )
                FlowRow(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..PATTERN_MAX_BARS).forEach { bar ->
                        FilterChip(
                            selected = metronome.barPattern.getOrNull(bar - 1) == 1,
                            onClick = {
                                val pattern = metronome.barPattern.toMutableList()
                                while (pattern.size < bar) pattern.add(0)
                                pattern[bar - 1] = if (pattern[bar - 1] == 1) 0 else 1
                                viewModel.setBarPattern(pattern)
                            },
                            label = { Text("$bar") },
                        )
                    }
                }
            }
        }
    }

    if (presetSheet) {
        OptionSheet(
            title = "Pattern preset",
            options = PATTERN_PRESETS.map { preset ->
                PickerOption(preset.bars, preset.label, supporting = "${preset.bars.count { it == 1 }} bars")
            },
            selected = metronome.barPattern,
            onSelect = { bars ->
                viewModel.setBarPattern(bars)
                presetSheet = false
            },
            onDismiss = { presetSheet = false },
        )
    }
}

@Composable
private fun PolyCard(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            SwitchRow(
                headline = "Polyrhythm",
                checked = metronome.poly.enabled,
                onCheckedChange = viewModel::setPolyEnabled,
            )
            if (metronome.poly.enabled) {
                NumberStepper(
                    headline = "Poly events",
                    valueText = "${metronome.poly.events}",
                    onDecrement = { viewModel.changePolyEvents(-1) },
                    onIncrement = { viewModel.changePolyEvents(1) },
                )
                SwitchRow(
                    headline = "Accent first event",
                    checked = metronome.poly.accentFirst,
                    onCheckedChange = viewModel::setPolyAccentFirst,
                )
            }
        }
    }
}

@Composable
private fun SoundsCard(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    val voices = remember { MetronomeVoices.options() }
    var openSlot by rememberSaveable { mutableStateOf<SoundSlot?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Sounds",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            SoundSlot.entries.forEach { slot ->
                val voiceId = metronome.sounds.role(slot).id
                PickerRow(
                    headline = slot.label,
                    value = voices.firstOrNull { it.id == voiceId }?.label ?: voiceId,
                    onClick = { openSlot = slot },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Master volume", style = MaterialTheme.typography.bodyLarge)
            }
            Slider(
                value = metronome.masterVol.toFloat(),
                onValueChange = { viewModel.setMasterVolume(it.toDouble()) },
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "Master volume" },
            )
        }
    }

    openSlot?.let { slot ->
        OptionSheet(
            title = slot.label,
            options = voices.map { PickerOption(it.id, it.label) },
            selected = metronome.sounds.role(slot).id,
            onSelect = { id ->
                viewModel.setVoice(slot, id)
                viewModel.previewVoice(id, metronome.sounds.role(slot).vol)
                openSlot = null
            },
            onDismiss = { openSlot = null },
        )
    }
}

@Composable
private fun RampCard(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            SwitchRow(
                headline = "Count-in bar",
                checked = metronome.countIn,
                onCheckedChange = viewModel::setCountIn,
            )
            SwitchRow(
                headline = "Tempo ramp",
                checked = metronome.ramp.enabled,
                onCheckedChange = viewModel::setRampEnabled,
            )
            if (metronome.ramp.enabled) {
                NumberStepper(
                    headline = "Target BPM",
                    valueText = "${metronome.ramp.targetBpm.roundToInt()}",
                    onDecrement = { viewModel.changeRampTargetBpm(-1) },
                    onIncrement = { viewModel.changeRampTargetBpm(1) },
                )
                NumberStepper(
                    headline = "Ramp bars",
                    valueText = "${metronome.ramp.bars}",
                    onDecrement = { viewModel.changeRampBars(-1) },
                    onIncrement = { viewModel.changeRampBars(1) },
                )
            }
        }
    }
}

@Composable
private fun PresetsCard(ui: MetronomeUiState, viewModel: MetronomeViewModel, onSave: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Presets (${ui.presets.size}/$PRESETS_MAX)",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onSave) { Text("Save current") }
            }
            ui.presets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Apply preset ${preset.name}" }
                            .padding(vertical = 12.dp),
                    )
                    Text(
                        text = "${preset.state.bpm.roundToInt()} bpm",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete preset ${preset.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun rememberTapTempo(onBpm: (Double) -> Unit): () -> Unit {
    val taps = remember { mutableStateListOf<Long>() }
    val latestOnBpm by rememberUpdatedState(onBpm)
    return remember(taps) {
        {
            val now = SystemClock.elapsedRealtime()
            if (taps.isNotEmpty() && now - taps.last() > TAP_RESET_MS) taps.clear()
            taps.add(now)
            while (taps.size > TAP_HISTORY) taps.removeAt(0)
            if (taps.size >= 2) {
                val intervals = buildList {
                    for (i in 1 until taps.size) add((taps[i] - taps[i - 1]).toDouble())
                }
                tapBpm(intervals)?.let(latestOnBpm)
            }
        }
    }
}

private const val TAP_RESET_MS = 2000L
private const val TAP_HISTORY = 6
