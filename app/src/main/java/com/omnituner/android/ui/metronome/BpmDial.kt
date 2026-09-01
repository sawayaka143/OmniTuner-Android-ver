package com.omnituner.android.ui.metronome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnituner.core.metronome.BPM_MAX
import com.omnituner.core.metronome.BPM_MIN
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TICKS_PER_ROTATION = 55
private const val DEG_PER_TICK = 360.0 / TICKS_PER_ROTATION
private const val POINTER_DEG = 270.0

// Web dial face coordinates (bpm-dial.ts): center 150,150; tick band 116-124.
private const val FACE = 300f
private const val TICK_INNER = 116f
private const val TICK_OUTER = 124f
private const val HUB_RADIUS = 6f

private fun rotationFor(bpm: Double): Double = (bpm - 1.0) * DEG_PER_TICK

private fun bpmFor(rotation: Double): Double =
    ((rotation / DEG_PER_TICK).roundToInt() + 1).coerceIn(BPM_MIN, BPM_MAX).toDouble()

/**
 * Port of tests/bpm-dial.ts: a rotary tempo dial with 55 ticks per rotation
 * (one BPM per tick). Dragging spins the tick ring; the value snaps to the
 * nearest tick on release. Live-emits BPM changes during the drag.
 */
@Composable
fun BpmDial(
    bpm: Double,
    onBpmChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragRotation by remember { mutableStateOf<Double?>(null) }
    val latestBpm by rememberUpdatedState(bpm)
    val latestOnBpmChange by rememberUpdatedState(onBpmChange)

    val displayRotation = dragRotation ?: rotationFor(bpm)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val pointerColor = MaterialTheme.colorScheme.primary
    val hubColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .pointerInput(Unit) {
                    var lastAngle: Double? = null
                    fun angleAt(offset: Offset): Double {
                        val cx = size.width / 2.0
                        val cy = size.height / 2.0
                        val deg = Math.toDegrees(
                            atan2((offset.y - cy).toFloat(), (offset.x - cx).toFloat()).toDouble(),
                        )
                        return if (deg < 0) deg + 360.0 else deg
                    }

                    detectDragGestures(
                        onDragStart = { offset -> lastAngle = angleAt(offset) },
                        onDrag = { change, _ ->
                            val angle = angleAt(change.position)
                            val prev = lastAngle
                            if (prev != null) {
                                var delta = angle - prev
                                if (delta > 180) delta -= 360
                                if (delta < -180) delta += 360
                                lastAngle = angle
                                val next = (dragRotation ?: rotationFor(latestBpm)) + delta
                                dragRotation = next
                                latestOnBpmChange(bpmFor(next))
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            val current = dragRotation ?: rotationFor(latestBpm)
                            val snapped = bpmFor(current)
                            dragRotation = null
                            latestOnBpmChange(snapped)
                        },
                        onDragCancel = { dragRotation = null },
                    )
                }
                .semantics {
                    contentDescription = "Tempo dial, ${bpm.roundToInt()} beats per minute"
                    customActions = listOf(
                        CustomAccessibilityAction("Decrease tempo by 5") {
                            latestOnBpmChange((latestBpm - 5).coerceAtLeast(BPM_MIN.toDouble()))
                            true
                        },
                        CustomAccessibilityAction("Increase tempo by 5") {
                            latestOnBpmChange((latestBpm + 5).coerceAtMost(BPM_MAX.toDouble()))
                            true
                        },
                    )
                },
        ) {
            val scale = size.width / FACE
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Rotating tick ring (web: faceTransform rotate(rotation cx cy))
            rotate(degrees = displayRotation.toFloat(), pivot = Offset(cx, cy)) {
                for (i in 0 until TICKS_PER_ROTATION) {
                    val angle = (POINTER_DEG + i * DEG_PER_TICK) * PI / 180.0
                    val cosA = cos(angle).toFloat()
                    val sinA = sin(angle).toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(cx + TICK_INNER * scale * cosA, cy + TICK_INNER * scale * sinA),
                        end = Offset(cx + TICK_OUTER * scale * cosA, cy + TICK_OUTER * scale * sinA),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Static pointer at 270 degrees + hub
            val pointerAngle = POINTER_DEG * PI / 180.0
            val pointerLength = (TICK_INNER - 16f) * scale
            drawLine(
                color = pointerColor,
                start = Offset(cx, cy),
                end = Offset(
                    cx + pointerLength * cos(pointerAngle).toFloat(),
                    cy + pointerLength * sin(pointerAngle).toFloat(),
                ),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = hubColor,
                radius = HUB_RADIUS * scale,
                center = Offset(cx, cy),
            )
        }
    }
}
