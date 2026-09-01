package com.omnituner.android.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.R
import com.omnituner.android.ui.common.RepeatStepperRow
import com.omnituner.android.ui.common.WebCard
import com.omnituner.core.metronome.DENOMINATORS
import com.omnituner.core.metronome.METER_PRESETS
import com.omnituner.core.metronome.PATTERN_PRESETS
import com.omnituner.core.metronome.PolyState
import com.omnituner.core.metronome.PRESETS_MAX
import com.omnituner.core.metronome.SUBDIVISIONS
import com.omnituner.core.sound.MetronomeVoices
import com.omnituner.core.timing.getTempoMarking
import com.omnituner.core.timing.tapBpm
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(
    prefs: com.omnituner.core.prefs.MetronomePreferences,
    viewModel: MetronomeViewModel = viewModel(factory = MetronomeViewModel.factory(prefs)),
) {
    val state by viewModel.ui.collectAsState()
    val metronome = state.state

    var showPresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Transport header
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${metronome.bpm.roundToInt()}",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${getTempoMarking(metronome.bpm)} · " +
                        "${metronome.timeSignature.numerator}/${metronome.timeSignature.denominator}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledIconButton(
                    onClick = viewModel::toggle,
                    modifier = Modifier
                        .size(72.dp)
                        .semantics {
                            contentDescription = if (state.isPlaying) "Stop metronome" else "Start metronome"
                        },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.isPlaying) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                ) {
                    Icon(
                        if (state.isPlaying) {
                            painterResource(R.drawable.tabler_player_stop)
                        } else {
                            painterResource(R.drawable.tabler_player_play)
                        },
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
                if (state.countIn) {
                    Text(
                        "Count-in bar ${state.barIndex}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    Text(
                        "Bar ${state.barIndex} · pattern ${state.patternPos + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // BPM controls
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Tempo", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { viewModel.setBpm(metronome.bpm - 5) }) { Text("−5") }
                    TextButton(onClick = { viewModel.setBpm(metronome.bpm - 1) }) { Text("−1") }
                    TextButton(onClick = { viewModel.setBpm(metronome.bpm + 1) }) { Text("+1") }
                    TextButton(onClick = { viewModel.setBpm(metronome.bpm + 5) }) { Text("+5") }
                }
                BpmDial(
                    bpm = metronome.bpm,
                    onBpmChange = viewModel::setBpm,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                TapTempoRow(onBpm = viewModel::setBpm)
            }
        }

        // Meter
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Meter", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(METER_PRESETS) { preset ->
                        FilterChip(
                            selected = metronome.timeSignature == preset,
                            onClick = {
                                viewModel.setTimeSignature(preset.numerator, preset.denominator)
                            },
                            label = { Text("${preset.numerator}/${preset.denominator}") },
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DENOMINATORS) { den ->
                        FilterChip(
                            selected = metronome.timeSignature.denominator == den,
                            onClick = {
                                viewModel.setTimeSignature(metronome.timeSignature.numerator, den)
                            },
                            label = { Text("/$den") },
                        )
                    }
                }
                RepeatStepperRow(
                    label = "Numerator",
                    valueText = "${metronome.timeSignature.numerator}",
                    onDelta = viewModel::changeTimeSignatureNumerator,
                )
                RepeatStepperRow(
                    label = "Divisions per beat",
                    valueText = "${metronome.divisionsPerBeat}",
                    onDelta = viewModel::changeDivisionsPerBeat,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SUBDIVISIONS) { option ->
                        FilterChip(
                            selected = metronome.divisionsPerBeat == option.n,
                            onClick = { viewModel.setDivisionsPerBeat(option.n) },
                            label = { Text(option.shortLabel) },
                        )
                    }
                }
            }
        }

        // Bar pattern
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bar mute pattern", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PATTERN_PRESETS) { preset ->
                        FilterChip(
                            selected = metronome.barPattern == preset.bars,
                            onClick = { viewModel.setBarPattern(preset.bars) },
                            label = { Text(preset.label) },
                        )
                    }
                }
                // 16 pads; tapping pad 1..n sets pattern length, toggling beyond truncates
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..16).toList()) { bar ->
                        val active = metronome.barPattern.getOrNull(bar - 1) == 1
                        val within = bar <= metronome.barPattern.size
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                    when {
                                        active -> MaterialTheme.colorScheme.primary
                                        within -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    val current = metronome.barPattern.toMutableList()
                                    if (bar <= current.size) {
                                        current[bar - 1] = if (current[bar - 1] == 1) 0 else 1
                                        viewModel.setBarPattern(current)
                                    } else {
                                        while (current.size < bar) current.add(0)
                                        current[bar - 1] = 1
                                        viewModel.setBarPattern(current)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$bar",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Polyrhythm
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Polyrhythm", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = metronome.poly.enabled,
                        onCheckedChange = { viewModel.setPoly(enabled = it) },
                    )
                }
                if (metronome.poly.enabled) {
                    RepeatStepperRow(
                        label = "Poly events",
                        valueText = "${metronome.poly.events}",
                        onDelta = viewModel::changePolyEvents,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = metronome.poly.accentFirst,
                            onCheckedChange = { viewModel.setPoly(accentFirst = it) },
                        )
                        Text("Accent first event", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        // Sounds
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sounds", style = MaterialTheme.typography.titleMedium)
                for (role in listOf("downbeat", "beat", "subdivision", "poly")) {
                    val roleState = when (role) {
                        "downbeat" -> metronome.sounds.downbeat
                        "beat" -> metronome.sounds.beat
                        "subdivision" -> metronome.sounds.subdivision
                        else -> metronome.sounds.poly
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            role.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.width(96.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(MetronomeVoices.options()) { option ->
                                FilterChip(
                                    selected = roleState.id == option.id,
                                    onClick = {
                                        viewModel.setSoundRole(role, option.id)
                                        viewModel.previewVoice(option.id, roleState.vol)
                                    },
                                    label = { Text(option.label) },
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Master volume", modifier = Modifier.width(96.dp))
                    Slider(
                        value = metronome.masterVol.toFloat(),
                        onValueChange = { viewModel.setMasterVol(it.toDouble()) },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Master volume" },
                    )
                }
            }
        }

        // Count-in + ramp
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Count-in bar", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = metronome.countIn, onCheckedChange = viewModel::setCountIn)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tempo ramp", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = metronome.ramp.enabled,
                        onCheckedChange = { viewModel.setRamp(enabled = it) },
                    )
                }
                if (metronome.ramp.enabled) {
                    RepeatStepperRow(
                        label = "Target BPM",
                        valueText = "${metronome.ramp.targetBpm.roundToInt()}",
                        onDelta = viewModel::changeRampTargetBpm,
                    )
                    RepeatStepperRow(
                        label = "Ramp bars",
                        valueText = "${metronome.ramp.bars}",
                        onDelta = viewModel::changeRampBars,
                    )
                }
            }
        }

        // Presets
        WebCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Presets (${state.presets.size}/$PRESETS_MAX)", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showPresetDialog = true }) { Text("Save current") }
                }
                for (preset in state.presets) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            preset.name,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.applyPreset(preset.id) },
                        )
                        Text(
                            "${preset.state.bpm.roundToInt()} bpm",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                            Icon(painterResource(R.drawable.tabler_x), contentDescription = "Delete preset ${preset.name}")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Save preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset name") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.savePreset(presetName)
                        presetName = ""
                        showPresetDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TapTempoRow(onBpm: (Double) -> Unit) {
    val taps = remember { mutableStateListOf<Long>() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Tap tempo", style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                val now = android.os.SystemClock.elapsedRealtime()
                if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                taps.add(now)
                while (taps.size > 6) taps.removeAt(0)
                if (taps.size >= 2) {
                    val intervals = buildList {
                        for (i in 1 until taps.size) add((taps[i] - taps[i - 1]).toDouble())
                    }
                    tapBpm(intervals)?.let(onBpm)
                }
            },
            modifier = Modifier.semantics { contentDescription = "Tap tempo" },
        ) { Text("TAP") }
    }
}
