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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.TunerMode
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.android.ui.theme.accentSwitchColors
import com.omnituner.core.audio.hzDisplay
import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.Tuning
import kotlin.math.roundToInt

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
        viewModel.lockEvents.collect {
            if (viewModel.ui.value.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        TunerPanel(
            state = state,
            hasPermission = hasPermission,
            permanentlyDenied = permanentlyDenied,
            onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
            onRetry = viewModel::startCapture,
            onModeChange = viewModel::selectMode,
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
            modifier = Modifier
                .padding(top = 8.dp)
                .weight(1f)
                .fillMaxWidth(),
        )

        StringRow(
            state = state,
            onSelect = viewModel::selectString,
            modifier = Modifier.padding(vertical = 12.dp),
        )
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

@Composable
private fun TuningMenu(
    state: TunerUiState,
    onTuningPicked: (instrumentId: String, tuningId: String) -> Unit,
    onEditTuning: (Tuning) -> Unit,
    onDeleteTuning: (String) -> Unit,
    onNewTuning: () -> Unit,
    onNewInstrument: () -> Unit,
    onManageInstruments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val close = { expanded = false }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = TuningMenuShape,
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = state.instrument.menuTitle(),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                    Text(
                        text = state.tuning.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose tuning",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = close) {
            state.catalog.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = entry.instrument.menuTitle(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                entry.tunings.forEach { tuning ->
                    val selected = entry.instrument.id == state.instrument.id && tuning.id == state.tuning.id
                    val editable = tuning.isCustom && tuning.id != "${entry.instrument.id}-default"
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tuning.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            close()
                            onTuningPicked(entry.instrument.id, tuning.id)
                        },
                        leadingIcon = {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                        trailingIcon = if (editable) {
                            {
                                Row {
                                    IconButton(onClick = {
                                        close()
                                        onTuningPicked(entry.instrument.id, tuning.id)
                                        onEditTuning(tuning)
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${tuning.label}", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = { onDeleteTuning(tuning.id) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete ${tuning.label}",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("New tuning for ${state.instrument.label}…") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    close()
                    onNewTuning()
                },
            )
            DropdownMenuItem(
                text = { Text("New instrument…") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    close()
                    onNewInstrument()
                },
            )
            if (state.catalog.any { it.instrument.isCustom }) {
                DropdownMenuItem(
                    text = { Text("Manage instruments…") },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        close()
                        onManageInstruments()
                    },
                )
            }
        }
    }
}

private fun Instrument.menuTitle(): String = "$label $stringCount-string"

@Composable
private fun AutoSwitch(auto: Boolean, onModeChange: (TunerMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            // Label and switch are one tap target; clip so its press highlight matches the tuning
            // menu's opposite it instead of drawing a hard-edged rectangle.
            .clip(TuningMenuShape)
            .toggleable(
                value = auto,
                role = Role.Switch,
                onValueChange = { onModeChange(if (it) TunerMode.Auto else TunerMode.Manual) },
            )
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Auto", style = MaterialTheme.typography.labelLarge)
        Switch(checked = auto, onCheckedChange = null, colors = accentSwitchColors())
    }
}

private class TuneColors(val container: Color, val content: Color, val note: Color, val needle: Color)

@Composable
private fun animatedTuneColors(state: TunerUiState): TuneColors {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentColors.current.accent
    val note by animateColorAsState(
        targetValue = if (state.tuned) accent else scheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tuneNote",
    )
    return TuneColors(
        container = scheme.surfaceContainerHigh,
        content = scheme.onSurface,
        note = note,
        needle = accent,
    )
}

private val PanelCorner = 32.dp
private val PanelHeaderInset = 12.dp

// Concentric with the panel: inner radius = panel radius - inset, so the menu's press highlight
// follows the panel's corner at an even distance instead of bulging into it.
private val TuningMenuShape = SmoothShape(PanelCorner - PanelHeaderInset)

@Composable
private fun TunerPanel(
    state: TunerUiState,
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onModeChange: (TunerMode) -> Unit,
    onTuningPicked: (instrumentId: String, tuningId: String) -> Unit,
    onEditTuning: (Tuning) -> Unit,
    onDeleteTuning: (String) -> Unit,
    onNewTuning: () -> Unit,
    onNewInstrument: () -> Unit,
    onManageInstruments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = animatedTuneColors(state)
    Surface(
        modifier = modifier,
        shape = SmoothShape(PanelCorner),
        color = colors.container,
        contentColor = colors.content,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PanelHeaderInset, end = PanelHeaderInset, top = PanelHeaderInset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TuningMenu(
                    state = state,
                    onTuningPicked = onTuningPicked,
                    onEditTuning = onEditTuning,
                    onDeleteTuning = onDeleteTuning,
                    onNewTuning = onNewTuning,
                    onNewInstrument = onNewInstrument,
                    onManageInstruments = onManageInstruments,
                    modifier = Modifier.weight(1f, fill = false),
                )
                AutoSwitch(auto = state.mode == TunerMode.Auto, onModeChange = onModeChange)
            }
            PanelContent(
                state = state,
                colors = colors,
                hasPermission = hasPermission,
                permanentlyDenied = permanentlyDenied,
                onAllow = onAllow,
                onOpenSettings = onOpenSettings,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun PanelContent(
    state: TunerUiState,
    colors: TuneColors,
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val error = state.error
    when {
        !hasPermission -> EmptyState(
            icon = Icons.Filled.MicOff,
            title = "Microphone access needed",
            body = "OmniTuner listens to your instrument to show how far off each string is.",
            action = if (permanentlyDenied) "Open settings" else "Allow",
            onAction = if (permanentlyDenied) onOpenSettings else onAllow,
        )
        error != null -> EmptyState(
            icon = Icons.Filled.ErrorOutline,
            title = "Can't use the microphone",
            body = error,
            action = "Try again",
            onAction = onRetry,
        )
        else -> Readout(state = state, colors = colors)
    }
}

@Composable
private fun Readout(state: TunerUiState, colors: TuneColors) {
    val noteScale = remember { Animatable(1f) }
    LaunchedEffect(state.pulse) {
        if (state.pulse) {
            noteScale.animateTo(1.15f, spring(stiffness = Spring.StiffnessMediumLow))
            noteScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
    }
    val needle by animateFloatAsState(
        targetValue = state.needlePercent,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "needle",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val noteSize = with(LocalDensity.current) { (maxHeight * 0.26f).coerceIn(56.dp, 168.dp).toSp() }
        val meterHeight = min(120.dp, maxHeight * 0.28f)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            NoteName(
                state = state,
                size = noteSize,
                color = colors.note,
                modifier = Modifier.graphicsLayer {
                    scaleX = noteScale.value
                    scaleY = noteScale.value
                },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centsLabel(state.cents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = state.frequency?.let { hzDisplay(it) } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
            NeedleMeter(
                needlePercent = needle,
                needleColor = colors.needle,
                scaleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                glow = state.pulse,
                cents = state.cents,
                modifier = Modifier.height(meterHeight),
            )
            Text(
                text = statusLabel(state),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (state.tuned) colors.note else colors.content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NoteName(state: TunerUiState, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    val label = state.noteMidi?.let { midiNoteLabel(it) }
    val name = label?.takeWhile { it != '-' && !it.isDigit() }
    val octave = if (label != null && name != null) label.drop(name.length) else ""
    val big = MaterialTheme.typography.displayLarge.copy(
        fontSize = size,
        lineHeight = size,
        fontWeight = FontWeight.Bold,
    )
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = name ?: "—",
            style = big,
            color = if (name == null) LocalContentColor.current.copy(alpha = 0.35f) else color,
            modifier = Modifier.alignByBaseline(),
        )
        if (octave.isNotEmpty()) {
            Text(
                text = octave,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

private fun centsLabel(cents: Double?): String {
    if (cents == null || !cents.isFinite()) return ""
    val rounded = cents.roundToInt()
    return when {
        rounded > 0 -> "+$rounded cents"
        rounded < 0 -> "−${-rounded} cents"
        else -> "0 cents"
    }
}

private fun statusLabel(state: TunerUiState): String {
    val cents = state.cents
    return when {
        state.tuned -> "In tune"
        cents == null -> ""
        cents < -state.tolerance -> "Tune up"
        cents > state.tolerance -> "Tune down"
        else -> ""
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(shape = SmoothShape.Full, onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
            Text(action)
        }
    }
}

private val StringButtonMinWidth = 48.dp
private val StringButtonScrollWidth = 56.dp
private val StringButtonSpacing = 8.dp

@Composable
private fun StringRow(
    state: TunerUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = state.tuning.strings
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = strings.size
        val fits = StringButtonMinWidth * count + StringButtonSpacing * (count - 1) <= maxWidth
        Row(
            modifier = if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StringButtonSpacing),
        ) {
            val itemModifier = if (fits) Modifier.weight(1f) else Modifier.width(StringButtonScrollWidth)
            strings.forEachIndexed { index, string ->
                val active = state.activeIndex == index
                StringButton(
                    label = string.name,
                    selected = active,
                    tuned = (state.tuned && active) || string.name in state.tunedStrings,
                    onClick = { onSelect(index) },
                    modifier = itemModifier,
                )
            }
        }
    }
}

@Composable
private fun StringButton(
    label: String,
    selected: Boolean,
    tuned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentColors.current
    val container by animateColorAsState(
        targetValue = when {
            selected -> accent.accent
            tuned -> accent.accent.copy(alpha = 0.18f).compositeOver(scheme.surfaceContainerHighest)
            else -> scheme.surfaceContainerHighest
        },
        label = "stringContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            selected -> accent.onAccent
            tuned -> accent.accent
            else -> scheme.onSurface
        },
        label = "stringContent",
    )
    val corner: Dp by animateDpAsState(
        targetValue = if (selected) 12.dp else 28.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "stringCorner",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .semantics { if (tuned) stateDescription = "In tune" },
        shape = SmoothShape(corner),
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (tuned) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 6.dp)
                        .size(14.dp),
                )
            }
        }
    }
}
