package com.omnituner.android.ui.tuner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnituner.android.ui.common.GroupedSection
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.core.data.Tuning
import kotlinx.coroutines.launch

/**
 * The tuning picker, upright: a full-height sheet that slides up over the tuner, laid out as a
 * grouped list. Recent picks first, then New tuning / New instrument / Manage, then every
 * instrument with all its tunings. Instrument names are set larger than the tunings under them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningPickerSheet(
    state: TunerUiState,
    onDismiss: () -> Unit,
    onTuningPicked: (instrumentId: String, tuningId: String) -> Unit,
    onEditTuning: (Tuning) -> Unit,
    onDeleteTuning: (String) -> Unit,
    onNewTuning: () -> Unit,
    onNewInstrument: () -> Unit,
    onManageInstruments: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = LocalGroupedListColors.current

    // Every way out slides the sheet down first, so it never just vanishes.
    fun closeThen(action: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    fun pick(instrumentId: String, tuningId: String) {
        onTuningPicked(instrumentId, tuningId)
        closeThen()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.page,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tunings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = { closeThen() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.group),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterHorizontally)
                .widthIn(max = MaxListWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (state.recent.isNotEmpty()) {
                GroupedSection(title = "Recent") {
                    state.recent.forEach { recent ->
                        val selected = recent.instrument.id == state.instrument.id && recent.tuning.id == state.tuning.id
                        PickerItem(
                            headline = recent.instrument.menuTitle(),
                            supporting = "${recent.tuning.label} · ${recent.tuning.noteNames()}",
                            selected = selected,
                            onClick = { pick(recent.instrument.id, recent.tuning.id) },
                        )
                    }
                }
            }

            GroupedSection(title = "Create") {
                ActionItem(
                    icon = Icons.Filled.Add,
                    headline = "New tuning",
                    supporting = "For ${state.instrument.menuTitle()}",
                    onClick = { closeThen(onNewTuning) },
                )
                ActionItem(icon = Icons.Filled.Add, headline = "New instrument", onClick = { closeThen(onNewInstrument) })
                if (state.catalog.any { it.instrument.isCustom }) {
                    ActionItem(icon = Icons.Filled.Tune, headline = "Manage instruments", onClick = { closeThen(onManageInstruments) })
                }
            }

            Text(
                text = "All instruments",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            state.catalog.forEach { entry ->
                GroupedSection(
                    title = entry.instrument.menuTitle(),
                    titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    titleColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    entry.tunings.forEachIndexed { index, tuning ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        val selected = entry.instrument.id == state.instrument.id && tuning.id == state.tuning.id
                        val editable = tuning.isCustom && tuning.id != "${entry.instrument.id}-default"
                        PickerItem(
                            headline = tuning.label,
                            supporting = tuning.noteNames(),
                            selected = selected,
                            onClick = { pick(entry.instrument.id, tuning.id) },
                            actions = if (editable) {
                                {
                                    IconButton(onClick = {
                                        closeThen {
                                            onTuningPicked(entry.instrument.id, tuning.id)
                                            onEditTuning(tuning)
                                        }
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${tuning.label}", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = { onDeleteTuning(tuning.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${tuning.label}", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerItem(
    headline: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccentColors.current.accent
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(headline, color = if (selected) accent else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions?.invoke()
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = accent)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ActionItem(icon: ImageVector, headline: String, onClick: () -> Unit, supporting: String? = null) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { text -> { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** The open strings low to high without octaves ("E A D G B E"). */
private fun Tuning.noteNames(): String =
    strings.joinToString(" ") { string -> string.name.takeWhile { it != '-' && !it.isDigit() } }

private val MaxListWidth = 640.dp
