package com.omnituner.android.ui.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * The tempo readout as an iOS-style drum: the current value sits large in the middle while its
 * neighbours shrink, tilt and fade as they curve away. Drag or fling to change it (it always comes
 * to rest on a whole value), tap a neighbour to step to it, tap the centre for [onCenterClick].
 *
 * The app's one custom picker (see AGENTS.md): a slider can't land on an exact BPM across a
 * 1–800 range and Material 3 has no wheel.
 *
 * [value] is the committed value. While the user drags or flings, the wheel is the source of truth
 * and [onValueChange] fires once, when it settles; outside an interaction, external changes
 * (steppers, tap tempo, presets) animate the wheel to [value].
 */
@Composable
fun BpmWheel(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    onCenterClick: () -> Unit,
    centerSize: Dp,
    pitch: Dp,
    modifier: Modifier = Modifier,
    centerScale: () -> Float = { 1f },
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnCenterClick by rememberUpdatedState(onCenterClick)

    val maxIndex = range.last - range.first
    val position = remember(range) {
        Animatable((value.coerceIn(range) - range.first).toFloat()).apply {
            updateBounds(0f, maxIndex.toFloat())
        }
    }
    // True from the first touch until the wheel has settled and committed.
    var interacting by remember { mutableStateOf(false) }
    val centerRow by remember { derivedStateOf { position.value.roundToInt() } }

    val bigPx = with(density) { centerSize.toPx() }
    val pitchPx = with(density) { pitch.toPx() }

    fun commit() {
        val settled = range.first + position.value.roundToInt()
        interacting = false
        if (settled != latestValue) latestOnChange(settled)
    }

    // If a new drag interrupts this, animateTo throws and the commit is skipped: the drag owns the
    // wheel now and commits when it settles.
    suspend fun settleTo(row: Int) {
        position.animateTo(row.toFloat(), SnapSpring, initialVelocity = position.velocity)
        commit()
    }

    suspend fun fling(velocity: Float) {
        val start = position.value
        val natural = exponentialDecay<Float>(FlingFriction).calculateTargetValue(start, velocity)
        val target = natural.roundToInt().coerceIn(0, maxIndex)
        val distance = target - start
        if (abs(velocity) > MinFlingVelocity && distance != 0f && sign(distance) == sign(velocity)) {
            // Decay distance scales with 1 / friction, so retune the friction to come to rest on
            // the snapped row instead of decaying past it and springing back.
            val unit = exponentialDecay<Float>(1f).calculateTargetValue(0f, velocity)
            position.animateDecay(velocity, exponentialDecay(unit / distance, DecayStopVelocity))
        }
        settleTo(target)
    }

    // Tick per row the user moves through, like the system pickers.
    LaunchedEffect(position) {
        snapshotFlow { position.value.roundToInt() }.drop(1).collect {
            if (interacting) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LaunchedEffect(value) {
        if (interacting) return@LaunchedEffect
        val target = (value.coerceIn(range) - range.first).toFloat()
        if (position.value != target) position.animateTo(target, SyncSpring)
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch { position.snapTo(position.value - delta / pitchPx) }
    }

    val window = (centerRow - VisibleRows).coerceAtLeast(0)..(centerRow + VisibleRows).coerceAtMost(maxIndex)
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = with(density) { centerSize.toSp() },
        lineHeight = with(density) { centerSize.toSp() },
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )

    Box(
        modifier = modifier
            .clipToBounds()
            // Fade the drum out at its top and bottom edges rather than cutting rows off.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        EdgeFade to Color.Black,
                        1f - EdgeFade to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { interacting = true },
                onDragStopped = { velocity -> scope.launch { fling(-velocity / pitchPx) } },
            )
            .pointerInput(bigPx, pitchPx, range) {
                detectTapGestures { offset ->
                    val dy = offset.y - size.height / 2f
                    if (abs(dy) <= bigPx / 2f) {
                        latestOnCenterClick()
                        return@detectTapGestures
                    }
                    val first = (position.value.roundToInt() - VisibleRows).coerceAtLeast(0)
                    val last = (position.value.roundToInt() + VisibleRows).coerceAtMost(maxIndex)
                    val row = (first..last).minBy { abs(rowOffset(it - position.value, bigPx, pitchPx) - dy) }
                    interacting = true
                    scope.launch { settleTo(row) }
                }
            }
            .semantics {
                contentDescription = "Tempo"
                stateDescription = "$value BPM"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = range.first.toFloat()..range.last.toFloat(),
                    steps = maxIndex - 1,
                )
                setProgress { target ->
                    latestOnChange(target.roundToInt().coerceIn(range))
                    true
                }
                onClick(label = "Type a tempo") {
                    latestOnCenterClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        for (row in window) {
            key(row) {
                Text(
                    text = "${range.first + row}",
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        val d = row - position.value
                        val near = abs(d).coerceAtMost(1f)
                        val ease = near * near * (3f - 2f * near)
                        val angle = (d * RowAngle).coerceIn(-MaxAngle, MaxAngle)
                        translationY = rowOffset(d, bigPx, pitchPx)
                        val scale = lerp(1f, NeighbourRatio * pitchPx / bigPx, ease) * lerp(centerScale(), 1f, ease)
                        scaleX = scale
                        scaleY = scale
                        rotationX = -Math.toDegrees(angle.toDouble()).toFloat()
                        cameraDistance = CameraDistance * this.density
                        alpha = lerp(1f, NeighbourAlpha, ease) * cos(angle).coerceAtLeast(0f)
                    },
                )
            }
        }
    }
}

/**
 * Vertical offset from the wheel's centre of a row [d] rows away (fractional while moving). Rows
 * sit on a drum of radius pitch / angle, and every row but the centre one is pushed out far enough
 * to clear the large centre number, keeping the same gap the small rows have between each other.
 */
private fun rowOffset(d: Float, bigPx: Float, pitchPx: Float): Float {
    val near = abs(d).coerceAtMost(1f)
    val ease = near * near * (3f - 2f * near)
    val angle = (d * RowAngle).coerceIn(-MaxAngle, MaxAngle)
    val neighbour = NeighbourRatio * pitchPx
    val push = bigPx / 2f + (pitchPx - neighbour) + neighbour / 2f - pitchPx
    return (pitchPx / RowAngle) * sin(angle) + sign(d) * push * ease
}

// Angle between neighbouring rows on the drum (~21°); rows past 90° are hidden.
private const val RowAngle = 0.36f
private const val MaxAngle = (PI / 2).toFloat()
private val VisibleRows = (MaxAngle / RowAngle).toInt() + 1

// Small rows' text height as a share of the row pitch, and their opacity next to the centre.
private const val NeighbourRatio = 0.62f
private const val NeighbourAlpha = 0.5f
private const val CameraDistance = 12f
private const val EdgeFade = 0.12f

// Lower than 1 so a fling travels further than a stock list's, closer to the iOS picker.
private const val FlingFriction = 0.5f
private const val MinFlingVelocity = 1f // rows per second
private const val DecayStopVelocity = 0.5f // rows per second

private val SnapSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val SyncSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
