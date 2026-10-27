package com.omnituner.android.ui.metronome

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.omnituner.android.ui.common.WheelPickerPage
import com.omnituner.android.ui.common.PickerRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.ui.common.GroupedSection
import com.omnituner.android.ui.common.MenuPickerRow
import com.omnituner.android.ui.common.NumberStepper
import com.omnituner.android.ui.common.PickerOption
import com.omnituner.android.ui.common.OptionSheet
import com.omnituner.android.ui.common.RepeatingIconButton
import com.omnituner.android.ui.common.SegmentedRow
import com.omnituner.android.ui.common.SwitchRow
import com.omnituner.android.ui.common.TopBarContent
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

    // Upright, the meter and preset menus live in the top bar (in landscape the rail replaces the
    // bar, and the panel carries them itself).
    TopBarContent(
        title = {
            MeterMenu(
                metronome = ui.state,
                onPick = { viewModel.setTimeSignature(it.numerator, it.denominator) },
            )
        },
        actions = {
            PresetMenu(
                ui = ui,
                onApply = viewModel::applyPreset,
                onDelete = viewModel::deletePreset,
                onSave = { savingPreset = true },
            )
        },
    )

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
        OptionsSheet(
            metronome = ui.state,
            viewModel = viewModel,
            playingBar = ui.patternPos.takeIf { ui.isPlaying && !ui.inCountIn },
            onDismiss = { optionsOpen = false },
        )
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

    val header = @Composable { headerModifier: Modifier ->
        Row(
            modifier = headerModifier.fillMaxWidth(),
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
    }
    val readout = @Composable { readoutModifier: Modifier, wide: Boolean ->
        TempoReadout(
            ui = ui,
            viewModel = viewModel,
            fold = { fold.value },
            onWheelInteraction = { active ->
                if (active) wheelOpen = true
                wheelBusy = active
            },
            onTap = onTap,
            wide = wide,
            modifier = readoutModifier,
        )
    }
    val transport = @Composable { transportModifier: Modifier, tall: Boolean ->
        TransportRow(
            isPlaying = ui.isPlaying,
            onToggle = {
                if (!ui.isPlaying) wheelOpen = false
                viewModel.toggle()
            },
            onOptions = onOptions,
            tall = tall,
            modifier = transportModifier,
        )
    }

    Surface(
        modifier = modifier,
        shape = SmoothShape(PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints {
            if (maxWidth > maxHeight) {
                // Landscape: the tempo readout with a large Tap under it on the left; on the right the
                // menus, and Start filling everything under them.
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(bottom = PanelHeaderInset),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        readout(Modifier.weight(1f), true)
                        TapButton(onTap = onTap, size = WideTapButtonSize)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = PanelHeaderInset, end = PanelHeaderInset, bottom = PanelHeaderInset),
                    ) {
                        header(Modifier)
                        transport(Modifier.weight(1f).padding(top = 8.dp), true)
                    }
                }
            } else {
                Column {
                    readout(Modifier.weight(1f), false)
                    transport(Modifier.padding(start = PanelHeaderInset, end = PanelHeaderInset, bottom = PanelHeaderInset), false)
                }
            }
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
    }
    if (expanded) {
        OptionSheet(
            title = "Meter",
            options = METER_PRESETS.map {
                PickerOption(it, "${it.numerator}/${it.denominator}")
            },
            selected = meter,
            onSelect = onPick,
            onDismiss = { expanded = false },
        )
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
    }
    if (expanded) {
        OptionSheet(
            title = "Presets",
            options = ui.presets.map { PickerOption(it.id, it.name, supporting = presetSummary(it.state)) },
            selected = current?.id,
            onSelect = onApply,
            onDismiss = { expanded = false },
            // Deleting keeps the sheet open, so several can go in a row.
            trailing = { id ->
                val name = ui.presets.firstOrNull { it.id == id }?.name.orEmpty()
                IconButton(onClick = { onDelete(id) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete preset $name",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            // Under the list, so Save stays in reach however many presets there are.
            footer = { close ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.presets.isEmpty()) {
                        Text(
                            text = "No presets yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            close()
                            onSave()
                        },
                        shape = SmoothShape.Full,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save current…")
                    }
                    if (ui.presets.size >= PRESETS_MAX) {
                        Text(
                            text = "Replaces the oldest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
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
    // Landscape: Tap moves out from under the caption, so the wheel may take more of the height.
    wide: Boolean = false,
) {
    val metronome = ui.state
    // One dot per written pulse (12/8 shows twelve), grouped into the meter's beats. While playing the
    // dots follow the bar being played, which keeps the old meter until the next bar line.
    val chosen = meterModel(metronome.timeSignature.numerator, metronome.timeSignature.denominator)
    val playingBar = ui.isPlaying && ui.pulsesPerBar > 0
    val beats = if (playingBar) ui.pulsesPerBar else chosen.numerator
    val beatGroup = if (playingBar) ui.pulsesPerBeat else chosen.divisionsPerBeat
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

    // The chosen tempo is always in the accent, like the reference pitch wheel in Settings.
    val numberColor = LocalAccentColors.current.accent

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ReadoutInset, vertical = 16.dp),
    ) {
        // Three digits must fit between the steppers (also once grown by CompactScale when folded),
        // and the drum needs room for rows around them.
        val heightShare = if (wide) 0.3f else 0.2f
        val wheelShare = if (wide) WideWheelHeightShare else WheelHeightShare
        val bpmSize = min(maxHeight * heightShare, (maxWidth - StepperSlot * 2) / 1.9f).coerceIn(48.dp, 144.dp) * NumberShare
        val rowPitch = (bpmSize * 0.36f).coerceIn(24.dp, 38.dp)
        val dotsHeight = min(32.dp, maxHeight * 0.08f)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BeatDots(
                beats = beats,
                group = beatGroup,
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
                        if (!wide) TapButton(onTap = onTap, modifier = Modifier.padding(top = 8.dp))
                    }
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val area = constraints.maxHeight
                val caption = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
                val gap = CaptionGap.roundToPx()
                // The drum is bent onto the wheel's height, so this also sets how many neighbours show.
                val wheelHeight = ((area - caption.height - gap) * wheelShare).roundToInt().coerceAtLeast(0)
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

// Landscape is short: the wheel takes nearly all of it, or its centre band would not fit.
private const val WideWheelHeightShare = 0.9f

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
        containerColor = LocalGroupedListColors.current.group,
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
 * Dots that start a beat are drawn full size and the rest smaller so beats read at a glance.
 * Count-in and muted bars light neutral, since nothing is "in time" to play along to yet.
 */
@Composable
private fun BeatDots(
    beats: Int,
    group: Int,
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
                    targetValue = if (on) lit else scheme.onSurface.copy(alpha = if (index % group == 0) 0.28f else 0.14f),
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "beatDot",
                )
                val size by animateDpAsState(
                    targetValue = if (index % group == 0 || on) dot else dot * 0.7f,
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
    // Landscape: the buttons fill the height they are given, as blocks with the panel's inner corner.
    tall: Boolean = false,
) {
    val accent = LocalAccentColors.current
    val shape = if (tall) HeaderMenuShape else SmoothShape.Full
    val height = if (tall) Modifier.fillMaxHeight() else Modifier.height(TransportHeight)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onToggle,
            shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = accent.accent, contentColor = accent.onAccent),
            modifier = Modifier
                .weight(1f)
                .then(height)
                .semantics { contentDescription = if (isPlaying) "Stop metronome" else "Start metronome" },
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(if (tall) 40.dp else 28.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isPlaying) "Stop" else "Start",
                style = if (tall) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Icon only: the row is too narrow inside the panel for a "More" label.
        FilledTonalButton(
            onClick = onOptions,
            shape = shape,
            colors = panelTonalColors(),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .width(MoreButtonWidth)
                .then(height),
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
private fun TapButton(onTap: () -> Unit, modifier: Modifier = Modifier, size: Dp = TapButtonSize) {
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
        modifier = modifier.size(size),
    ) {
        Text(
            "Tap",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val TapButtonSize = 128.dp
private val WideTapButtonSize = 136.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(metronome: MetronomeState, viewModel: MetronomeViewModel, playingBar: Int?, onDismiss: () -> Unit) {
    // Laid out like Settings: an inset grouped list on the grouped-list page color.
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalGroupedListColors.current.page) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            GroupedSection("Meter") { MeterOptions(metronome, viewModel) }
            GroupedSection("Bar mute pattern") { PatternOptions(metronome, viewModel, playingBar) }
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
    SegmentedRow(
        options = DENOMINATORS,
        selected = meter.denominator,
        label = { "/$it" },
        onSelect = { viewModel.setTimeSignature(meter.numerator, it) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    MenuPickerRow(
        headline = "Subdivision",
        options = (DIVISIONS_MIN..DIVISIONS_MAX).map { PickerOption(it, subdivisionLabel(it)) },
        selected = metronome.divisionsPerBeat,
        onSelect = viewModel::setDivisionsPerBeat,
        menuColor = LocalGroupedListColors.current.group,
    )
}

/**
 * The mute pattern as the loop it is: one cell per bar (filled accent plays, outline is muted), as
 * many cells as bars in the loop, with quick fills above and, while running, a ring on the bar
 * being played ([playingBar]).
 */
@Composable
private fun PatternOptions(metronome: MetronomeState, viewModel: MetronomeViewModel, playingBar: Int?) {
    val pattern = metronome.barPattern
    val accent = LocalAccentColors.current
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PATTERN_PRESETS.forEach { preset ->
            FilterChip(
                selected = pattern == preset.bars,
                onClick = { viewModel.setBarPattern(preset.bars) },
                label = { Text(preset.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.accent,
                    selectedLabelColor = accent.onAccent,
                ),
            )
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pattern.withIndex().toList().chunked(PATTERN_CELLS_PER_ROW).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCells.forEach { (index, value) ->
                    BarCell(
                        number = index + 1,
                        plays = value == 1,
                        current = index == playingBar,
                        onClick = {
                            viewModel.setBarPattern(pattern.toMutableList().also { it[index] = if (value == 1) 0 else 1 })
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep a short last row's cells the same width as the full rows above.
                repeat(PATTERN_CELLS_PER_ROW - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    NumberStepper(
        headline = "Bars in loop",
        valueText = "${pattern.size}",
        onDecrement = { if (pattern.size > 1) viewModel.setBarPattern(pattern.dropLast(1)) },
        onIncrement = { if (pattern.size < PATTERN_MAX_BARS) viewModel.setBarPattern(pattern + 1) },
    )
}

@Composable
private fun BarCell(number: Int, plays: Boolean, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccentColors.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = SmoothShape(12.dp),
        color = if (plays) accent.accent else Color.Transparent,
        contentColor = if (plays) accent.onAccent else scheme.onSurfaceVariant,
        border = BorderStroke(
            width = if (current) 2.dp else 1.dp,
            color = if (current) scheme.onSurface else scheme.outlineVariant,
        ),
        modifier = modifier
            .height(40.dp)
            .semantics { stateDescription = if (plays) "Plays" else "Muted" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val PATTERN_CELLS_PER_ROW = 8

@Composable
private fun PolyOptions(metronome: MetronomeState, viewModel: MetronomeViewModel) {
    SwitchRow(
        headline = "Polyrhythm",
        checked = metronome.poly.enabled,
        onCheckedChange = viewModel::setPolyEnabled,
    )
    AnimatedVisibility(
        visible = metronome.poly.enabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
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
    var pickingTarget by rememberSaveable { mutableStateOf(false) }
    AnimatedVisibility(
        visible = metronome.ramp.enabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            PickerRow(
                headline = "Target BPM",
                value = "${metronome.ramp.targetBpm.roundToInt()}",
                onClick = { pickingTarget = true },
            )
            NumberStepper(
                headline = "Ramp bars",
                valueText = "${metronome.ramp.bars}",
                onDecrement = { viewModel.changeRampBars(-1) },
                onIncrement = { viewModel.changeRampBars(1) },
            )
        }
    }
    if (pickingTarget) {
        TargetBpmDialog(
            initial = metronome.ramp.targetBpm.roundToInt(),
            onDismiss = { pickingTarget = false },
            onSet = {
                viewModel.setRampTargetBpm(it)
                pickingTarget = false
            },
        )
    }
}

/** Full-screen wheel picker for the ramp's target tempo: the Reference pitch page without Reset. */
@Composable
private fun TargetBpmDialog(initial: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var bpm by rememberSaveable { mutableIntStateOf(initial) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = LocalGroupedListColors.current.page) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                WheelPickerPage(
                    title = "Adjust target tempo",
                    unit = "BPM",
                    value = bpm,
                    range = BPM_MIN..BPM_MAX,
                    label = "Target tempo",
                    valueText = { "$it beats per minute" },
                    onValueChange = { bpm = it },
                    onSet = { onSet(bpm) },
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = LocalGroupedListColors.current.group),
                    modifier = Modifier.padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
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
        containerColor = LocalGroupedListColors.current.group,
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
