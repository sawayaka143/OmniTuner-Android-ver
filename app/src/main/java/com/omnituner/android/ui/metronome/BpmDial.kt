package com.omnituner.android.ui.metronome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omnituner.android.ui.theme.currentWebPalette
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

// Hardware-knob styling radii, same 300-unit FACE space.
private val DIAL_SIZE = 220.dp
private const val WELL_RADIUS = 138f
private const val KNOB_RADIUS = 128f
private const val STEP_RADIUS = 80f
private const val TAP_RADIUS = 62f
private const val POINTER_BASE_RADIUS = 122f
private const val POINTER_APEX_RADIUS = 106f
private const val POINTER_HALF_WIDTH = 7f

private fun rotationFor(bpm: Double): Double = (bpm - 1.0) * DEG_PER_TICK

private fun bpmFor(rotation: Double): Double =
    ((rotation / DEG_PER_TICK).roundToInt() + 1).coerceIn(BPM_MIN, BPM_MAX).toDouble()

/**
 * Port of tests/bpm-dial.ts: a rotary tempo dial with 55 ticks per rotation
 * (one BPM per tick). Dragging spins the tick ring; the value snaps to the
 * nearest tick on release. Live-emits BPM changes during the drag.
 *
 * Rendered as a physical hardware knob (tokens --dial-well/-face/-sheen/-shadow):
 * recessed well, raised knurled body carrying the rotating tick ring on its
 * rim, fixed pointer triangle at 12 o'clock, and a centered TAP button that
 * reports taps through [onTap] (drawn but not clickable when null).
 */
@Composable
fun BpmDial(
    bpm: Double,
    onBpmChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    var dragRotation by remember { mutableStateOf<Double?>(null) }
    val latestBpm by rememberUpdatedState(bpm)
    val latestOnBpmChange by rememberUpdatedState(onBpmChange)

    val displayRotation = dragRotation ?: rotationFor(bpm)

    val palette = currentWebPalette()
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val inkColor = MaterialTheme.colorScheme.onSurface

    val textMeasurer = rememberTextMeasurer()
    val tapLabel = remember(textMeasurer) {
        textMeasurer.measure(
            text = "TAP",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.12.em,
            ),
        )
    }

    // TAP hit target mirrors the painted center button (radius 62 in FACE units).
    val tapHitDiameter = DIAL_SIZE * (TAP_RADIUS * 2f / FACE)

    Box(modifier = modifier.size(DIAL_SIZE), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(DIAL_SIZE)
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
            val center = Offset(cx, cy)

            // 1) Recessed outer well (--dial-well): falls off into shadow
            //    toward the edge, with an inner-shadow rim just inside it.
            val wellRadius = WELL_RADIUS * scale
            drawCircle(color = palette.dialWell, radius = wellRadius, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, palette.dialShadow.copy(alpha = 0.25f)),
                    center = center,
                    radius = wellRadius,
                ),
                radius = wellRadius,
                center = center,
            )
            drawCircle(
                color = palette.dialShadow.copy(alpha = 0.35f),
                radius = (WELL_RADIUS - 1.5f) * scale,
                center = center,
                style = Stroke(width = 3f * scale),
            )

            // 2) Raised knob body (the ticks sit on its rim): convex vertical
            //    shading plus top rim highlight / bottom rim shadow arcs.
            val knobRadius = KNOB_RADIUS * scale
            drawCircle(color = palette.dialFace, radius = knobRadius, center = center)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.dialSheen.copy(alpha = 0.10f),
                        Color.Transparent,
                        palette.dialShadow.copy(alpha = 0.25f),
                    ),
                    startY = cy - knobRadius,
                    endY = cy + knobRadius,
                ),
                radius = knobRadius,
                center = center,
            )
            val knobTopRim = (KNOB_RADIUS - 1.25f) * scale
            drawArc(
                color = palette.dialSheen.copy(alpha = 0.14f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - knobTopRim, cy - knobTopRim),
                size = Size(knobTopRim * 2f, knobTopRim * 2f),
                style = Stroke(width = 2.5f * scale, cap = StrokeCap.Round),
            )
            val knobBottomRim = (KNOB_RADIUS - 1.5f) * scale
            drawArc(
                color = palette.dialShadow.copy(alpha = 0.30f),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - knobBottomRim, cy - knobBottomRim),
                size = Size(knobBottomRim * 2f, knobBottomRim * 2f),
                style = Stroke(width = 3f * scale, cap = StrokeCap.Round),
            )

            // 3) Rotating tick ring (web: faceTransform rotate(rotation cx cy)).
            rotate(degrees = displayRotation.toFloat(), pivot = center) {
                for (i in 0 until TICKS_PER_ROTATION) {
                    val angle = (POINTER_DEG + i * DEG_PER_TICK) * PI / 180.0
                    val cosA = cos(angle).toFloat()
                    val sinA = sin(angle).toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(cx + TICK_INNER * scale * cosA, cy + TICK_INNER * scale * sinA),
                        end = Offset(cx + TICK_OUTER * scale * cosA, cy + TICK_OUTER * scale * sinA),
                        strokeWidth = 2f * scale,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // 4) Fixed pointer triangle at 12 o'clock: base corners sit just
            //    outside the tick band, apex pointing down toward the ticks.
            val pointerAngle = POINTER_DEG * PI / 180.0
            val dirX = cos(pointerAngle).toFloat()
            val dirY = sin(pointerAngle).toFloat()
            val perpX = -dirY
            val perpY = dirX
            val halfPointerWidth = POINTER_HALF_WIDTH * scale
            val pointerBaseX = cx + POINTER_BASE_RADIUS * scale * dirX
            val pointerBaseY = cy + POINTER_BASE_RADIUS * scale * dirY
            drawPath(
                path = Path().apply {
                    moveTo(
                        cx + POINTER_APEX_RADIUS * scale * dirX,
                        cy + POINTER_APEX_RADIUS * scale * dirY,
                    )
                    lineTo(pointerBaseX + halfPointerWidth * perpX, pointerBaseY + halfPointerWidth * perpY)
                    lineTo(pointerBaseX - halfPointerWidth * perpX, pointerBaseY - halfPointerWidth * perpY)
                    close()
                },
                color = inkColor,
            )

            // 5) Machined step ring between the knob face and the center cap.
            val stepRadius = STEP_RADIUS * scale
            drawCircle(color = palette.dialFace, radius = stepRadius, center = center)
            drawCircle(
                color = palette.dialShadow.copy(alpha = 0.15f),
                radius = stepRadius,
                center = center,
            )
            drawCircle(
                color = palette.dialShadow.copy(alpha = 0.25f),
                radius = (STEP_RADIUS - 1f) * scale,
                center = center,
                style = Stroke(width = 2f * scale),
            )

            // 6) Center TAP button: convex like the knob body but a step
            //    lighter than the step ring, subtler rim light/shadow.
            val tapRadius = TAP_RADIUS * scale
            drawCircle(color = palette.dialFace, radius = tapRadius, center = center)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.dialSheen.copy(alpha = 0.10f),
                        Color.Transparent,
                        palette.dialShadow.copy(alpha = 0.25f),
                    ),
                    startY = cy - tapRadius,
                    endY = cy + tapRadius,
                ),
                radius = tapRadius,
                center = center,
            )
            val tapEdgeRadius = (TAP_RADIUS - 1f) * scale
            drawArc(
                color = palette.dialSheen.copy(alpha = 0.10f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - tapEdgeRadius, cy - tapEdgeRadius),
                size = Size(tapEdgeRadius * 2f, tapEdgeRadius * 2f),
                style = Stroke(width = 2f * scale, cap = StrokeCap.Round),
            )
            drawArc(
                color = palette.dialShadow.copy(alpha = 0.20f),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - tapEdgeRadius, cy - tapEdgeRadius),
                size = Size(tapEdgeRadius * 2f, tapEdgeRadius * 2f),
                style = Stroke(width = 2f * scale, cap = StrokeCap.Round),
            )
            drawText(
                tapLabel,
                color = inkColor,
                topLeft = Offset(
                    cx - tapLabel.size.width / 2f,
                    cy - tapLabel.size.height / 2f,
                ),
            )
        }

        // TAP hit target, layered above the canvas so it receives its own
        // taps; the drag gesture keeps working everywhere outside this circle.
        Box(
            modifier = Modifier
                .size(tapHitDiameter)
                .clip(CircleShape)
                .clickable(enabled = onTap != null) { onTap?.invoke() }
                .semantics {
                    contentDescription = "Tap tempo"
                    role = Role.Button
                },
        )
    }
}
