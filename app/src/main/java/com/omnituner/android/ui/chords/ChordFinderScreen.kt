package com.omnituner.android.ui.chords

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.audio.GuitarSamplePlayer
import com.omnituner.android.audio.NotePlayer
import com.omnituner.android.ui.common.SectionCard
import com.omnituner.core.data.PROGRESSION_PRESETS
import com.omnituner.core.theory.VoicingShape

@Composable
fun ChordFinderScreen(app: OmniTunerApp) {
    val container = app.container
    val notePlayer = remember { NotePlayer() }
    val samplePlayer = remember { GuitarSamplePlayer(app.applicationContext) }
    val viewModel: ChordFinderViewModel = viewModel(
        factory = ChordFinderViewModel.factory(
            container.scalePreferences,
            container.instrumentRegistry,
            notePlayer,
            samplePlayer,
        ),
    )
    val state by viewModel.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Chord input
        SectionCard {
            Text("Chord finder", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.chordInput,
                onValueChange = viewModel::setChordInput,
                label = { Text("Chord symbol (e.g. Cmaj7, Bø, F#m7b5)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = state.chordError != null,
                supportingText = {
                    when {
                        state.chordError != null -> Text(state.chordError!!)
                        state.parsedChord != null -> {
                            val chord = state.parsedChord!!
                            Text(
                                "root ${chord.rootPc} · ${chord.quality} · " +
                                    chord.intervals.joinToString(" ") { it.toString() },
                            )
                        }
                        else -> Text("Enter a chord symbol")
                    }
                },
            )

            val badge = state.badges[state.chordInput.trim()]
            if (badge != null) {
                Text(
                    badge.text,
                    color = when (badge.kind) {
                        "good" -> Color(0xFF7ECBA8)
                        "warn" -> Color(0xFFFFC163)
                        else -> MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val tonicChips = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tonicChips) { tonic ->
                    FilterChip(
                        selected = state.tonicInput == tonic,
                        onClick = { viewModel.setTonic(tonic) },
                        label = { Text(tonic) },
                    )
                }
            }
        }

        // Key finder
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Key", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::playProgression) { Text("Play progression") }
            }
            OutlinedTextField(
                value = state.progressionInput,
                onValueChange = viewModel::setProgressionInput,
                label = { Text("Progression (chords or roman numerals)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val key = state.detectedKey
            if (key != null) {
                Text(
                    "${key.tonicName} ${key.mode} · ${key.confidence}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF7ECBA8),
                )
                for (alt in key.alternatives) {
                    Text(
                        "alt: ${alt.tonicName} ${alt.mode} (${alt.confidence})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    for (chord in state.progression) {
                        FilterChip(
                            selected = chord.symbol == state.parsedChord?.symbol,
                            onClick = {
                                viewModel.setChordInput(chord.symbol)
                                viewModel.playChord(chord)
                            },
                            label = { Text(chord.symbol) },
                        )
                    }
                }
            }
        }

        // Progression presets
        SectionCard {
            Text("Progressions", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PROGRESSION_PRESETS) { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text(preset.name) },
                    )
                }
            }
        }

        // Voicings
        SectionCard {
            Text("Voicings", style = MaterialTheme.typography.titleMedium)
            if (state.parsedChord == null) {
                Text("Parse a chord to see voicings.")
            } else if (state.tuningParseError != null) {
                Text(state.tuningParseError!!, color = MaterialTheme.colorScheme.error)
            } else {
                for (shape in state.voicings) {
                    VoicingRow(shape, state.tuningSummary)
                }
            }
        }
    }
}

@Composable
private fun VoicingRow(shape: VoicingShape, tuningSummary: String) {
    val frets = shape.frets
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (fret in frets) {
                    val label = fret?.toString() ?: "x"
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                if (fret == 0) Color(0xFF7ECBA8) else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                "span ${shape.span} · pos ${shape.position} · " +
                    "bass ${if (shape.bassIsRoot) "root" else "non-root"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
