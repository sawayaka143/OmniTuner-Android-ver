package com.omnituner.android.ui.tuner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.R
import com.omnituner.android.ui.common.WebSelectOption
import com.omnituner.android.ui.common.WebSelectRow
import com.omnituner.android.ui.theme.LightTuneInk
import com.omnituner.android.ui.theme.currentWebPalette
import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.data.Instrument
import com.omnituner.core.prefs.MAX_CUSTOM_INSTRUMENT_NAME_LENGTH
import com.omnituner.core.prefs.MAX_CUSTOM_TUNING_NAME_LENGTH
import com.omnituner.core.prefs.MAX_STRING_COUNT
import com.omnituner.core.prefs.MAX_TUNER_MIDI_NOTE
import com.omnituner.core.prefs.MIN_STRING_COUNT
import com.omnituner.core.prefs.MIN_TUNER_MIDI_NOTE
import com.omnituner.core.prefs.TUNER_MODE_AUTO
import com.omnituner.core.prefs.TUNER_MODE_MANUAL
import com.omnituner.core.prefs.TUNER_STARTUP_REMEMBER
import kotlin.math.roundToInt

private data class TuningEditorRequest(
    val editingId: String?,
    val initialName: String,
    val initialNotes: List<Int>,
)

private data class InstrumentFormState(
    val editingId: String?,
    val name: String,
    val stringCount: Int,
    val notes: List<Int>,
)

private data class InstrumentManagerRequest(
    val initialForm: InstrumentFormState?,
)

private data class TuningPreset(
    val id: String,
    val name: String,
    val notes: List<Int>,
)

@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current

    // Keep the screen on while capturing (web: keep-awake plugin).
    DisposableEffect(state.isCapturing) {
        val window = (context as? Activity)?.window
        if (state.isCapturing) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var permissionAutoRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            hasPermission = true
            viewModel.startCapture()
        } else {
            permanentlyDenied =
                (context as? ComponentActivity)
                    ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.startCapture()
        } else if (!permissionAutoRequested) {
            permissionAutoRequested = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Mic mirrors the app lifecycle: capture while the tuner is on screen, stop on pause.
    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        if (!hasPermission) hasPermission = true
                        viewModel.startCapture()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.stopCapture()
                else -> Unit
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            viewModel.stopCapture()
        }
    }

    var tuningEditor by remember { mutableStateOf<TuningEditorRequest?>(null) }
    var instrumentManager by remember { mutableStateOf<InstrumentManagerRequest?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Workbench card (web: .workbench — bordered rounded card, max 480px on phones)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    RoundedCornerShape(8.dp),
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        ) {
            WorkbenchHeader(
                state = state,
                autoDetect = state.mode == TUNER_MODE_AUTO,
                onSelectMode = { auto ->
                    viewModel.selectMode(if (auto) TUNER_MODE_AUTO else TUNER_MODE_MANUAL)
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Controls row (web: .tuner-controls — centered instrument selector)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                InstrumentTuningSelector(
                    state = state,
                    onSelectInstrument = viewModel::selectInstrument,
                    onSelectTuning = viewModel::selectTuning,
                    onNewTuning = {
                        val currentTuning = state.tunings.firstOrNull {
                            it.id == state.selectedTuningId
                        }
                        val notes = currentTuning?.strings?.map { midiNumberOf(it) }
                            ?: List(state.instrumentStringCount) { 69 }
                        tuningEditor = TuningEditorRequest(
                            editingId = null,
                            initialName = "",
                            initialNotes = notes,
                        )
                    },
                    onEditTuning = { tuningId ->
                        val tuning = state.tunings.firstOrNull {
                            it.id == tuningId && it.kind == "custom"
                        } ?: return@InstrumentTuningSelector
                        tuningEditor = TuningEditorRequest(
                            editingId = tuning.id,
                            initialName = tuning.label,
                            initialNotes = tuning.strings.map { midiNumberOf(it) },
                        )
                    },
                    onDeleteTuning = viewModel::deleteCustomTuning,
                    onManageInstruments = {
                        instrumentManager = InstrumentManagerRequest(initialForm = null)
                    },
                    onNewInstrument = {
                        instrumentManager = InstrumentManagerRequest(
                            initialForm = InstrumentFormState(
                                editingId = null,
                                name = "",
                                stringCount = 6,
                                notes = List(6) { 40 },
                            ),
                        )
                    },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Stage (web: .tuner-stage — centered column)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TunePrompt(state = state)

                // Needle meter: ±50 cents, 41 ticks, center at index 20
                PitchMeterCanvas(
                    needlePercent = state.needlePercent.toFloat(),
                    needleColor = needleColor(state) ?: currentWebPalette().needleColor,
                    glow = state.pulseActive && state.confirmed,
                    cents = state.frameCents?.toFloat(),
                )

                PitchDisplay(
                    state = state,
                    color = needleColor(state) ?: currentWebPalette().needleColor,
                )

                StringChips(state = state, onSelect = viewModel::selectString)

                if (!hasPermission) {
                    MicPermissionBanner(
                        permanentDenial = permanentlyDenied,
                        onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                            context.startActivity(intent)
                        },
                    )
                }

                if (state.captureError != null) {
                    Text(
                        text = state.captureError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    tuningEditor?.let { request ->
        TuningEditorDialog(
            request = request,
            presets = state.tunings.map { tuning ->
                TuningPreset(
                    id = tuning.id,
                    name = tuning.label,
                    notes = tuning.strings.map { midiNumberOf(it) },
                )
            },
            onDismiss = { tuningEditor = null },
            onSave = { name, notes ->
                val error = viewModel.saveCustomTuning(request.editingId, name, notes)
                if (error == null) tuningEditor = null
                error
            },
        )
    }

    instrumentManager?.let { request ->
        InstrumentManagerDialog(
            request = request,
            customInstruments = state.instruments.filter { it.kind == "custom" },
            onDismiss = { instrumentManager = null },
            onEdit = { instrument ->
                val notes = instrument.tunings.firstOrNull()?.strings?.map { midiNumberOf(it) }
                    ?: List(instrument.stringCount) { 40 }
                instrumentManager = InstrumentManagerRequest(
                    initialForm = InstrumentFormState(
                        editingId = instrument.id,
                        name = instrument.label,
                        stringCount = instrument.stringCount,
                        notes = notes,
                    ),
                )
            },
            onDelete = viewModel::deleteCustomInstrument,
            onNew = {
                instrumentManager = InstrumentManagerRequest(
                    initialForm = InstrumentFormState(
                        editingId = null,
                        name = "",
                        stringCount = 6,
                        notes = List(6) { 40 },
                    ),
                )
            },
            onSave = { form ->
                val error = viewModel.saveCustomInstrument(
                    form.editingId,
                    form.name,
                    form.stringCount,
                    form.notes,
                )
                if (error == null) instrumentManager = null
                error
            },
        )
    }
}

private fun midiNumberOf(string: com.omnituner.core.data.NamedFrequency): Int =
    com.omnituner.core.audio.frequencyToMidiNote(string.freq) ?: 69

@Composable
private fun WorkbenchHeader(
    state: TunerUiState,
    autoDetect: Boolean,
    onSelectMode: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CURRENT TUNING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.tuningLabel,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.02).em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${state.instrumentLabel} · ${state.tuningSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AutoDetectToggle(checked = autoDetect, onChange = onSelectMode)
        }
    }
}

@Composable
private fun AutoDetectToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Auto detect", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun TunePrompt(state: TunerUiState) {
    val promptColor = if (state.isTuned) {
        tunedColor(state)
    } else {
        needleColor(state) ?: currentWebPalette().needleColor
    }
    val centsColor = if (state.isTuned) {
        tunedColor(state)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.tunePrompt,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                color = promptColor,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.tuneCents,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.04.em,
                color = centsColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PitchDisplay(state: TunerUiState, color: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.noteName ?: "—",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = state.noteOctave?.toString() ?: "",
                fontSize = 28.sp,
                color = color,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        Text(
            text = state.hzText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.labelLarge,
            color = if (state.isTuned) tunedColor(state) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun needleColor(state: TunerUiState): Color? {
    val tuned = state.isTuned
    if (tuned) return tunedColor(state)
    val hex = state.tuneColorHex ?: return null
    val color = parseHex(hex) ?: return null
    // Adapt ink for light backgrounds (web: LIGHT_TUNE_INK blend).
    val surfaceLuminance = MaterialTheme.colorScheme.surface.luminance()
    return if (surfaceLuminance > 0.5f) {
        blend(color, LightTuneInk, 0.3f)
    } else {
        color
    }
}

@Composable
private fun tunedColor(state: TunerUiState): Color =
    parseHex(state.inTune.color) ?: currentWebPalette().inTuneColor

private fun parseHex(hex: String): Color? {
    if (!hex.startsWith("#") || hex.length != 7) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        null
    }
}

private fun blend(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = 1f,
)

@Composable
private fun MicPermissionBanner(
    permanentDenial: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.tabler_microphone),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = "Microphone needed to tune",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = if (permanentDenial) onOpenSettings else onAllow) {
            Text(if (permanentDenial) "Open settings" else "Allow")
        }
    }
}

@Composable
private fun InstrumentTuningSelector(
    state: TunerUiState,
    onSelectInstrument: (String) -> Unit,
    onSelectTuning: (String) -> Unit,
    onNewTuning: () -> Unit,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
    onManageInstruments: () -> Unit,
    onNewInstrument: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WebSelectRow(
            label = "Instrument",
            value = state.instrumentLabel,
            options = state.instruments.map { instrument ->
                WebSelectOption(instrument.id, instrument.label)
            },
            selected = state.selectedInstrumentId,
            onSelect = onSelectInstrument,
            footer = {
                FooterAction(R.drawable.tabler_plus, "New instrument", onNewInstrument)
                FooterAction(null, "Manage instruments", onManageInstruments)
            },
        )
        WebSelectRow(
            label = "Tuning",
            value = state.tuningLabel,
            options = state.tunings.map { tuning ->
                WebSelectOption(tuning.id, tuning.label)
            },
            selected = state.selectedTuningId,
            onSelect = onSelectTuning,
            itemTrailing = { option ->
                val tuning = state.tunings.firstOrNull { it.id == option.value }
                if (tuning?.kind == "custom") {
                    IconButton(
                        onClick = { onEditTuning(tuning.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.tabler_pencil),
                            contentDescription = "Edit tuning ${tuning.label}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { onDeleteTuning(tuning.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.tabler_trash),
                            contentDescription = "Delete tuning ${tuning.label}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            footer = {
                FooterAction(R.drawable.tabler_plus, "New tuning", onNewTuning)
            },
        )
    }
}

@Composable
private fun FooterAction(iconRes: Int?, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        if (iconRes != null) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label)
    }
}

@Composable
private fun StringChips(state: TunerUiState, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.strings) { string ->
            val index = state.strings.indexOf(string)
            val isActive = state.activeString == string.name
            val isTuned = state.isTuned && isActive
            val inAutoSet = string.name in state.autoTuned
            AssistChip(
                onClick = { onSelect(index) },
                label = { Text(string.name) },
                leadingIcon = if (inAutoSet || isTuned) {
                    {
                        Icon(
                            painterResource(R.drawable.tabler_check),
                            contentDescription = null,
                            tint = tunedColor(state),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = when {
                        isTuned -> tunedColor(state).copy(alpha = 0.25f)
                        isActive -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
                modifier = Modifier.semantics {
                    contentDescription = "String ${string.name}${if (inAutoSet) ", tuned" else ""}"
                },
            )
        }
    }
}

@Composable
private fun PitchMeterCanvas(
    needlePercent: Float,
    needleColor: Color,
    glow: Boolean,
    cents: Float?,
) {
    val pulse = rememberInfiniteTransition(label = "glow")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Theme-aware tick ink from web tokens --meter-tick-minor/-major.
    val tickColor = currentWebPalette().meterTickMinor
    val majorColor = currentWebPalette().meterTickMajor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    val clamped = (cents ?: 0f).coerceIn(-50f, 50f).roundToInt()
                    contentDescription =
                        "Tuning meter, needle at $clamped cents, range -50 to +50"
                },
        ) {
            val width = size.width
            val centerY = size.height * 0.55f
            val tickHeightMajor = size.height * 0.30f
            val tickHeightMinor = size.height * 0.18f
            val totalTicks = 41

            // baseline
            drawLine(
                color = labelColor.copy(alpha = 0.10f),
                start = Offset(width * 0.02f, centerY),
                end = Offset(width * 0.98f, centerY),
                strokeWidth = 2f,
            )

            for (i in 0 until totalTicks) {
                val x = width * (0.02f + 0.96f * i / (totalTicks - 1))
                val isCenter = i == 20
                val isMajor = i % 5 == 0
                val height = when {
                    isCenter -> tickHeightMajor * 1.25f
                    isMajor -> tickHeightMajor
                    else -> tickHeightMinor
                }
                drawLine(
                    color = if (isCenter) needleColor else if (isMajor) majorColor else tickColor,
                    start = Offset(x, centerY - height / 2),
                    end = Offset(x, centerY + height / 2),
                    strokeWidth = if (isCenter) 4f else if (isMajor) 3f else 2f,
                    cap = StrokeCap.Round,
                )
            }

            // needle
            val needleX = width * (needlePercent / 100f).coerceIn(0f, 1f)
            if (glow) {
                drawCircle(
                    color = needleColor.copy(alpha = glowAlpha * 0.5f),
                    radius = size.height * 0.28f,
                    center = Offset(needleX, centerY),
                )
            }
            drawLine(
                color = needleColor,
                start = Offset(needleX, centerY - tickHeightMajor * 0.95f),
                end = Offset(needleX, centerY + tickHeightMajor * 0.95f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = needleColor,
                radius = 9f,
                center = Offset(needleX, centerY),
                style = Stroke(width = 3f),
            )

            // numeric labels (web PitchMeter: -50/-25/0/+25/+50 at 0/25/50/75/100%)
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = 11.sp.toPx()
                }
                val labelY = centerY + tickHeightMajor * 0.5f + 18.sp.toPx()
                val labels = listOf("-50", "-25", "0", "+25", "+50")
                labels.forEachIndexed { index, text ->
                    val x = width * (0.02f + 0.96f * index / (labels.size - 1))
                    paint.color = if (index == 2) needleColor.toArgb() else labelColor.toArgb()
                    paint.isFakeBoldText = index == 2
                    canvas.nativeCanvas.drawText(text, x, labelY, paint)
                }
            }
        }
    }
}

// ------------------------------------------------------------------- dialogs

@Composable
private fun TuningEditorDialog(
    request: TuningEditorRequest,
    presets: List<TuningPreset>,
    onDismiss: () -> Unit,
    onSave: (name: String, notes: List<Int>) -> String?,
) {
    var name by remember(request) { mutableStateOf(request.initialName) }
    var notes by remember(request) { mutableStateOf(request.initialNotes) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var presetOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (request.editingId == null) "New tuning" else "Edit tuning") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_CUSTOM_TUNING_NAME_LENGTH) name = it },
                    label = { Text("Tuning name") },
                    supportingText = {
                        Text("${name.length}/$MAX_CUSTOM_TUNING_NAME_LENGTH")
                    },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Box {
                    TextButton(onClick = { presetOpen = true }) {
                        Text("Start from preset ▾")
                    }
                    DropdownMenu(
                        expanded = presetOpen,
                        onDismissRequest = { presetOpen = false },
                    ) {
                        for (preset in presets) {
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    presetOpen = false
                                    notes = preset.notes
                                },
                            )
                        }
                    }
                }

                notes.forEachIndexed { index, midi ->
                    NoteStepperRow(
                        label = "String ${index + 1}",
                        midi = midi,
                        onChange = { next ->
                            notes = notes.toMutableList().also { it[index] = next }
                        },
                    )
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(name.trim(), notes)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun InstrumentManagerDialog(
    request: InstrumentManagerRequest,
    customInstruments: List<Instrument>,
    onDismiss: () -> Unit,
    onEdit: (Instrument) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onSave: (InstrumentFormState) -> String?,
) {
    val form = request.initialForm

    if (form == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Custom instruments") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (customInstruments.isEmpty()) {
                        Text(
                            "No custom instruments yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    for (instrument in customInstruments) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(instrument.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${instrument.stringCount} strings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onEdit(instrument) }) {
                                Icon(
                                    painterResource(R.drawable.tabler_pencil),
                                    contentDescription = "Edit ${instrument.label}",
                                )
                            }
                            IconButton(onClick = { onDelete(instrument.id) }) {
                                Icon(
                                    painterResource(R.drawable.tabler_trash),
                                    contentDescription = "Delete ${instrument.label}",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onNew) { Text("New instrument") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            },
        )
        return
    }

    var name by remember(form) { mutableStateOf(form.name) }
    var stringCount by remember(form) { mutableStateOf(form.stringCount) }
    var notes by remember(form) { mutableStateOf(form.notes) }
    var error by remember(form) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.editingId == null) "New instrument" else "Edit instrument") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= MAX_CUSTOM_INSTRUMENT_NAME_LENGTH) name = it
                    },
                    label = { Text("Instrument name") },
                    supportingText = {
                        Text("${name.length}/$MAX_CUSTOM_INSTRUMENT_NAME_LENGTH")
                    },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Strings", modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            if (stringCount > MIN_STRING_COUNT) {
                                stringCount -= 1
                                notes = notes.take(stringCount)
                            }
                        },
                    ) { Icon(painterResource(R.drawable.tabler_minus), contentDescription = "Fewer strings") }
                    Text("$stringCount", modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                    IconButton(
                        onClick = {
                            if (stringCount < MAX_STRING_COUNT) {
                                stringCount += 1
                                notes = notes + MIN_TUNER_MIDI_NOTE
                            }
                        },
                    ) { Icon(painterResource(R.drawable.tabler_plus), contentDescription = "More strings") }
                }

                notes.forEachIndexed { index, midi ->
                    NoteStepperRow(
                        label = "String ${index + 1}",
                        midi = midi,
                        onChange = { next ->
                            notes = notes.toMutableList().also { it[index] = next }
                        },
                    )
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = onSave(
                    InstrumentFormState(form.editingId, name.trim(), stringCount, notes),
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NoteStepperRow(
    label: String,
    midi: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange((midi - 1).coerceIn(MIN_TUNER_MIDI_NOTE, MAX_TUNER_MIDI_NOTE)) },
            modifier = Modifier.size(32.dp),
        ) { Icon(painterResource(R.drawable.tabler_minus), contentDescription = "$label down a semitone") }
        Text(
            midiNoteLabel(midi),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = { onChange((midi + 1).coerceIn(MIN_TUNER_MIDI_NOTE, MAX_TUNER_MIDI_NOTE)) },
            modifier = Modifier.size(32.dp),
        ) { Icon(painterResource(R.drawable.tabler_plus), contentDescription = "$label up a semitone") }
        Text(
            "$midi",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )
    }
}
