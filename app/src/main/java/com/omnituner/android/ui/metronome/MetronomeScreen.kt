package com.omnituner.android.ui.metronome

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.ui.common.GroupedSection
import com.omnituner.android.ui.common.MenuPickerRow
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.PullDownMenu
import com.omnituner.android.ui.common.PullDownMenuDivider
import com.omnituner.android.ui.common.PullDownMenuItem
import com.omnituner.android.ui.common.RepeatingIconButton
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape
import com.omnituner.android.ui.theme.accentSliderColors
import com.omnituner.core.metronome.BPM_MAX
import com.omnituner.core.metronome.BPM_MIN
import com.omnituner.core.metronome.DENOMINATORS
import com.omnituner.core.metronome.DIVISIONS_MAX
import com.omnituner.core.metronome.DIVISIONS_MIN
import com.omnituner.core.metronome.METER_PRESETS
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.metronome.PATTERN_MAX_BARS
import com.omnituner.core.metronome.PATTERN_PRESETS
import com.omnituner.core.metronome.PRESETS_MAX
import com.omnituner.core.metronome.SUBDIVISIONS
import com.omnituner.core.metronome.TimeSignature
import com.omnituner.core.sound.MetronomeVoices
import com.omnituner.core.timing.getTempoMarking
import com.omnituner.core.timing.meterModel
import com.omnituner.core.timing.tapBpm
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun MetronomeScreen(viewModel: MetronomeViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsState()
    val tapTempo = rememberTapTempo(viewModel::setBpm)
    val view = LocalView.current
    var savingPreset by rememberSaveable { mutableStateOf(false) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }

    // A running metronome is usually being played along to, not looked at, so keep the screen up.
    DisposableEffect(ui.isPlaying) {
        view.keepScreenOn = ui.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        TempoPanel(
            ui = ui,
            viewModel = viewModel,
            onTap = tapTempo,
            onOptions = { optionsOpen = true },
            onSavePreset = { savingPreset = true },
            modifier = Modifier
                .padding(top = 8.dp, bottom = 12.dp)
                .weight(1f)
                .fillMaxWidth(),
        )
    }

    if (optionsOpen) {
        OptionsSheet(metronome = ui.state, viewModel = viewModel, onDismiss = { optionsOpen = false })
    }

    if (savingPreset) {
        SavePresetDialog(
            onDismiss = { savingPreset = false },
            onSave = { name ->
                viewModel.savePreset(name)
                savingPreset = false
            },
        )
    }
}

private val PanelCorner = 32.dp
private val PanelHeaderInset = 12.dp

// Concentric with the panel, as on the tuner: inner radius = panel radius - inset.
private val HeaderMenuShape = SmoothShape(PanelCorner - PanelHeaderInset)

@Composable
private fun TempoPanel(
    ui: MetronomeUiState,
    viewModel: MetronomeViewModel,
    onTap: () -> Unit,
    onOptions: () -> Unit,
    onSavePreset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The wheel unfolds while it is being turned and folds back into a plain readout once the tempo
    // has been left alone for a moment, or right away when the metronome starts. Any tempo change
    // (steppers, tap, a preset, typing) restarts the wait without unfolding it.
    var wheelOpen by remember { mutableStateOf(false) }
    var wheelBusy by remember { mutableStateOf(false) }
    val bpm = ui.state.bpm.roundToInt()
    LaunchedEffect(wheelOpen, wheelBusy, bpm) {
        if (wheelOpen && !wheelBusy) {
            delay(WheelIdleMs)
            wheelOpen = false
        }
    }
    val fold = animateFloatAsState(
        targetValue = if (wheelOpen) 0f else 1f,
        animationSpec = FoldSpring,
        label = "wheelFold",
    )

    Surface(
        modifier = modifier,
        shape = SmoothShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PanelHeaderInset, end = PanelHeaderInset, top = PanelHeaderInset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeterMenu(
                    metronome = ui.state,
                    onPick = { viewModel.setTimeSignature(it.numerator, it.denominator) },
                    modifier = Modifier.weight(1f, fill = false),
                )
                PresetMenu(
                    ui = ui,
                    onApply = viewModel::applyPreset,
                    onDelete = viewModel::deletePreset,
                    onSave = onSavePreset,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            TempoReadout(
                ui = ui,
                viewModel = viewModel,
                fold = { fold.value },
                onWheelInteraction = { active ->
                    if (active) wheelOpen = true
                    wheelBusy = active
                },
                onTap = onTap,
                modifier = Modifier.weight(1f),
            )
            TransportRow(
                isPlaying = ui.isPlaying,
                onToggle = {
                    if (!ui.isPlaying) wheelOpen = false
                    viewModel.toggle()
                },
                onOptions = onOptions,
                modifier = Modifier.padding(start = PanelHeaderInset, end = PanelHeaderInset, bottom = PanelHeaderInset),
            )
        }
    }
}

private const val WheelIdleMs = 2500L
private val FoldSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/** The panel-header trigger shared by both menus: a small caption over a large value. */
@Composable
private fun HeaderMenuTrigger(
    caption: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    alignEnd: Boolean = false,
) {
    Surface(onClick = onClick, shape = HeaderMenuShape, color = Color.Transparent) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                    maxLines = 1,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MeterMenu(
    metronome: MetronomeState,
    onPick: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meter = metronome.timeSignature
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        HeaderMenuTrigger(
            caption = subdivisionLabel(metronome.divisionsPerBeat),
            value = "${meter.numerator}/${meter.denominator}",
            expanded = expanded,
            onClick = { expanded = true },
            contentDescription = "Choose meter",
        )
        PullDownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            METER_PRESETS.forEach { preset ->
                PullDownMenuItem(
                    text = "${preset.numerator}/${preset.denominator}",
                    selected = preset == meter,
                    supporting = meterFeel(preset.denominator),
                    onClick = {
                        expanded = false
                        onPick(preset)
                    },
                )
            }
        }
    }
}

@Composable
private fun PresetMenu(
    ui: MetronomeUiState,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val close = { expanded = false }
    val current = ui.presets.firstOrNull { it.state == ui.state }
    Box(modifier = modifier) {
        HeaderMenuTrigger(
            caption = "Preset",
            value = current?.name ?: if (ui.presets.isEmpty()) "None" else "Custom",
            expanded = expanded,
            onClick = { expanded = true },
            contentDescription = "Choose preset",
            alignEnd = true,
        )
        PullDownMenu(expanded = expanded, onDismissRequest = close) {
            ui.presets.forEach { preset ->
                PullDownMenuItem(
                    text = preset.name,
                    selected = preset.id == current?.id,
                    supporting = presetSummary(preset.state),
                    trailing = {
                        IconButton(onClick = { onDelete(preset.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete preset ${preset.name}",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    onClick = {
                        close()
                        onApply(preset.id)
                    },
                )
            }
            if (ui.presets.isNotEmpty()) PullDownMenuDivider()
            PullDownMenuItem(
                text = "Save current…",
                icon = Icons.Filled.Add,
                supporting = if (ui.presets.size >= PRESETS_MAX) "Replaces the oldest" else null,
                onClick = {
                    close()
                    onSave()
                },
            )
        }
    }
}

@Composable
private fun TempoReadout(
    ui: MetronomeUiState,
    viewModel: MetronomeViewModel,
    fold: () -> Float,
    onWheelInteraction: (Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metronome = ui.state
    val beats = meterModel(metronome.timeSignature.numerator, metronome.timeSignature.denominator).beatsPerBar
    val currentBeat = if (ui.isPlaying) floor(ui.progress * beats).toInt().coerceIn(0, beats - 1) else null

    // A small kick on every beat, the metronome's counterpart of the tuner's in-tune note pop.
    val bpmScale = remember { Animatable(1f) }
    LaunchedEffect(ui.isPlaying, ui.barIndex, currentBeat) {
        if (currentBeat != null) {
            bpmScale.snapTo(if (currentBeat == 0) 1.06f else 1.03f)
            bpmScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    var typingTempo by rememberSaveable { mutableStateOf(false) }

    // The value lights in the accent while the metronome runs, like the tuner's in-tune note.
    val numberColor by animateColorAsState(
        targetValue = if (ui.isPlaying) LocalAccentColors.current.accent else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bpmColor",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ReadoutInset, vertical = 16.dp),
    ) {
        // Three digits must fit between the steppers (also once grown by CompactScale when folded),
        // and the drum needs room for rows around them.
        val bpmSize = min(maxHeight * 0.2f, (maxWidth - StepperSlot * 2) / 1.9f).coerceIn(48.dp, 144.dp) * NumberShare
        val rowPitch = (bpmSize * 0.36f).coerceIn(24.dp, 38.dp)
        val dotsHeight = min(32.dp, maxHeight * 0.08f)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BeatDots(
                beats = beats,
                current = currentBeat,
                countIn = ui.inCountIn,
                barActive = ui.barActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(dotsHeight),
            )
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = {
                    Box(contentAlignment = Alignment.Center) {
                        // Selection band behind the centre row; the steppers sit at its two ends.
                        // It goes with the neighbours when the wheel folds.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bpmSize + 20.dp)
                                .graphicsLayer { alpha = 1f - fold() }
                                .background(MaterialTheme.colorScheme.surface, BandShape),
                        )
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.width(StepperSlot), contentAlignment = Alignment.Center) {
                                RepeatingIconButton(label = "Decrease tempo", onStep = { viewModel.changeBpm(-1) }) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            BpmWheel(
                                value = metronome.bpm.roundToInt(),
                                range = BPM_MIN..BPM_MAX,
                                onValueChange = { viewModel.setBpm(it.toDouble()) },
                                onCenterClick = { typingTempo = true },
                                centerSize = bpmSize,
                                pitch = rowPitch,
                                centerScale = { bpmScale.value },
                                collapse = fold,
                                onInteractionChange = onWheelInteraction,
                                centerColor = numberColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            Box(modifier = Modifier.width(StepperSlot), contentAlignment = Alignment.Center) {
                                RepeatingIconButton(label = "Increase tempo", onStep = { viewModel.changeBpm(1) }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "BPM · ${getTempoMarking(metronome.bpm)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                        Text(
                            text = statusLabel(ui),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // Rides with the readout as it folds; tapping only sets the tempo, it never
                        // unfolds the wheel.
                        TapButton(onTap = onTap, modifier = Modifier.padding(top = 8.dp))
                    }
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val area = constraints.maxHeight
                val caption = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
                val gap = CaptionGap.roundToPx()
                // The drum is bent onto the wheel's height, so this also sets how many neighbours show.
                val wheelHeight = ((area - caption.height - gap) * WheelHeightShare).roundToInt().coerceAtLeast(0)
                val wheel = measurables[0].measure(Constraints.fixed(width, wheelHeight))
                layout(width, area) {
                    val f = fold()
                    val folded = bpmSize.toPx() * CompactScale
                    // Open, the wheel and caption are centred together. Folded, the value rises by
                    // part of the room its neighbours gave up and the caption follows it up.
                    val openCenter = (area - caption.height - gap) / 2f
                    val foldedCenter = openCenter - (wheelHeight - folded) * FoldLift
                    val center = lerp(openCenter, foldedCenter, f)
                    val captionTop = lerp(openCenter + wheelHeight / 2f, foldedCenter + folded / 2f, f) + gap
                    wheel.place(0, (center - wheelHeight / 2f).roundToInt())
                    caption.place((width - caption.width) / 2, captionTop.roundToInt())
                }
            }
        }
    }

    if (typingTempo) {
        TempoEntryDialog(
            current = metronome.bpm.roundToInt(),
            onDismiss = { typingTempo = false },
            onSet = { bpm ->
                viewModel.setBpm(bpm.toDouble())
                typingTempo = false
            },
        )
    }
}

private val ReadoutInset = 8.dp
private val StepperSlot = 56.dp

// Share of the space between the beat dots and the caption the wheel spans; it bends its rows onto
// a drum that tall, so this also sets how many neighbours show.
private const val WheelHeightShare = 0.56f

// The centre value's size relative to what fits (leaves room for its CompactScale growth).
private const val NumberShare = 0.85f
private val CaptionGap = 8.dp

// How far the folded value rises, as a share of the wheel height its neighbours gave up.
private const val FoldLift = 0.5f

// Concentric with the panel corner at the readout's inset.
private val BandShape = SmoothShape(PanelCorner - ReadoutInset)

@Composable
private fun TempoEntryDialog(current: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("$current", selection = TextRange(0, "$current".length)))
    }
    val focus = remember { FocusRequester() }
    val bpm = text.text.toIntOrNull()?.takeIf { it in BPM_MIN..BPM_MAX }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tempo") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (new.text.length <= BPM_MAX.toString().length && new.text.all(Char::isDigit)) text = new
                },
                label = { Text("BPM") },
                singleLine = true,
                isError = text.text.isNotEmpty() && bpm == null,
                supportingText = { Text("$BPM_MIN–$BPM_MAX") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { bpm?.let(onSet) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            Button(shape = SmoothShape.Full, enabled = bpm != null, onClick = { bpm?.let(onSet) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(shape = SmoothShape.Full, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val BeatDotMax = 28.dp
private val BeatDotSpacing = 8.dp

/**
 * One dot per beat of the bar; the sounding beat lights in the accent (the needle's counterpart).
 * The downbeat dot is drawn full size and the others smaller so bar starts read at a glance.
 * Count-in and muted bars light neutral, since nothing is "in time" to play along to yet.
 */
@Composable
private fun BeatDots(
    beats: Int,
    current: Int?,
    countIn: Boolean,
    barActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = LocalAccentColors.current.accent
    val lit = if (countIn || !barActive) scheme.onSurface.copy(alpha = 0.55f) else accent
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val fit = (maxWidth - BeatDotSpacing * (beats - 1)) / beats
        val dot = minOf(BeatDotMax, maxHeight, fit).coerceAtLeast(4.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(BeatDotSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(beats) { index ->
                val on = index == current
                val color by animateColorAsState(
                    targetValue = if (on) lit else scheme.onSurface.copy(alpha = if (index == 0) 0.28f else 0.14f),
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "beatDot",
                )
                val size by animateDpAsState(
                    targetValue = if (index == 0 || on) dot else dot * 0.7f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "beatDotSize",
                )
                Box(modifier = Modifier.size(dot), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .background(color, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccentColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onToggle,
            shape = SmoothShape.Full,
            colors = ButtonDefaults.buttonColors(containerColor = accent.accent, contentColor = accent.onAccent),
            modifier = Modifier
                .weight(1f)
                .height(TransportHeight)
                .semantics { contentDescription = if (isPlaying) "Stop metronome" else "Start metronome" },
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isPlaying) "Stop" else "Start",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Icon only: the row is too narrow inside the panel for a "More" label.
        FilledTonalButton(
            onClick = onOptions,
            shape = SmoothShape.Full,
            colors = panelTonalColors(),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .width(MoreButtonWidth)
                .height(TransportHeight),
        ) {
            Icon(Icons.Filled.Tune, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val TransportHeight = 56.dp
private val MoreButtonWidth = 72.dp

// The panel is the tonal grey itself, so tonal buttons on it take the page color to stand out.
@Composable
private fun panelTonalColors() =
    ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface)

/** Tap tempo; it flashes the accent while pressed so each tap registers visibly. */
@Composable
private fun TapButton(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = LocalAccentColors.current
    val base = panelTonalColors()
    val container by animateColorAsState(
        targetValue = if (pressed) accent.accent else base.containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tapContainer",
    )
    val content by animateColorAsState(
        targetValue = if (pressed) accent.onAccent else base.contentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tapContent",
    )
    FilledTonalButton(
        onClick = onTap,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content),
        interactionSource = interaction,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(TapButtonSize),
    ) {
        Text("Tap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private val TapButtonSize = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(metronome: MetronomeState, viewModel: MetronomeViewModel, onDismiss: () -> Unit) {
    // Laid out like Settings: an inset grouped list on the grouped-list page color.
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalGroupedListColors.current.page) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            GroupedSection("Meter") { MeterOptions(metronome, viewModel) }
            GroupedSection("Bar mute pattern") { PatternOptions(metronome, viewModel) }
            GroupedSection("Polyrhythm") { PolyOptions(metronome, viewModel) }
            GroupedSection("Practice") { PracticeOptions(metronome, viewModel) }
            GroupedSection("Sounds") { SoundOptions(metronome, viewModel) }
        }
    }
}

@Composable
private fun MeterOptions(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    val meter = metronome.timeSignature
    NumberStepper(
        headline = "Beats per bar",
        valueText = "${meter.numerator}",
        onDecrement = { viewModel.changeNumerator(-1) },
        onIncrement = { viewModel.changeNumerator(1) },
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        DENOMINATORS.forEachIndexed { index, denominator ->
            SegmentedButton(
                selected = meter.denominator == denominator,
                onClick = { viewModel.setTimeSignature(meter.numerator, denominator) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DENOMINATORS.size, baseShape = SmoothShape.Full),
            ) {
                Text("/$denominator")
            }
        }
    }
    MenuPickerRow(
        headline = "Subdivision",
        options = (DIVISIONS_MIN..DIVISIONS_MAX).map { PickerOption(it, subdivisionLabel(it)) },
        selected = metronome.divisionsPerBeat,
        onSelect = viewModel::setDivisionsPerBeat,
        menuColor = LocalGroupedListColors.current.group,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternOptions(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    val presets = PATTERN_PRESETS.map { PickerOption(it.bars, it.label) }
    val options = if (presets.any { it.key == metronome.barPattern }) {
        presets
    } else {
        presets + PickerOption(metronome.barPattern, "Custom")
    }
    MenuPickerRow(
        headline = "Preset",
        options = options,
        selected = metronome.barPattern,
        onSelect = viewModel::setBarPattern,
        menuColor = LocalGroupedListColors.current.group,
    )
    FlowRow(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..PATTERN_MAX_BARS).forEach { bar ->
            FilterChip(
                selected = metronome.barPattern.getOrNull(bar - 1) == 1,
                onClick = {
                    val pattern = metronome.barPattern.toMutableList()
                    while (pattern.size < bar) pattern.add(0)
                    pattern[bar - 1] = if (pattern[bar - 1] == 1) 0 else 1
                    viewModel.setBarPattern(pattern)
                },
                label = { Text("$bar") },
            )
        }
    }
}

@Composable
private fun PolyOptions(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    SwitchRow(
        headline = "Polyrhythm",
        checked = metronome.poly.enabled,
        onCheckedChange = viewModel::setPolyEnabled,
    )
    if (metronome.poly.enabled) {
        NumberStepper(
            headline = "Events per bar",
            valueText = "${metronome.poly.events}",
            onDecrement = { viewModel.changePolyEvents(-1) },
            onIncrement = { viewModel.changePolyEvents(1) },
        )
        SwitchRow(
            headline = "Accent first event",
            checked = metronome.poly.accentFirst,
            onCheckedChange = viewModel::setPolyAccentFirst,
        )
    }
}

@Composable
private fun PracticeOptions(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    SwitchRow(
        headline = "Count-in bar",
        checked = metronome.countIn,
        onCheckedChange = viewModel::setCountIn,
    )
    SwitchRow(
        headline = "Tempo ramp",
        checked = metronome.ramp.enabled,
        onCheckedChange = viewModel::setRampEnabled,
    )
    if (metronome.ramp.enabled) {
        NumberStepper(
            headline = "Target BPM",
            valueText = "${metronome.ramp.targetBpm.roundToInt()}",
            onDecrement = { viewModel.changeRampTargetBpm(-1) },
            onIncrement = { viewModel.changeRampTargetBpm(1) },
        )
        NumberStepper(
            headline = "Ramp bars",
            valueText = "${metronome.ramp.bars}",
            onDecrement = { viewModel.changeRampBars(-1) },
            onIncrement = { viewModel.changeRampBars(1) },
        )
    }
}

@Composable
private fun SoundOptions(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    val voices = remember { MetronomeVoices.options().map { PickerOption(it.id, it.label) } }
    SoundSlot.entries.forEach { slot ->
        val role = metronome.sounds.role(slot)
        MenuPickerRow(
            headline = slot.label,
            options = voices,
            selected = role.id,
            onSelect = { id ->
                viewModel.setVoice(slot, id)
                viewModel.previewVoice(id, role.vol)
            },
            menuColor = LocalGroupedListColors.current.group,
        )
    }
    Text(
        text = "Volume",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
    Slider(
        colors = accentSliderColors(),
        value = metronome.masterVol.toFloat(),
        onValueChange = { viewModel.setMasterVolume(it.toDouble()) },
        valueRange = 0f..1f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .semantics { contentDescription = "Master volume" },
    )
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(shape = SmoothShape.Full, onClick = { onSave(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(shape = SmoothShape.Full, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun subdivisionLabel(n: Int): String =
    SUBDIVISIONS.firstOrNull { it.n == n }?.label ?: "$n per beat"

private fun meterFeel(denominator: Int): String = when (denominator) {
    8 -> "compound feel"
    16 -> "fast"
    else -> "simple"
}

private fun presetSummary(state: MetronomeState): String =
    "${state.bpm.roundToInt()} BPM · ${state.timeSignature.numerator}/${state.timeSignature.denominator}"

private fun statusLabel(ui: MetronomeUiState): String {
    if (!ui.isPlaying) return ""
    val ramp = ui.state.ramp
    return when {
        ui.inCountIn -> "Count-in"
        !ui.barActive -> "Bar ${ui.barIndex + 1} · muted"
        ramp.enabled -> "Bar ${ui.barIndex + 1} · ramping to ${ramp.targetBpm.roundToInt()}"
        else -> "Bar ${ui.barIndex + 1}"
    }
}

@Composable
private fun rememberTapTempo(onBpm: (Double) -> Unit): () -> Unit {
    val taps = remember { mutableStateListOf<Long>() }
    val latestOnBpm by rememberUpdatedState(onBpm)
    return remember(taps) {
        {
            val now = SystemClock.elapsedRealtime()
            if (taps.isNotEmpty() && now - taps.last() > TAP_RESET_MS) taps.clear()
            taps.add(now)
            while (taps.size > TAP_HISTORY) taps.removeAt(0)
            if (taps.size >= 2) {
                val intervals = buildList {
                    for (i in 1 until taps.size) add((taps[i] - taps[i - 1]).toDouble())
                }
                tapBpm(intervals)?.let(latestOnBpm)
            }
        }
    }
}

private const val TAP_RESET_MS = 2000L
private const val TAP_HISTORY = 6
