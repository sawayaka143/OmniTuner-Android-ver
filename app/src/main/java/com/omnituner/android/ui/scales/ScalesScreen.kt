package com.omnituner.android.ui.scales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.Accidental
import com.omnituner.android.data.FRET_COUNT_OPTIONS
import com.omnituner.android.data.LabelMode
import com.omnituner.android.ui.common.FretboardCanvas
import com.omnituner.android.ui.common.NoteWheel
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PickerRow
import com.omnituner.android.ui.common.SegmentedRow
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.core.data.FLAT_NAMES
import com.omnituner.core.data.SCALES
import com.omnituner.core.data.SHARP_NAMES
import com.omnituner.core.theory.noteName

@Composable
fun ScalesScreen(viewModel: ScalesViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The map comes first so a scale or root change below shows on the board without scrolling.
            ScaleMapCard(ui = ui, viewModel = viewModel)
            if (wide) {
                // Landscape: the board keeps the full width; the cards under it pair up side by side.
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ScaleCard(ui = ui, viewModel = viewModel)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DisplayCard(ui = ui, viewModel = viewModel)
                        InstrumentCard(ui = ui, viewModel = viewModel)
                    }
                }
            } else {
                ScaleCard(ui = ui, viewModel = viewModel)
                DisplayCard(ui = ui, viewModel = viewModel)
                InstrumentCard(ui = ui, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ScaleMapCard(ui: ScalesUiState, viewModel: ScalesViewModel) {
    val settings = ui.settings
    val flats = settings.accidental == Accidental.Flat
    val scale = SCALES.firstOrNull { it.id == settings.scaleId } ?: SCALES.first()
    val title = "${noteName(settings.rootPitchClass, flats)} ${scale.label}"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${ui.tuning.label} · ${ui.tuning.strings.joinToString(" ") { it.name }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = viewModel::togglePlayScale, shape = SmoothShape.Full) {
                    Icon(
                        imageVector = if (ui.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (ui.isPlaying) "Stop" else "Play")
                }
            }
            FretboardCanvas(
                board = ui.board,
                useNoteNames = settings.labelMode == LabelMode.NoteNames,
                showOutside = settings.showOutsideScale,
                sounding = ui.sounding,
                onCellTap = viewModel::playCell,
                modifier = Modifier.semantics {
                    contentDescription = "Fretboard showing $title, highest string on top"
                },
            )
            ScaleTones(ui = ui, onPlay = viewModel::playTone)
        }
    }
}

// The scale's notes with their degrees, as in the web app's footer: tap one to hear it; the root
// is outlined in the accent and the sounding note fills with it. Always one row (scales have five
// to seven notes), the chips sharing its width, so the card keeps its height from scale to scale.
@Composable
private fun ScaleTones(ui: ScalesUiState, onPlay: (Int) -> Unit) {
    val accent = LocalAccentColors.current
    val scheme = MaterialTheme.colorScheme
    val soundingPc = ui.sounding?.midi?.let { Math.floorMod(it, 12) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (tone in ui.tones) {
            val sounding = tone.pitchClass == soundingPc
            Surface(
                onClick = { onPlay(tone.midi) },
                shape = MaterialTheme.shapes.small,
                color = if (sounding) accent.accent else Color.Transparent,
                contentColor = if (sounding) accent.onAccent else scheme.onSurface,
                border = BorderStroke(1.dp, if (tone.isRoot || sounding) accent.accent else scheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Play ${tone.noteName}, scale degree ${tone.intervalLabel}"
                    },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tone.noteName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = when {
                            sounding -> accent.onAccent
                            tone.isRoot -> accent.accent
                            else -> scheme.onSurface
                        },
                    )
                    Text(
                        text = tone.intervalLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = if (sounding) accent.onAccent else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaleCard(ui: ScalesUiState, viewModel: ScalesViewModel) {
    val settings = ui.settings
    val flats = settings.accidental == Accidental.Flat
    val rootNames = if (flats) FLAT_NAMES else SHARP_NAMES
    val scale = SCALES.firstOrNull { it.id == settings.scaleId } ?: SCALES.first()
    var scaleSheet by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            PickerRow(
                headline = "Scale",
                value = scale.label,
                onClick = { scaleSheet = true },
            )
            RootRow(
                labels = rootNames,
                selected = settings.rootPitchClass,
                onSelect = viewModel::setRootPitchClass,
            )
            SegmentedRow(
                options = Accidental.entries,
                selected = settings.accidental,
                label = { if (it == Accidental.Sharp) "Sharps" else "Flats" },
                onSelect = viewModel::setAccidental,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (scaleSheet) {
        OptionSheet(
            title = "Scale",
            // Each scale spelled out from the current root, so the list says what each name means.
            options = SCALES.map { option ->
                val notes = option.intervals.joinToString(" ") { noteName(settings.rootPitchClass + it.semitones, flats) }
                PickerOption(option.id, option.label, supporting = notes, section = option.group)
            },
            selected = scale.id,
            onSelect = { id ->
                viewModel.setScaleId(id)
                scaleSheet = false
            },
            onDismiss = { scaleSheet = false },
        )
    }
}

// "Root" sits at the start like a list row's headline, but the wheel is centred on the card (and
// so on the screen) rather than on what's left of the row: it keeps the same margin on both sides,
// the label's width plus a gap.
@Composable
private fun RootRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    var labelWidth by remember { mutableStateOf(0.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Root",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onSizeChanged { labelWidth = with(density) { it.width.toDp() } },
        )
        NoteWheel(
            labels = labels,
            selected = selected,
            onSelect = onSelect,
            contentDescription = "Root",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = labelWidth + 12.dp),
            // The root is the accent everywhere on this page: the board, the tone row.
            centerColor = LocalAccentColors.current.accent,
        )
    }
}

@Composable
private fun DisplayCard(ui: ScalesUiState, viewModel: ScalesViewModel) {
    val settings = ui.settings
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SegmentedRow(
                    options = FRET_COUNT_OPTIONS,
                    selected = settings.fretCount,
                    label = { "$it frets" },
                    onSelect = viewModel::setFretCount,
                )
                SegmentedRow(
                    options = LabelMode.entries,
                    selected = settings.labelMode,
                    label = { if (it == LabelMode.NoteNames) "Notes" else "Degrees" },
                    onSelect = viewModel::setLabelMode,
                )
            }
            SwitchRow(
                headline = "Show outside scale",
                checked = settings.showOutsideScale,
                onCheckedChange = viewModel::setShowOutsideScale,
            )
        }
    }
}

@Composable
private fun InstrumentCard(ui: ScalesUiState, viewModel: ScalesViewModel) {
    var instrumentSheet by rememberSaveable { mutableStateOf(false) }
    var tuningSheet by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
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
