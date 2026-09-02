package com.omnituner.android.ui.scales

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.OmniTunerApp
import com.omnituner.android.R
import com.omnituner.android.audio.GuitarSamplePlayer
import com.omnituner.android.audio.NotePlayer
import com.omnituner.android.ui.common.FretboardCanvas
import com.omnituner.android.ui.common.SectionCard
import com.omnituner.android.ui.common.WebSegmented
import com.omnituner.android.ui.common.WebSegmentedOption
import com.omnituner.android.ui.common.WebToggleRow
import com.omnituner.android.ui.common.WheelOption
import com.omnituner.android.ui.common.WheelSection
import com.omnituner.android.ui.common.WheelSelectRow
import com.omnituner.android.ui.common.WheelSelectSheet
import com.omnituner.core.data.FLAT_NAMES
import com.omnituner.core.data.SCALES
import com.omnituner.core.data.SHARP_NAMES
import com.omnituner.core.prefs.ACCIDENTAL_FLAT
import com.omnituner.core.prefs.ACCIDENTAL_SHARP
import com.omnituner.core.prefs.LABEL_MODE_NOTE_NAMES
import com.omnituner.core.prefs.LABEL_MODE_SCALE_DEGREES
import com.omnituner.core.theory.noteName

@OptIn(ExperimentalMaterial3Api::class)
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
    val rootNames = if (prefsState.accidental == ACCIDENTAL_FLAT) FLAT_NAMES else SHARP_NAMES

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                var instrumentSheetOpen by rememberSaveable { mutableStateOf(false) }
                WheelSelectRow(
                    label = "Instrument",
                    value = state.instrumentLabel,
                    onClick = { instrumentSheetOpen = true },
                )
                if (instrumentSheetOpen) {
                    WheelSelectSheet(
                        title = "Instrument",
                        options = state.instruments.map { WheelOption(it.label) },
                        selectedIndex = state.instruments
                            .indexOfFirst { it.id == state.instrumentId }
                            .coerceAtLeast(0),
                        onConfirm = { index ->
                            viewModel.selectInstrument(state.instruments[index].id)
                            instrumentSheetOpen = false
                        },
                        onDismiss = { instrumentSheetOpen = false },
                    )
                }
                var tuningSheetOpen by rememberSaveable { mutableStateOf(false) }
                WheelSelectRow(
                    label = "Tuning",
                    value = state.tuningLabel,
                    onClick = { tuningSheetOpen = true },
                )
                if (tuningSheetOpen) {
                    WheelSelectSheet(
                        title = "Tuning",
                        options = state.tunings.map { WheelOption(it.label) },
                        selectedIndex = state.tunings
                            .indexOfFirst { it.id == state.tuningId }
                            .coerceAtLeast(0),
                        onConfirm = { index ->
                            viewModel.selectTuning(state.tunings[index].id)
                            tuningSheetOpen = false
                        },
                        onDismiss = { tuningSheetOpen = false },
                    )
                }
            }
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
            var scaleSheetOpen by rememberSaveable { mutableStateOf(false) }
            WheelSelectRow(
                label = "Scale",
                value = "${scale.label}${scale.aka?.let { " ($it)" } ?: ""}",
                onClick = { scaleSheetOpen = true },
            )
            if (scaleSheetOpen) {
                val groupedScales = remember { SCALES.groupBy { it.group } }
                val scaleSections = remember(groupedScales) {
                    groupedScales.map { (group, scales) ->
                        WheelSection(group, scales.map { WheelOption(it.label) })
                    }
                }
                val flatScales = remember(groupedScales) { groupedScales.values.flatten() }
                WheelSelectSheet(
                    title = "Scale",
                    sections = scaleSections,
                    selectedIndex = flatScales
                        .indexOfFirst { it.id == prefsState.scaleId }
                        .coerceAtLeast(0),
                    onConfirm = { index ->
                        viewModel.setScaleId(flatScales[index].id)
                        scaleSheetOpen = false
                    },
                    onDismiss = { scaleSheetOpen = false },
                )
            }
            var rootSheetOpen by rememberSaveable { mutableStateOf(false) }
            WheelSelectRow(
                label = "Root",
                value = noteName(prefsState.rootPitchClass, prefsState.accidental == ACCIDENTAL_FLAT),
                onClick = { rootSheetOpen = true },
            )
            if (rootSheetOpen) {
                WheelSelectSheet(
                    title = "Root",
                    options = rootNames.map { WheelOption(it) },
                    selectedIndex = prefsState.rootPitchClass.coerceIn(0, rootNames.lastIndex),
                    onConfirm = { index ->
                        viewModel.setRootPitchClass(index)
                        rootSheetOpen = false
                    },
                    onDismiss = { rootSheetOpen = false },
                )
            }
            WebSegmented(
                options = listOf(
                    WebSegmentedOption("Sharps", ACCIDENTAL_SHARP),
                    WebSegmentedOption("Flats", ACCIDENTAL_FLAT),
                ),
                selectedKey = prefsState.accidental,
                onSelect = viewModel::setAccidental,
            )
        }

        SectionCard {
            Text("Fretboard", style = MaterialTheme.typography.titleMedium)
            WebSegmented(
                options = listOf(12, 15, 21).map { WebSegmentedOption("$it", it) },
                selectedKey = prefsState.fretCount,
                onSelect = viewModel::setFretCount,
            )
            WebSegmented(
                options = listOf(
                    WebSegmentedOption("Notes", LABEL_MODE_NOTE_NAMES),
                    WebSegmentedOption("Degrees", LABEL_MODE_SCALE_DEGREES),
                ),
                selectedKey = prefsState.labelMode,
                onSelect = viewModel::setLabelMode,
            )
            WebToggleRow(
                label = "Show outside scale",
                checked = prefsState.showOutsideScale,
                onCheckedChange = viewModel::setShowOutsideScale,
            )

            FretboardCanvas(
                board = state.board,
                showLabels = true,
                useNoteNames = prefsState.labelMode == LABEL_MODE_NOTE_NAMES,
                showOutside = prefsState.showOutsideScale,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(state.board) {
                        detectTapGestures { offset ->
                            val fretCount = state.board.firstOrNull()?.size?.minus(1)
                                ?: return@detectTapGestures
                            val strings = state.board.size
                            val pad = 14.sp.toPx()
                            val rowHeight = (size.height - 2 * pad) / strings
                            val col = ((offset.x / size.width) * (fretCount + 1))
                                .toInt().coerceIn(0, fretCount)
                            val row = ((offset.y - pad) / rowHeight)
                                .toInt().coerceIn(0, strings - 1)
                            state.board.getOrNull(row)?.getOrNull(col)?.let(viewModel::playCell)
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
