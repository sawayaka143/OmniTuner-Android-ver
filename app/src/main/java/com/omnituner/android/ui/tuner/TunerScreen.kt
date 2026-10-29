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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.omnituner.android.ui.common.TopBarContent
import com.omnituner.android.ui.common.TuningHeaderPicker
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.android.ui.theme.accentSwitchColors
import com.omnituner.core.audio.hzDisplay
import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.Tuning
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface TunerDialog {
    data class EditTuning(val draft: TuningDraft) : TunerDialog
    data class EditInstrument(val draft: InstrumentDraft) : TunerDialog
    data object ManageInstruments : TunerDialog
}

private fun newInstrumentDraft(instruments: List<Instrument>) = InstrumentDraft(
    editingId = null,
    name = "",
    stringCount = GUITAR_STANDARD_NOTES.size,
    notes = GUITAR_STANDARD_NOTES,
    baseId = instruments.firstOrNull { it.defaultNotes() == GUITAR_STANDARD_NOTES }?.id,
)

private fun Context.hasRecordPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    // Read only inside the meter's draw pass, so the level redraws the meter without recomposing.
    val inputLevel = viewModel.inputLevel.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    var dialog by remember { mutableStateOf<TunerDialog?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

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

    // Upright, the tuning picker and the Auto switch live in the top bar (in landscape the rail
    // replaces the bar, and the panel carries them itself).
    TopBarContent(
        title = { TuningHeaderPicker(instrument = state.instrument, tuningLabel = state.tuning.label, onClick = { pickerOpen = true }) },
        actions = {
            AutoSwitch(auto = state.mode == TunerMode.Auto, onModeChange = viewModel::selectMode)
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        TunerPanel(
            state = state,
            inputLevel = { inputLevel.value },
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
            onOpenPicker = { pickerOpen = true },
            onSelectString = viewModel::selectString,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 12.dp)
                .weight(1f)
                .fillMaxWidth(),
        )
    }

    if (pickerOpen) {
        TuningPickerSheet(
            state = state,
            onDismiss = { pickerOpen = false },
            onTuningPicked = viewModel::selectTuning,
            onEditTuning = { tuning: Tuning ->
                dialog = TunerDialog.EditTuning(TuningDraft(tuning.id, tuning.label, tuning.midiNotes(), tuning.id))
            },
            onDeleteTuning = viewModel::deleteCustomTuning,
            onNewTuning = {
                dialog = TunerDialog.EditTuning(TuningDraft(null, "", state.tuning.midiNotes(), state.tuning.id))
            },
            onNewInstrument = { dialog = TunerDialog.EditInstrument(newInstrumentDraft(state.instruments)) },
            onManageInstruments = { dialog = TunerDialog.ManageInstruments },
        )
    }

    when (val open = dialog) {
        null -> Unit
        is TunerDialog.EditTuning -> TuningEditor(
            draft = open.draft,
            instrument = state.instrument,
            presets = state.tunings,
            onDismiss = { dialog = null },
            onSave = { name, notes ->
                val error = viewModel.saveCustomTuning(open.draft.editingId, name, notes)
                if (error == null) dialog = null
                error
            },
        )
        is TunerDialog.EditInstrument -> InstrumentEditor(
            draft = open.draft,
            instruments = state.instruments,
            onDismiss = { dialog = null },
            onSave = { name, count, notes ->
                val error = viewModel.saveCustomInstrument(open.draft.editingId, name, count, notes)
                if (error == null) dialog = null
                error
            },
        )
        TunerDialog.ManageInstruments -> CustomInstrumentsPage(
            customInstruments = state.instruments.filter { it.isCustom },
            onDismiss = { dialog = null },
            onEdit = { instrument ->
                dialog = TunerDialog.EditInstrument(
                    InstrumentDraft(
                        editingId = instrument.id,
                        name = instrument.label,
                        stringCount = instrument.stringCount,
                        notes = instrument.defaultNotes(),
                        baseId = instrument.id,
                    ),
                )
            },
            onDelete = viewModel::deleteCustomInstrument,
            onNew = { dialog = TunerDialog.EditInstrument(newInstrumentDraft(state.instruments)) },
        )
    }
}

/** The top-bar trigger for [TuningPickerSheet]: the instrument, and its tuning under it in smaller type. */
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
            .padding(start = 16.dp, end = AutoSwitchEndPadding, top = 8.dp, bottom = 8.dp),
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

// Landscape: where the header's text and the meter's outer labels sit, in from the column beside
// the strings. It is the header picker's own start padding, so the picker needs none added.
private val PanelContentInset = 16.dp
private val AutoSwitchEndPadding = 8.dp

// Landscape: the share of the readout's width each top corner keeps for its control (the tuning
// picker, the Auto switch); the note may grow into everything between them.
private const val WideSideBand = 0.3f

// Landscape: the height the overlaid header takes, kept clear by the full-panel messages.
private val WideHeaderClearance = 56.dp

// Concentric with the panel: inner radius = panel radius - inset, so the menu's press highlight
// follows the panel's corner at an even distance instead of bulging into it.
private val TuningMenuShape = SmoothShape(PanelCorner - PanelHeaderInset)

@Composable
private fun TunerPanel(
    state: TunerUiState,
    inputLevel: () -> Float,
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onModeChange: (TunerMode) -> Unit,
    onOpenPicker: () -> Unit,
    onSelectString: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = animatedTuneColors(state)
    val content = @Composable { wide: Boolean ->
        PanelContent(
            state = state,
            inputLevel = inputLevel,
            colors = colors,
            hasPermission = hasPermission,
            permanentlyDenied = permanentlyDenied,
            onAllow = onAllow,
            onOpenSettings = onOpenSettings,
            onRetry = onRetry,
            wide = wide,
        )
    }
    Surface(
        modifier = modifier,
        shape = SmoothShape(PanelCorner),
        color = colors.container,
        contentColor = colors.content,
    ) {
        BoxWithConstraints {
            if (maxWidth > maxHeight) {
                // Landscape: the strings run down the left edge (highest on top, as on the Scales
                // board); beside them the readout takes the full height, with the tuning button and
                // Auto switch laid over its top corners. A header strip would cost the note its
                // height for two controls that only use the ends; instead each keeps to a side band
                // and the note fills the middle.
                Row(modifier = Modifier.fillMaxSize().padding(PanelHeaderInset)) {
                    StringColumn(state = state, onSelect = onSelectString)
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val sideBand = maxWidth * WideSideBand
                        content(true)
                        // SpaceBetween, not a weighted Spacer: the picker doesn't fill its weighted
                        // share, and a Spacer would leave that unused share after Auto instead of
                        // before it, floating the switch mid-panel.
                        // The end padding tops Auto's own 8 dp up to the picker's 16 dp, so the
                        // header's text and switch sit on the same edges as the meter below.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = PanelContentInset - AutoSwitchEndPadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TuningHeaderPicker(
                                instrument = state.instrument,
                                tuningLabel = state.tuning.label,
                                onClick = onOpenPicker,
                                // Kept to its side band, so a long tuning name ellipsizes before
                                // it can reach under the note.
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .widthIn(max = sideBand),
                            )
                            AutoSwitch(auto = state.mode == TunerMode.Auto, onModeChange = onModeChange)
                        }
                    }
                }
            } else {
                Column {
                    Box(modifier = Modifier.weight(1f)) { content(false) }
                    StringRow(
                        state = state,
                        onSelect = onSelectString,
                        modifier = Modifier.padding(start = PanelHeaderInset, end = PanelHeaderInset, bottom = PanelHeaderInset),
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelContent(
    state: TunerUiState,
    inputLevel: () -> Float,
    colors: TuneColors,
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    wide: Boolean,
) {
    val error = state.error
    // Landscape lays the header over the panel's top, so the full-panel messages start below it.
    val emptyModifier = if (wide) Modifier.padding(top = WideHeaderClearance) else Modifier
    when {
        !hasPermission -> EmptyState(
            modifier = emptyModifier,
            icon = Icons.Filled.MicOff,
            title = "Microphone access needed",
            body = "OmniTuner listens to your instrument to show how far off each string is.",
            action = if (permanentlyDenied) "Open settings" else "Allow",
            onAction = if (permanentlyDenied) onOpenSettings else onAllow,
        )
        error != null -> EmptyState(
            modifier = emptyModifier,
            icon = Icons.Filled.ErrorOutline,
            title = "Can't use the microphone",
            body = error,
            action = "Try again",
            onAction = onRetry,
        )
        else -> Readout(state = state, inputLevel = inputLevel, colors = colors, wide = wide)
    }
}

// The faded note and the "no note" dash share one strength, so idle reads the same either way.
private const val IdleNoteAlpha = 0.35f

@Composable
private fun Readout(state: TunerUiState, inputLevel: () -> Float, colors: TuneColors, wide: Boolean) {
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
    // Manual with no pitch yet: the target string's note waits, faded, where the reading will
    // land, and comes up to full strength when it does.
    val liveLabel = state.noteMidi?.let { midiNoteLabel(it) }
    val waiting = liveLabel == null && state.targetMidi != null
    val noteLabel = liveLabel ?: state.targetMidi?.let { midiNoteLabel(it) }
    val noteAlpha by animateFloatAsState(
        targetValue = when {
            state.holding -> 0.5f
            waiting -> IdleNoteAlpha
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "noteAlpha",
    )
    // Auto with nothing heard: the status line asks for a string instead of sitting blank.
    val prompting = state.mode == TunerMode.Auto && state.capturing && state.cents == null && !state.holding

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (wide) PanelContentInset else 20.dp, vertical = 16.dp),
    ) {
        val meterHeight = min(120.dp, maxHeight * if (wide) 0.22f else 0.28f)
        val typography = MaterialTheme.typography
        val noteSize = with(LocalDensity.current) {
            if (wide) {
                // Landscape is short, so a share of the height left the note ~60 dp tall: unreadable
                // from a stand. It takes all the height the other lines don't need (in dp, so a
                // larger system font can't push it past the meter), and no more width than the
                // middle band the header's corner controls leave free (a note with its octave runs
                // about 1.45 times its size wide, "C#4" being the widest).
                val reserved = meterHeight +
                    typography.headlineSmall.lineHeight.toDp() + // status
                    typography.bodyMedium.lineHeight.toDp() + // frequency
                    (if (state.proAccuracy) typography.titleLarge.lineHeight.toDp() else 0.dp)
                val byHeight = maxHeight - reserved - 8.dp
                val byWidth = maxWidth * (1f - 2 * WideSideBand) / 1.45f
                min(byHeight, byWidth).coerceIn(56.dp, 260.dp).toSp()
            } else {
                (maxHeight * 0.26f).coerceIn(56.dp, 168.dp).toSp()
            }
        }
        // Landscape keeps the octave in proportion to the larger note; upright it stays headlineLarge.
        val octaveSize = if (wide) noteSize * 0.4f else typography.headlineLarge.fontSize

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (wide) Arrangement.Top else Arrangement.SpaceEvenly,
        ) {
            // The note and its readings stay together as one group, so the pitch sits right under it.
            // Landscape: the group centres in the height the status and meter leave above them, as
            // it does upright, between the header's corner controls.
            Column(
                modifier = if (wide) Modifier.weight(1f).wrapContentHeight(Alignment.CenterVertically) else Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NoteName(
                    label = noteLabel,
                    size = noteSize,
                    octaveSize = octaveSize,
                    color = if (waiting) colors.content else colors.note,
                    modifier = Modifier.graphicsLayer {
                        scaleX = noteScale.value
                        scaleY = noteScale.value
                        alpha = noteAlpha
                    },
                )
                // Pro accuracy adds the exact offset; without it the note and needle say enough.
                if (state.proAccuracy) {
                    Text(
                        text = centsLabel(state.cents),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    text = state.frequency?.let { hzDisplay(it) } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
            val meter = @Composable {
                NeedleMeter(
                    needlePercent = needle,
                    needleColor = colors.needle,
                    scaleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cents = state.cents,
                    dimmed = state.holding,
                    inputLevel = inputLevel,
                    modifier = Modifier.height(meterHeight),
                )
            }
            // The status line always holds its one line (blank or not), so the prompt and the
            // readings swap in place without moving the meter.
            val status = @Composable {
                Text(
                    text = if (prompting) "Play a string" else statusLabel(state),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (prompting) FontWeight.Normal else FontWeight.SemiBold,
                    color = when {
                        state.tuned -> colors.note
                        prompting -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> colors.content
                    },
                    textAlign = TextAlign.Center,
                )
            }
            // Landscape: the meter drops to the bottom edge, level with the lowest string, and the
            // status moves up over it.
            if (wide) {
                status()
                meter()
            } else {
                meter()
                status()
            }
        }
    }
}

@Composable
private fun NoteName(
    label: String?,
    size: TextUnit,
    octaveSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
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
            color = if (name == null) LocalContentColor.current.copy(alpha = IdleNoteAlpha) else color,
            modifier = Modifier.alignByBaseline(),
        )
        if (octave.isNotEmpty()) {
            Text(
                text = octave,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = octaveSize, lineHeight = octaveSize),
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

private fun centsLabel(cents: Double?): String {
    if (cents == null || !cents.isFinite()) return ""
    val tenths = (cents * 10).roundToInt()
    val magnitude = "${abs(tenths) / 10}.${abs(tenths) % 10}"
    return when {
        tenths > 0 -> "+$magnitude cents"
        tenths < 0 -> "−$magnitude cents"
        else -> "0.0 cents"
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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

// Upright the row has the width to spare; down the landscape edge six strings must share the
// panel's height, so the circles may come out a little smaller there.
private val StringColumnMin = 40.dp

// The pull-down's own max width, so the unfolded list reads the same as the menu.

@Composable
private fun StringRow(
    state: TunerUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = state.tuning.strings
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = strings.size
        // Circles as large as an even share of the row allows, up to the scroll width; below the
        // minimum the row scrolls instead.
        val share = (maxWidth - StringButtonSpacing * (count - 1)) / count
        val fits = share >= StringButtonMinWidth
        val size = if (fits) share.coerceAtMost(StringButtonScrollWidth) else StringButtonScrollWidth
        Row(
            modifier = if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = if (fits) Arrangement.SpaceBetween else Arrangement.spacedBy(StringButtonSpacing),
        ) {
            strings.forEachIndexed { index, _ -> StringButtonFor(state, index, onSelect, size) }
        }
    }
}

/**
 * Landscape: the strings stood on end down the panel's edge, highest on top. They share the height
 * the way the row shares its width; when they would come out smaller than [StringColumnMin] they
 * split into as few even columns as fit, the highest strings in the first.
 */
@Composable
private fun StringColumn(
    state: TunerUiState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = state.tuning.strings.size
    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        fun share(perColumn: Int) = (maxHeight - StringButtonSpacing * (perColumn - 1)) / perColumn
        val columns = (1..count).firstOrNull { n -> share(ceilDiv(count, n)) >= StringColumnMin } ?: count
        val perColumn = ceilDiv(count, columns)
        val size = share(perColumn).coerceAtMost(StringButtonScrollWidth)
        Row(horizontalArrangement = Arrangement.spacedBy(StringButtonSpacing)) {
            (count - 1 downTo 0).chunked(perColumn).forEach { line ->
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = if (columns == 1) Arrangement.SpaceBetween else Arrangement.spacedBy(StringButtonSpacing),
                ) {
                    line.forEach { index -> StringButtonFor(state, index, onSelect, size) }
                }
            }
        }
    }
}

@Composable
private fun StringButtonFor(state: TunerUiState, index: Int, onSelect: (Int) -> Unit, size: Dp) {
    val string = state.tuning.strings[index]
    val active = state.activeIndex == index
    StringButton(
        label = string.name,
        selected = active,
        tuned = (state.tuned && active) || string.name in state.tunedStrings,
        onClick = { onSelect(index) },
        size = size,
    )
}

private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b

@Composable
private fun StringButton(
    label: String,
    selected: Boolean,
    tuned: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentColors.current
    // On the grey panel the idle strings take the page color (black in dark), like the metronome's
    // in-panel buttons.
    val container by animateColorAsState(
        targetValue = when {
            selected -> accent.accent
            tuned -> accent.accent.copy(alpha = 0.18f).compositeOver(scheme.surface)
            else -> scheme.surface
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
        targetValue = if (selected) 18.dp else size / 2,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "stringCorner",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .size(size)
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
