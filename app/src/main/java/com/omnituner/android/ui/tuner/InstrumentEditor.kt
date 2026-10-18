package com.omnituner.android.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.omnituner.android.ui.common.GroupedSection
import com.omnituner.android.ui.common.MenuPickerRow
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.RepeatingIconButton
import com.omnituner.android.ui.theme.LocalDarkTheme
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape
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

/** [baseId] is the preset the editor starts from; the string rows show how far each note moved from it. */
data class TuningDraft(
    val editingId: String?,
    val name: String,
    val notes: List<Int>,
    val baseId: String?,
)

/** [baseId] is the instrument the editor starts from (see [TuningDraft]). */
data class InstrumentDraft(
    val editingId: String?,
    val name: String,
    val stringCount: Int,
    val notes: List<Int>,
    val baseId: String?,
)

val GUITAR_STANDARD_NOTES = listOf(40, 45, 50, 55, 59, 64)

fun Tuning.midiNotes(): List<Int> = strings.map { frequencyToMidiNote(it.freq) ?: 69 }

fun Instrument.defaultNotes(): List<Int> = tunings.firstOrNull()?.midiNotes() ?: List(stringCount) { 40 }

private fun List<Int>.shifted(index: Int, delta: Int): List<Int> = toMutableList().also {
    it[index] = (it[index] + delta).coerceIn(MIN_TUNER_MIDI_NOTE, MAX_TUNER_MIDI_NOTE)
}

@Composable
fun TuningEditor(
    draft: TuningDraft,
    instrument: Instrument,
    presets: List<Tuning>,
    onDismiss: () -> Unit,
    onSave: (String, List<Int>) -> String?,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var baseId by remember(draft) { mutableStateOf(draft.baseId) }
    var error by remember(draft) { mutableStateOf<String?>(null) }
    val base = presets.firstOrNull { it.id == baseId }

    FullScreenEditor(
        title = if (draft.editingId == null) "New tuning" else "Edit tuning",
        saveEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = { error = onSave(name.trim(), notes) },
        details = {
            NameSection(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                placeholder = "Tuning name",
                maxLength = MAX_CUSTOM_TUNING_NAME_LENGTH,
                error = error,
            )
            GroupedSection(title = "Start from") {
                MenuPickerRow(
                    headline = "Preset",
                    options = presets.map { PickerOption<String?>(it.id, it.label) },
                    selected = baseId,
                    onSelect = { id ->
                        presets.firstOrNull { it.id == id }?.let { preset ->
                            baseId = preset.id
                            notes = preset.midiNotes()
                        }
                    },
                    menuColor = LocalGroupedListColors.current.group,
                )
            }
        },
        strings = {
            GroupedSection(title = instrument.menuTitle()) {
                StringRows(notes = notes, baseNotes = base?.midiNotes(), onChange = { notes = it })
            }
        },
    )
}

@Composable
fun InstrumentEditor(
    draft: InstrumentDraft,
    instruments: List<Instrument>,
    onDismiss: () -> Unit,
    onSave: (String, Int, List<Int>) -> String?,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var stringCount by remember(draft) { mutableIntStateOf(draft.stringCount) }
    var notes by remember(draft) { mutableStateOf(draft.notes) }
    var baseId by remember(draft) { mutableStateOf(draft.baseId) }
    var error by remember(draft) { mutableStateOf<String?>(null) }
    val base = instruments.firstOrNull { it.id == baseId }

    FullScreenEditor(
        title = if (draft.editingId == null) "New instrument" else "Edit instrument",
        saveEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = { error = onSave(name.trim(), stringCount, notes) },
        details = {
            NameSection(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                placeholder = "Instrument name",
                maxLength = MAX_CUSTOM_INSTRUMENT_NAME_LENGTH,
                error = error,
            )
            GroupedSection(title = "Start from") {
                MenuPickerRow(
                    headline = "Instrument",
                    options = instruments.map { PickerOption<String?>(it.id, it.menuTitle()) },
                    selected = baseId,
                    onSelect = { id ->
                        instruments.firstOrNull { it.id == id }?.let { instrument ->
                            baseId = instrument.id
                            stringCount = instrument.stringCount
                            notes = instrument.defaultNotes()
                        }
                    },
                    menuColor = LocalGroupedListColors.current.group,
                )
            }
        },
        strings = {
            GroupedSection(title = "Strings") {
                // Strings come and go at the low end (a 7-string adds a low string under a 6-string's),
                // so the existing strings keep their numbers and notes.
                NumberStepper(
                    headline = "Number of strings",
                    valueText = "$stringCount",
                    onDecrement = {
                        if (stringCount > MIN_STRING_COUNT) {
                            stringCount -= 1
                            notes = notes.drop(1)
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
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                StringRows(notes = notes, baseNotes = base?.defaultNotes(), onChange = { notes = it })
            }
        },
    )
}

@Composable
fun CustomInstrumentsPage(
    customInstruments: List<Instrument>,
    onDismiss: () -> Unit,
    onEdit: (Instrument) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
) {
    FullScreenEditor(title = "Custom instruments", onDismiss = onDismiss) {
        GroupedSection(title = "Instruments") {
            if (customInstruments.isEmpty()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text("No custom instruments yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
            customInstruments.forEach { instrument ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(instrument.label) },
                    supportingContent = { Text("${instrument.stringCount} strings") },
                    trailingContent = {
                        Row {
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
                    },
                    modifier = Modifier.clickable { onEdit(instrument) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                headlineContent = { Text("New instrument") },
                modifier = Modifier.clickable(onClick = onNew),
            )
        }
    }
}

/**
 * A full-screen editor (M3 full-screen dialog) on the grouped-list page: ✕ to discard, Save in the
 * top bar. Portrait stacks [details] over [strings]; landscape puts them side by side, each column
 * scrolling on its own so the string list stays beside the name.
 */
@Composable
private fun FullScreenEditor(
    title: String,
    saveEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    strings: @Composable ColumnScope.() -> Unit,
) {
    EditorDialog(
        title = title,
        onDismiss = onDismiss,
        action = {
            FilledTonalButton(
                onClick = onSave,
                enabled = saveEnabled,
                shape = SmoothShape.Full,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = LocalGroupedListColors.current.group,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("Save") }
        },
    ) { wide ->
        if (wide) {
            Row(
                modifier = Modifier.widthIn(max = MaxWideWidth).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorColumn(Modifier.weight(1f), details)
                EditorColumn(Modifier.weight(1f), strings)
            }
        } else {
            EditorColumn(Modifier.widthIn(max = MaxListWidth).fillMaxSize()) {
                details()
                strings()
            }
        }
    }
}

/** A full-screen page with no Save action (the custom instruments list). */
@Composable
private fun FullScreenEditor(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    EditorDialog(title = title, onDismiss = onDismiss, action = {}) {
        EditorColumn(Modifier.widthIn(max = MaxListWidth).fillMaxSize(), content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorDialog(
    title: String,
    onDismiss: () -> Unit,
    action: @Composable () -> Unit,
    content: @Composable (wide: Boolean) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The dialog has a window of its own, so its system bar icons need the theme's contrast too.
        val view = LocalView.current
        val dark = LocalDarkTheme.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        val pageColor = LocalGroupedListColors.current.page
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = pageColor,
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime),
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = pageColor),
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = LocalGroupedListColors.current.group,
                            ),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = { action() },
                )
            },
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                content(maxWidth > maxHeight)
            }
        }
    }
}

@Composable
private fun EditorColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        content = content,
    )
}

private val MaxListWidth = 640.dp
private val MaxWideWidth = 1000.dp

@Composable
private fun NameSection(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    error: String?,
) {
    GroupedSection(title = "Name") {
        TextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            placeholder = { Text(placeholder) },
            // The counter and the save error share this line, so an error never adds a row.
            supportingText = {
                if (error != null) {
                    Text(error)
                } else {
                    Text("${value.length}/$maxLength", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
            },
            isError = error != null,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One row per string, highest (string 1) on top. [baseNotes] is the starting preset: rows whose note
 * moved from it show the change in semitones. Both lists are lowest-first and are matched from the
 * top string down, so strings added at the low end simply have nothing to compare with.
 */
@Composable
private fun StringRows(notes: List<Int>, baseNotes: List<Int>?, onChange: (List<Int>) -> Unit) {
    notes.indices.reversed().forEachIndexed { fromTop, noteIndex ->
        val baseNote = baseNotes?.getOrNull(baseNotes.lastIndex - fromTop)
        StringRow(
            number = fromTop + 1,
            note = notes[noteIndex],
            change = baseNote?.let { notes[noteIndex] - it } ?: 0,
            onStep = { delta -> onChange(notes.shifted(noteIndex, delta)) },
        )
    }
}

@Composable
private fun StringRow(number: Int, note: Int, change: Int, onStep: (Int) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(LocalGroupedListColors.current.page, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.labelLarge)
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(midiNoteLabel(note), style = MaterialTheme.typography.titleLarge)
                if (change != 0) {
                    Text(
                        text = if (change > 0) "+$change" else "−${-change}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RepeatingIconButton(label = "Lower string $number", onStep = { onStep(-1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                RepeatingIconButton(label = "Raise string $number", onStep = { onStep(1) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
    )
}
