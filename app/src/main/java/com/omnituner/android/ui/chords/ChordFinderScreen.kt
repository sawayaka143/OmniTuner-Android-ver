package com.omnituner.android.ui.chords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PickerRow
import com.omnituner.core.data.PROGRESSION_PRESETS
import com.omnituner.core.theory.VoicingShape

private val TONIC_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordFinderScreen(viewModel: ChordFinderViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    var tonicSheet by rememberSaveable { mutableStateOf(false) }
    var presetSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Chord finder", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = ui.chordInput,
                        onValueChange = viewModel::setChordInput,
                        label = { Text("Chord symbol (e.g. Cmaj7, Bø, F#m7b5)") },
                        singleLine = true,
                        isError = ui.chordError != null,
                        supportingText = {
                            val error = ui.chordError
                            val chord = ui.parsedChord
                            when {
                                error != null -> Text(error)
                                chord != null -> Text(
                                    "root ${chord.rootPc} · ${chord.quality} · " +
                                        chord.intervals.joinToString(" "),
                                )
                                else -> Text("Enter a chord symbol")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ui.badge?.let { badge ->
                        Text(
                            text = badge.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = badgeColor(badge.kind),
                        )
                    }
                }
                PickerRow(
                    headline = "Tonic",
                    value = ui.tonic,
                    onClick = { tonicSheet = true },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Key",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::playProgression) { Text("Play progression") }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = ui.progressionInput,
                        onValueChange = viewModel::setProgressionInput,
                        label = { Text("Progression (chords or roman numerals)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ui.detectedKey?.let { key ->
                        Text(
                            text = "${key.tonicName} ${key.mode} · ${key.confidence}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        key.alternatives.forEach { alt ->
                            Text(
                                text = "alt: ${alt.tonicName} ${alt.mode} (${alt.confidence})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (ui.detectedKey != null) {
                    ProgressionChips(ui = ui, onChordTap = { chord ->
                        viewModel.setChordInput(chord.symbol)
                        viewModel.playChord(chord)
                    })
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Progressions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                PickerRow(
                    headline = "Progression preset",
                    value = "Load a preset",
                    onClick = { presetSheet = true },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Voicings", style = MaterialTheme.typography.titleMedium)
                if (ui.parsedChord == null) {
                    Text(
                        "Parse a chord to see voicings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (ui.voicings.isEmpty()) {
                    Text(
                        "No voicings for this tuning.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ui.voicings.forEach { shape -> VoicingRow(shape) }
                }
            }
        }
    }

    if (tonicSheet) {
        OptionSheet(
            title = "Tonic",
            options = TONIC_NAMES.map { PickerOption(it, it) },
            selected = ui.tonic,
            onSelect = { tonic ->
                viewModel.setTonic(tonic)
                tonicSheet = false
            },
            onDismiss = { tonicSheet = false },
        )
    }

    if (presetSheet) {
        OptionSheet(
            title = "Progression preset",
            options = PROGRESSION_PRESETS.map { preset ->
                PickerOption(preset, preset.name, supporting = preset.degrees.joinToString(" "))
            },
            selected = null,
            onSelect = { preset ->
                viewModel.applyPreset(preset)
                presetSheet = false
            },
            onDismiss = { presetSheet = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionChips(
    ui: ChordFinderUiState,
    onChordTap: (com.omnituner.core.theory.ParsedChord) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ui.progression.forEach { chord ->
            FilterChip(
                selected = chord.symbol == ui.parsedChord?.symbol,
                onClick = { onChordTap(chord) },
                label = { Text(chord.symbol) },
            )
        }
    }
}

@Composable
private fun VoicingRow(shape: VoicingShape) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            shape.frets.forEach { fret ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = when (fret) {
                                0 -> MaterialTheme.colorScheme.tertiaryContainer
                                null -> MaterialTheme.colorScheme.surface
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = fret?.toString() ?: "x",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Text(
            text = "span ${shape.span} · pos ${shape.position} · " +
                "bass ${if (shape.bassIsRoot) "root" else "non-root"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun badgeColor(kind: String): Color = when (kind) {
    "good" -> MaterialTheme.colorScheme.tertiary
    "warn" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}
