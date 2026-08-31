package com.omnituner.android.ui.tuner

import android.app.Activity
import android.view.WindowManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.core.prefs.TUNER_MODE_AUTO
import com.omnituner.core.prefs.TUNER_MODE_MANUAL

private val IN_TUNE_COLOR = Color(0xFF7ECBA8)
private val OUT_OF_TUNE_COLOR = Color(0xFFFF8AAB)

@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

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

    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequested = true
        if (granted) {
            viewModel.startCapture()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar: instrument/tuning selectors + capture toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            InstrumentTuningSelector(
                state = state,
                onSelectInstrument = viewModel::selectInstrument,
                onSelectTuning = viewModel::selectTuning,
            )
            CaptureButton(
                isCapturing = state.isCapturing,
                onToggle = {
                    if (!state.isCapturing && !permissionRequested) {
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    } else if (!state.isCapturing) {
                        val error = viewModel.startCapture()
                        if (error != null && error.contains("permission", ignoreCase = true)) {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        viewModel.toggleCapture()
                    }
                },
            )
        }

        // Mode selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == TUNER_MODE_AUTO,
                onClick = { viewModel.selectMode(TUNER_MODE_AUTO) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Auto") }
            SegmentedButton(
                selected = state.mode == TUNER_MODE_MANUAL,
                onClick = { viewModel.selectMode(TUNER_MODE_MANUAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Manual") }
        }

        // Status line
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.labelLarge,
            color = if (state.isTuned) IN_TUNE_COLOR else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        // Note display
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.noteName ?: "—",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = needleColor(state) ?: MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.noteOctave?.toString() ?: "",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
            Text(
                text = state.hzText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.tuneCents.isNotEmpty()) {
                Text(
                    text = state.tunePrompt + "  " + state.tuneCents,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = needleColor(state) ?: OUT_OF_TUNE_COLOR,
                )
            }
        }

        // Needle meter: ±50 cents, 41 ticks, center at index 20
        PitchMeterCanvas(
            needlePercent = state.needlePercent.toFloat(),
            needleColor = needleColor(state) ?: MaterialTheme.colorScheme.primary,
            glow = state.pulseActive && state.confirmed,
        )

        // String chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.strings) { string ->
                val index = state.strings.indexOf(string)
                val isActive = state.activeString == string.name
                val isTuned = state.isTuned && isActive
                val inAutoSet = string.name in state.autoTuned
                AssistChip(
                    onClick = { viewModel.selectString(index) },
                    label = { Text(string.name) },
                    leadingIcon = if (inAutoSet || isTuned) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = IN_TUNE_COLOR,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when {
                            isTuned -> IN_TUNE_COLOR.copy(alpha = 0.25f)
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

        if (state.captureError != null) {
            Text(
                text = state.captureError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = state.tuningSummary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun needleColor(state: TunerUiState): Color? {
    val tuned = state.isTuned
    if (tuned) return IN_TUNE_COLOR
    val hex = state.tuneColorHex ?: return null
    val color = parseHex(hex) ?: return null
    // Adapt ink for light backgrounds (web: LIGHT_TUNE_INK blend).
    val surfaceLuminance = MaterialTheme.colorScheme.surface.luminance()
    return if (surfaceLuminance > 0.5f) {
        blend(color, Color(0xFF1A1A18), 0.3f)
    } else {
        color
    }
}

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
private fun CaptureButton(isCapturing: Boolean, onToggle: () -> Unit) {
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier
            .size(width = 120.dp, height = 48.dp)
            .semantics {
                contentDescription = if (isCapturing) "Stop tuning" else "Start tuning"
            },
    ) {
        Icon(
            if (isCapturing) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(if (isCapturing) "Stop" else "Tune")
    }
}

@Composable
private fun InstrumentTuningSelector(
    state: TunerUiState,
    onSelectInstrument: (String) -> Unit,
    onSelectTuning: (String) -> Unit,
) {
    var instrumentMenuOpen by remember { mutableStateOf(false) }
    var tuningMenuOpen by remember { mutableStateOf(false) }

    Column {
        TextButton(onClick = { instrumentMenuOpen = true }) {
            Text("${state.instrumentLabel} ▾")
        }
        DropdownMenu(expanded = instrumentMenuOpen, onDismissRequest = { instrumentMenuOpen = false }) {
            for (instrument in state.instruments) {
                DropdownMenuItem(
                    text = { Text(instrument.label) },
                    onClick = {
                        instrumentMenuOpen = false
                        onSelectInstrument(instrument.id)
                    },
                )
            }
        }
    }
    Column {
        TextButton(onClick = { tuningMenuOpen = true }) {
            Text(state.tuningLabel)
        }
        DropdownMenu(expanded = tuningMenuOpen, onDismissRequest = { tuningMenuOpen = false }) {
            for (tuning in state.tunings) {
                DropdownMenuItem(
                    text = { Text(tuning.label) },
                    onClick = {
                        tuningMenuOpen = false
                        onSelectTuning(tuning.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun PitchMeterCanvas(
    needlePercent: Float,
    needleColor: Color,
    glow: Boolean,
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
                    contentDescription =
                        "Tuning meter, needle at ${needlePercent.toInt()} percent, " +
                            "50 cents full scale"
                },
        ) {
            val width = size.width
            val centerY = size.height * 0.62f
            val tickHeightMajor = size.height * 0.30f
            val tickHeightMinor = size.height * 0.18f
            val totalTicks = 41
            val tickColor = Color.White.copy(alpha = 0.55f)
            val majorColor = Color.White.copy(alpha = 0.85f)

            // baseline
            drawLine(
                color = tickColor.copy(alpha = 0.35f),
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
        }
    }
}
