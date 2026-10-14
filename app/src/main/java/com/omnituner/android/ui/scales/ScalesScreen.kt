package com.omnituner.android.ui.scales

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.Accidental
import com.omnituner.android.data.FRET_COUNT_OPTIONS
import com.omnituner.android.data.LabelMode
import com.omnituner.android.ui.common.FretboardCanvas
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PickerRow
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.core.data.FLAT_NAMES
import com.omnituner.core.data.SCALES
import com.omnituner.core.data.SHARP_NAMES
import com.omnituner.core.theory.noteName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScalesScreen(viewModel: ScalesViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    val settings = ui.settings
    val scale = SCALES.firstOrNull { it.id == settings.scaleId } ?: SCALES.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InstrumentCard(ui = ui, viewModel = viewModel)
        ScaleCard(ui = ui, viewModel = viewModel)
        FretboardCard(ui = ui, viewModel = viewModel, scaleLabel = scale.label)
    }
}

@Composable
private fun InstrumentCard(ui: ScalesUiState, viewModel: ScalesViewModel) {
    var instrumentSheet by rememberSaveable { mutableStateOf(false) }
    var tuningSheet by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Instrument",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = viewModel::togglePlayScale,
                    modifier = Modifier.semantics {
                        contentDescription = if (ui.isPlaying) "Stop playback" else "Play scale"
                    },
                ) {
                    Icon(
                        imageVector = if (ui.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            PickerRow(
                headline = "Instrument",
                value = ui.instrument.label,
                onClick = { instrumentSheet = true },
            )
            PickerRow(
                headline = "Tuning",
                value = ui.tuning.label,
                onClick = { tuningSheet = true },
            )
        }
    }

    if (instrumentSheet) {
        OptionSheet(
            title = "Instrument",
            options = ui.instruments.map { PickerOption(it.id, it.label) },
            selected = ui.instrument.id,
            onSelect = { id ->
                viewModel.selectInstrument(id)
                instrumentSheet = false
            },
            onDismiss = { instrumentSheet = false },
        )
    }

    if (tuningSheet) {
        OptionSheet(
            title = "Tuning",
            options = ui.tunings.map { PickerOption(it.id, it.label) },
            selected = ui.tuning.id,
            onSelect = { id ->
                viewModel.selectTuning(id)
                tuningSheet = false
            },
            onDismiss = { tuningSheet = false },
        )
    }
}

@Composable
private fun ScaleCard(ui: ScalesUiState, viewModel: ScalesViewModel) {
    val settings = ui.settings
    val flats = settings.accidental == Accidental.Flat
    val rootNames = if (flats) FLAT_NAMES else SHARP_NAMES
    val scale = SCALES.firstOrNull { it.id == settings.scaleId } ?: SCALES.first()
    var scaleSheet by rememberSaveable { mutableStateOf(false) }
    var rootSheet by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Scale",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            PickerRow(
                headline = "Scale",
                value = "${scale.label}${scale.aka?.let { " ($it)" } ?: ""}",
                onClick = { scaleSheet = true },
            )
            PickerRow(
                headline = "Root",
                value = noteName(settings.rootPitchClass, flats),
                onClick = { rootSheet = true },
            )
            Column(modifier = Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Accidental.entries.forEachIndexed { index, accidental ->
                        SegmentedButton(
                            selected = settings.accidental == accidental,
                            onClick = { viewModel.setAccidental(accidental) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Accidental.entries.size, baseShape = SmoothShape.Full),
                        ) {
                            Text(if (accidental == Accidental.Sharp) "Sharps" else "Flats")
                        }
                    }
                }
            }
        }
    }

    if (scaleSheet) {
        OptionSheet(
            title = "Scale",
            options = SCALES.map { PickerOption(it.id, it.label, supporting = it.aka, section = it.group) },
            selected = scale.id,
            onSelect = { id ->
                viewModel.setScaleId(id)
                scaleSheet = false
            },
            onDismiss = { scaleSheet = false },
        )
    }

    if (rootSheet) {
        OptionSheet(
            title = "Root",
            options = rootNames.mapIndexed { index, name -> PickerOption(index, name) },
            selected = settings.rootPitchClass,
            onSelect = { root ->
                viewModel.setRootPitchClass(root)
                rootSheet = false
            },
            onDismiss = { rootSheet = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FretboardCard(ui: ScalesUiState, viewModel: ScalesViewModel, scaleLabel: String) {
    val settings = ui.settings
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Fretboard",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FRET_COUNT_OPTIONS.forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = settings.fretCount == count,
                            onClick = { viewModel.setFretCount(count) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = FRET_COUNT_OPTIONS.size, baseShape = SmoothShape.Full),
                        ) {
                            Text("$count")
                        }
                    }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LabelMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.labelMode == mode,
                            onClick = { viewModel.setLabelMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = LabelMode.entries.size, baseShape = SmoothShape.Full),
                        ) {
                            Text(if (mode == LabelMode.NoteNames) "Notes" else "Degrees")
                        }
                    }
                }
            }
            SwitchRow(
                headline = "Show outside scale",
                checked = settings.showOutsideScale,
                onCheckedChange = viewModel::setShowOutsideScale,
            )
            FretboardCanvas(
                board = ui.board,
                showLabels = true,
                useNoteNames = settings.labelMode == LabelMode.NoteNames,
                showOutside = settings.showOutsideScale,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp)
                    .pointerInput(ui.board) {
                        detectTapGestures { offset ->
                            val fretCount = ui.board.firstOrNull()?.size?.minus(1)
                                ?: return@detectTapGestures
                            val strings = ui.board.size
                            val pad = 14.sp.toPx()
                            val rowHeight = (size.height - 2 * pad) / strings
                            val col = ((offset.x / size.width) * (fretCount + 1)).toInt().coerceIn(0, fretCount)
                            val row = ((offset.y - pad) / rowHeight).toInt().coerceIn(0, strings - 1)
                            ui.board.getOrNull(row)?.getOrNull(col)?.let(viewModel::playCell)
                        }
                    }
                    .semantics { contentDescription = "Fretboard diagram for $scaleLabel" },
            )
            Text(
                text = ui.tuning.strings.joinToString(" ") { it.name },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}
