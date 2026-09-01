package com.omnituner.android.ui.scales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.R
import com.omnituner.android.audio.GuitarSamplePlayer
import com.omnituner.android.audio.NotePlayer
import com.omnituner.android.ui.common.FretboardCanvas
import com.omnituner.android.ui.common.InstrumentTuningPicker
import com.omnituner.android.ui.common.SectionCard
import com.omnituner.core.data.SCALES
import com.omnituner.core.prefs.ACCIDENTAL_FLAT
import com.omnituner.core.prefs.ACCIDENTAL_SHARP
import com.omnituner.core.prefs.LABEL_MODE_NOTE_NAMES
import com.omnituner.core.prefs.LABEL_MODE_SCALE_DEGREES

@Composable
fun ScalesScreen(app: OmniTunerApp) {
    val container = app.container
    val notePlayer = remember { NotePlayer() }
    val samplePlayer = remember { GuitarSamplePlayer(app.applicationContext) }
    val viewModel: ScalesViewModel = viewModel(
        factory = ScalesViewModel.factory(
            container.scalePreferences,
            container.instrumentRegistry,
            notePlayer,
            samplePlayer,
        ),
    )
    val state by viewModel.ui.collectAsState()
    val prefsState = state.prefs
    val scale = SCALES.find { it.id == prefsState.scaleId } ?: SCALES.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstrumentTuningPicker(
                instruments = state.instruments,
                tunings = state.tunings,
                instrumentLabel = state.instrumentLabel,
                tuningLabel = state.tuningLabel,
                onSelectInstrument = viewModel::selectInstrument,
                onSelectTuning = viewModel::selectTuning,
            )
            IconButton(
                onClick = { viewModel.playScale(down = false) },
                modifier = Modifier.semantics {
                    contentDescription = if (state.isPlaying) "Stop playback" else "Play scale"
                },
            ) {
                Icon(
                    painterResource(R.drawable.tabler_player_play),
                    contentDescription = null,
                    tint = if (state.isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        SectionCard {
            Text("Scale", style = MaterialTheme.typography.titleMedium)
            Text(
                "${scale.label}${scale.aka?.let { " ($it)" } ?: ""} Â· ${prefsState.rootPitchClass}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SCALES) { item ->
                    FilterChip(
                        selected = prefsState.scaleId == item.id,
                        onClick = { viewModel.setScaleId(item.id) },
                        label = { Text(item.label) },
                    )
                }
            }
            Text("Root", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((0..11).toList()) { pc ->
                    FilterChip(
                        selected = prefsState.rootPitchClass == pc,
                        onClick = { viewModel.setRootPitchClass(pc) },
                        label = {
                            Text(
                                com.omnituner.core.theory.noteName(
                                    pc,
                                    prefsState.accidental == ACCIDENTAL_FLAT,
                                ),
                            )
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = prefsState.accidental == ACCIDENTAL_SHARP,
                    onClick = { viewModel.setAccidental(ACCIDENTAL_SHARP) },
                    label = { Text("Sharps") },
                )
                FilterChip(
                    selected = prefsState.accidental == ACCIDENTAL_FLAT,
                    onClick = { viewModel.setAccidental(ACCIDENTAL_FLAT) },
                    label = { Text("Flats") },
                )
            }
        }

        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Fretboard", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (frets in listOf(12, 15, 21)) {
                        FilterChip(
                            selected = prefsState.fretCount == frets,
                            onClick = { viewModel.setFretCount(frets) },
                            label = { Text("$frets") },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = prefsState.labelMode == LABEL_MODE_NOTE_NAMES,
                    onClick = { viewModel.setLabelMode(LABEL_MODE_NOTE_NAMES) },
                    label = { Text("Notes") },
                )
                FilterChip(
                    selected = prefsState.labelMode == LABEL_MODE_SCALE_DEGREES,
                    onClick = { viewModel.setLabelMode(LABEL_MODE_SCALE_DEGREES) },
                    label = { Text("Degrees") },
                )
                FilterChip(
                    selected = prefsState.showOutsideScale,
                    onClick = { viewModel.setShowOutsideScale(!prefsState.showOutsideScale) },
                    label = { Text("Show outside") },
                )
            }

            val board = if (prefsState.labelMode == LABEL_MODE_SCALE_DEGREES) {
                // degree labels: cells already carry interval labels; canvas draws
                // interval.label when showLabels is true, matching degree mode.
                state.board
            } else {
                state.board
            }
            FretboardCanvas(
                board = board,
                showLabels = true,
                onCellTap = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(state.board) {
                        detectTapGestures { offset ->
                            val fretCount = state.board.firstOrNull()?.size?.minus(1) ?: return@detectTapGestures
                            val strings = state.board.size
                            val labelWidth = size.width * 0.09f
                            val fretArea = size.width - labelWidth
                            val rowHeight = size.height / strings
                            val stringIndex = (offset.y / rowHeight).toInt().coerceIn(0, strings - 1)
                            val fret = (((offset.x - labelWidth) / fretArea) * fretCount)
                                .toInt().coerceIn(0, fretCount)
                            state.board.getOrNull(stringIndex)?.getOrNull(fret)?.let(viewModel::playCell)
                        }
                    }
                    .semantics { contentDescription = "Fretboard diagram for ${scale.label}" },
            )
            Text(
                text = state.tuningSummary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
