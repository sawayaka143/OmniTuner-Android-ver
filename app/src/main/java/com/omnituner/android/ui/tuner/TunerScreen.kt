package com.omnituner.android.ui.tuner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.TunerMode
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PickerRow
import com.omnituner.core.audio.hzDisplay
import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.audio.tuneCentsText
import com.omnituner.core.audio.tuneColorProgress
import com.omnituner.core.audio.tuneDirectionText
import com.omnituner.core.data.Tuning
import kotlin.math.abs

private sealed interface TunerDialog {
    data class EditTuning(val draft: TuningDraft) : TunerDialog
    data class EditInstrument(val draft: InstrumentDraft) : TunerDialog
    data object ManageInstruments : TunerDialog
}

private fun newInstrumentDraft() = InstrumentDraft(
    editingId = null,
    name = "",
    stringCount = GUITAR_STANDARD_NOTES.size,
    notes = GUITAR_STANDARD_NOTES,
)

private fun Context.hasRecordPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    var dialog by remember { mutableStateOf<TunerDialog?>(null) }

    DisposableEffect(state.capturing) {
        view.keepScreenOn = state.capturing
        onDispose { view.keepScreenOn = false }
    }

    var hasPermission by remember { mutableStateOf(context.hasRecordPermission()) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.startCapture()
        } else {
            permanentlyDenied = (context as? ComponentActivity)
                ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.startCapture()
        } else if (!requestedOnce) {
            requestedOnce = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (context.hasRecordPermission()) {
                    hasPermission = true
                    viewModel.startCapture()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.stopCapture()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopCapture()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.lockEvents.collect { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TuningCard(
            state = state,
            onModeChange = viewModel::selectMode,
            onInstrumentPicked = viewModel::selectInstrument,
            onTuningPicked = viewModel::selectTuning,
            onEditTuning = { tuning: Tuning ->
                dialog = TunerDialog.EditTuning(TuningDraft(tuning.id, tuning.label, tuning.midiNotes()))
            },
            onDeleteTuning = viewModel::deleteCustomTuning,
            onNewTuning = {
                dialog = TunerDialog.EditTuning(TuningDraft(null, "", state.tuning.midiNotes()))
            },
            onNewInstrument = { dialog = TunerDialog.EditInstrument(newInstrumentDraft()) },
            onManageInstruments = { dialog = TunerDialog.ManageInstruments },
        )

        MeterCard(state = state, onSelectString = viewModel::selectString)

        if (!hasPermission) {
            MicPermissionCard(
                permanentDenial = permanentlyDenied,
                onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (val open = dialog) {
        null -> Unit
        is TunerDialog.EditTuning -> TuningEditorDialog(
            draft = open.draft,
            presets = state.tunings,
            onDismiss = { dialog = null },
            onSave = { name, notes ->
                val error = viewModel.saveCustomTuning(open.draft.editingId, name, notes)
                if (error == null) dialog = null
                error
            },
        )
        is TunerDialog.EditInstrument -> InstrumentEditorDialog(
            draft = open.draft,
            onDismiss = { dialog = null },
            onSave = { name, count, notes ->
                val error = viewModel.saveCustomInstrument(open.draft.editingId, name, count, notes)
                if (error == null) dialog = null
                error
            },
        )
        TunerDialog.ManageInstruments -> CustomInstrumentsDialog(
            customInstruments = state.instruments.filter { it.isCustom },
            onDismiss = { dialog = null },
            onEdit = { instrument ->
                dialog = TunerDialog.EditInstrument(
                    InstrumentDraft(instrument.id, instrument.label, instrument.stringCount, instrument.defaultNotes()),
                )
            },
            onDelete = viewModel::deleteCustomInstrument,
            onNew = { dialog = TunerDialog.EditInstrument(newInstrumentDraft()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningCard(
    state: TunerUiState,
    onModeChange: (TunerMode) -> Unit,
    onInstrumentPicked: (String) -> Unit,
    onTuningPicked: (String) -> Unit,
    onEditTuning: (Tuning) -> Unit,
    onDeleteTuning: (String) -> Unit,
    onNewTuning: () -> Unit,
    onNewInstrument: () -> Unit,
    onManageInstruments: () -> Unit,
) {
    var instrumentSheet by rememberSaveable { mutableStateOf(false) }
    var tuningSheet by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Current tuning",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = state.tuning.label, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "${state.instrument.label} · ${state.tuning.strings.joinToString(" ") { it.name }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = TunerMode.entries
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) {
                            Text(if (mode == TunerMode.Auto) "Auto detect" else "Manual")
                        }
                    }
                }
            }
            PickerRow(
                headline = "Instrument",
                value = state.instrument.label,
                onClick = { instrumentSheet = true },
            )
            PickerRow(
                headline = "Tuning",
                value = state.tuning.label,
                onClick = { tuningSheet = true },
            )
        }
    }

    if (instrumentSheet) {
        OptionSheet(
            title = "Instrument",
            options = state.instruments.map { PickerOption(it.id, it.label) },
            selected = state.instrument.id,
            onSelect = { id ->
                onInstrumentPicked(id)
                instrumentSheet = false
            },
            onDismiss = { instrumentSheet = false },
            footer = {
                TextButton(onClick = {
                    instrumentSheet = false
                    onNewInstrument()
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New instrument")
                }
                TextButton(onClick = {
                    instrumentSheet = false
                    onManageInstruments()
                }) {
                    Text("Manage instruments")
                }
            },
        )
    }

    if (tuningSheet) {
        OptionSheet(
            title = "Tuning",
            options = state.tunings.map { PickerOption(it.id, it.label) },
            selected = state.tuning.id,
            onSelect = { id ->
                onTuningPicked(id)
                tuningSheet = false
            },
            onDismiss = { tuningSheet = false },
            trailing = { id ->
                val tuning = state.tunings.firstOrNull { it.id == id }
                if (tuning != null && tuning.isCustom) {
                    IconButton(onClick = {
                        tuningSheet = false
                        onEditTuning(tuning)
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${tuning.label}")
                    }
                    IconButton(onClick = { onDeleteTuning(tuning.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete ${tuning.label}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            footer = {
                TextButton(onClick = {
                    tuningSheet = false
                    onNewTuning()
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New tuning")
                }
            },
        )
    }
}

@Composable
private fun MeterCard(state: TunerUiState, onSelectString: (Int) -> Unit) {
    val color = needleColor(state)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TunePrompt(state = state, color = color)
            NeedleMeter(
                needlePercent = state.needlePercent,
                needleColor = color,
                glow = state.pulse,
                cents = state.cents,
            )
            NoteReadout(state = state, color = color)
            StringChips(state = state, onSelect = onSelectString)
        }
    }
}

@Composable
private fun TunePrompt(state: TunerUiState, color: Color) {
    val tolerance = state.tolerance.toDouble()
    val promptColor = if (state.tuned) MaterialTheme.colorScheme.primary else color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = tuneDirectionText(state.cents, tolerance),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = promptColor,
            maxLines = 1,
        )
        Text(
            text = tuneCentsText(state.cents, tolerance),
            style = MaterialTheme.typography.labelLarge,
            color = if (state.tuned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun NoteReadout(state: TunerUiState, color: Color) {
    val label = state.noteMidi?.let { midiNoteLabel(it) }
    val name = label?.takeWhile { it != '-' && !it.isDigit() } ?: "—"
    val octave = label?.drop(name.length) ?: ""
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = name,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = octave,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(
            text = hzDisplay(state.frequency),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = statusText(state.status),
            style = MaterialTheme.typography.labelLarge,
            color = if (state.tuned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun statusText(status: TuneStatus): String = when (status) {
    TuneStatus.Idle -> "IDLE"
    TuneStatus.Listening -> "LISTENING FOR A NOTE"
    is TuneStatus.Adjusting -> "TUNING ${status.stringName}"
    TuneStatus.InTune -> "IN TUNE"
}

@Composable
private fun needleColor(state: TunerUiState): Color {
    val scheme = MaterialTheme.colorScheme
    val cents = state.cents
    return when {
        state.tuned -> scheme.primary
        cents == null || abs(cents) <= state.tolerance -> scheme.onSurface
        else -> lerp(
            scheme.error,
            scheme.primary,
            tuneColorProgress(cents, state.tolerance.toDouble()).toFloat(),
        )
    }
}

private val CheckIcon: @Composable () -> Unit = {
    Icon(
        imageVector = Icons.Filled.Check,
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StringChips(state: TunerUiState, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.tuning.strings.forEachIndexed { index, string ->
            val active = state.activeIndex == index
            val tuned = (state.tuned && active) || string.name in state.tunedStrings
            FilterChip(
                selected = active,
                onClick = { onSelect(index) },
                label = { Text(string.name) },
                leadingIcon = if (tuned) CheckIcon else null,
            )
        }
    }
}

@Composable
private fun MicPermissionCard(
    permanentDenial: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.MicOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "Microphone needed to tune",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            TextButton(onClick = if (permanentDenial) onOpenSettings else onAllow) {
                Text(if (permanentDenial) "Open settings" else "Allow")
            }
        }
    }
}
