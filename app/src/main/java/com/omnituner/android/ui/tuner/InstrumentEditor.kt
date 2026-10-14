package com.omnituner.android.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.core.audio.frequencyToMidiNote
import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.MAX_CUSTOM_INSTRUMENT_NAME_LENGTH
import com.omnituner.core.data.MAX_CUSTOM_TUNING_NAME_LENGTH
import com.omnituner.core.data.MAX_STRING_COUNT
import com.omnituner.core.data.MIN_STRING_COUNT
import com.omnituner.core.data.MIN_TUNER_MIDI_NOTE
import com.omnituner.core.data.MAX_TUNER_MIDI_NOTE
import com.omnituner.core.data.Tuning

data class TuningDraft(
    val editingId: String?,
    val name: String,
    val notes: List<Int>,
)

data class InstrumentDraft(
    val editingId: String?,
    val name: String,
    val stringCount: Int,
    val notes: List<Int>,
)

val GUITAR_STANDARD_NOTES = listOf(40, 45, 50, 55, 59, 64)

fun Tuning.midiNotes(): List<Int> = strings.map { frequencyToMidiNote(it.freq) ?: 69 }

fun Instrument.defaultNotes(): List<Int> = tunings.firstOrNull()?.midiNotes() ?: List(stringCount) { 40 }

private fun List<Int>.shifted(index: Int, delta: Int): List<Int> = toMutableList().also {
    it[index] = (it[index] + delta).coerceIn(MIN_TUNER_MIDI_NOTE, MAX_TUNER_MIDI_NOTE)
}

@Composable
fun TuningEditorDialog(
    draft: TuningDraft,
    presets: List<Tuning>,
    onDismiss: () -> Unit,
    onSave: (String, List<Int>) -> String?,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var error by remember(draft) { mutableStateOf<String?>(null) }
    var presetsOpen by remember { mutableStateOf(false) }
    var presetLabel by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.editingId == null) "New tuning" else "Edit tuning") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_CUSTOM_TUNING_NAME_LENGTH) name = it },
                    label = { Text("Tuning name") },
                    supportingText = { Text("${name.length}/$MAX_CUSTOM_TUNING_NAME_LENGTH") },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { presetsOpen = true }) {
                    Text(presetLabel ?: "Start from preset")
                }
                notes.indices.reversed().forEachIndexed { displayIndex, noteIndex ->
                    NumberStepper(
                        headline = "String ${displayIndex + 1}",
                        valueText = midiNoteLabel(notes[noteIndex]),
                        onDecrement = { notes = notes.shifted(noteIndex, -1) },
                        onIncrement = { notes = notes.shifted(noteIndex, 1) },
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onSave(name.trim(), notes) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (presetsOpen) {
        OptionSheet(
            title = "Start from preset",
            options = presets.map { PickerOption(it.id, it.label) },
            selected = null,
            onSelect = { id ->
                val preset = presets.first { it.id == id }
                presetLabel = preset.label
                notes = preset.midiNotes()
                presetsOpen = false
            },
            onDismiss = { presetsOpen = false },
        )
    }
}

@Composable
fun InstrumentEditorDialog(
    draft: InstrumentDraft,
    onDismiss: () -> Unit,
    onSave: (String, Int, List<Int>) -> String?,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var stringCount by remember(draft) { mutableIntStateOf(draft.stringCount) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var error by remember(draft) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.editingId == null) "New instrument" else "Edit instrument") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_CUSTOM_INSTRUMENT_NAME_LENGTH) name = it },
                    label = { Text("Instrument name") },
                    supportingText = { Text("${name.length}/$MAX_CUSTOM_INSTRUMENT_NAME_LENGTH") },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberStepper(
                    headline = "Strings",
                    valueText = "$stringCount",
                    onDecrement = {
                        if (stringCount > MIN_STRING_COUNT) {
                            stringCount -= 1
                            notes = notes.take(stringCount)
                        }
                    },
                    onIncrement = {
                        if (stringCount < MAX_STRING_COUNT) {
                            val lowest = notes.firstOrNull() ?: MIN_TUNER_MIDI_NOTE
                            stringCount += 1
                            notes = listOf((lowest - 5).coerceAtLeast(MIN_TUNER_MIDI_NOTE)) + notes
                        }
                    },
                )
                notes.indices.reversed().forEachIndexed { displayIndex, noteIndex ->
                    NumberStepper(
                        headline = "String ${displayIndex + 1}",
                        valueText = midiNoteLabel(notes[noteIndex]),
                        onDecrement = { notes = notes.shifted(noteIndex, -1) },
                        onIncrement = { notes = notes.shifted(noteIndex, 1) },
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onSave(name.trim(), stringCount, notes) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun CustomInstrumentsDialog(
    customInstruments: List<Instrument>,
    onDismiss: () -> Unit,
    onEdit: (Instrument) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom instruments") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (customInstruments.isEmpty()) {
                    Text(
                        "No custom instruments yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                customInstruments.forEach { instrument ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(instrument.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${instrument.stringCount} strings",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEdit(instrument) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${instrument.label}", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { onDelete(instrument.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${instrument.label}",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNew) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Text("New instrument")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
