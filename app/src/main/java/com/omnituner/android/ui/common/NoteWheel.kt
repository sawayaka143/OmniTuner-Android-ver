package com.omnituner.android.ui.common

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
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
 * A compact horizontal drum that loops through [labels] (the twelve note names for a root): the
 * selected label sits bold in the middle while its neighbours shrink, turn and fade toward the
 * edges, and the ring has no ends, so B rolls on into C. Drag or fling (it always comes to rest on
 * a label) or tap a neighbour to step to it; [onSelect] fires once the drum has settled. The
 * label in the middle is drawn in [centerColor].
 *
 * The sideways sibling of the metronome's BpmWheel (see AGENTS.md): Material 3 has no wheel.
 */
@Composable
fun NoteWheel(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    centerColor: Color = Color.Unspecified,
) {
    val count = labels.size
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)

    // Unbounded row position; the label shown at row r is labels[r mod count].
    val position = remember { Animatable(selected.toFloat()) }
    var interacting by remember { mutableStateOf(false) }
    val centerRow by remember { derivedStateOf { position.value.roundToInt() } }

    fun commit() {
        interacting = false
        val settled = Math.floorMod(position.value.roundToInt(), count)
        if (settled != latestSelected) latestOnSelect(settled)
    }

    suspend fun settleTo(row: Int) {
        position.animateTo(row.toFloat(), SnapSpring, initialVelocity = position.velocity)
        commit()
    }

    suspend fun fling(velocity: Float) {
        val start = position.value
        val target = exponentialDecay<Float>(FlingFriction).calculateTargetValue(start, velocity).roundToInt()
        val distance = target - start
        if (abs(velocity) > MinFlingVelocity && distance != 0f && sign(distance) == sign(velocity)) {
            // Retune the decay to stop on the snapped row (see BpmWheel).
            val unit = exponentialDecay<Float>(1f).calculateTargetValue(0f, velocity)
            position.animateDecay(velocity, exponentialDecay(unit / distance, DecayStopVelocity))
        }
        settleTo(target)
    }

    // The nearest row showing [index], so outside changes take the short way round.
    fun nearestRow(index: Int): Int {
        val current = position.value.roundToInt()
        var delta = Math.floorMod(index - current, count)
        if (delta > count / 2) delta -= count
        return current + delta
    }

    LaunchedEffect(position) {
        snapshotFlow { position.value.roundToInt() }.drop(1).collect {
            if (interacting) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LaunchedEffect(selected) {
        if (interacting) return@LaunchedEffect
        if (Math.floorMod(position.value.roundToInt(), count) != selected || position.value % 1f != 0f) {
            position.animateTo(nearestRow(selected).toFloat(), SyncSpring)
        }
    }

    val textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    var widthPx by remember { mutableFloatStateOf(0f) }
    val radius = widthPx / 2f * DrumRadiusShare
    // Row spacing along the drum, so SideRows labels fit each side before it turns away.
    val pitchPx = (radius * MaxAngle / SideRows).coerceAtLeast(1f)
    fun angle(d: Float) = (d * pitchPx / radius.coerceAtLeast(1f)).coerceIn(-MaxAngle, MaxAngle)

    val dragState = rememberDraggableState { delta ->
        scope.launch { position.snapTo(position.value - delta / pitchPx) }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .clipToBounds()
            // Fade the drum out at its ends rather than cutting labels off.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
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
                orientation = Orientation.Horizontal,
                onDragStarted = { interacting = true },
                onDragStopped = { velocity -> scope.launch { fling(-velocity / pitchPx) } },
            )
            .pointerInput(pitchPx) {
                detectTapGestures { offset ->
                    val dx = offset.x - size.width / 2f
                    val base = position.value.roundToInt()
                    val reach = SideRows.toInt()
                    val row = (base - reach..base + reach)
                        .minBy { abs(radius * sin(angle(it - position.value)) - dx) }
                    if (row == base) return@detectTapGestures
                    interacting = true
                    scope.launch { settleTo(row) }
                }
            }
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = labels[Math.floorMod(selected, count)]
                customActions = listOf(
                    CustomAccessibilityAction("Previous") { latestOnSelect(Math.floorMod(latestSelected - 1, count)); true },
                    CustomAccessibilityAction("Next") { latestOnSelect(Math.floorMod(latestSelected + 1, count)); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = SideRows.toInt() + 1
        for (row in centerRow - side..centerRow + side) {
            key(row) {
                Text(
                    text = labels[Math.floorMod(row, count)],
                    style = textStyle,
                    color = if (row == centerRow) centerColor else Color.Unspecified,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        val d = row - position.value
                        val a = angle(d)
                        val focus = (1f - abs(d)).coerceIn(0f, 1f)
                        translationX = radius * sin(a)
                        val scale = lerp(NeighbourScale, 1f, focus) * (0.75f + 0.25f * cos(a))
                        scaleX = scale
                        scaleY = scale
                        rotationY = Math.toDegrees(a.toDouble()).toFloat()
                        cameraDistance = CameraDistance * this.density
                        alpha = lerp(NeighbourAlpha, 1f, focus) * cos(a).coerceAtLeast(0f)
                    },
                )
            }
        }
    }
}

private const val MaxAngle = (PI / 2).toFloat()
// Labels each side of the centre before the drum turns 90° away.
private const val SideRows = 4.5f
private const val DrumRadiusShare = 0.95f
private const val NeighbourScale = 0.7f
private const val NeighbourAlpha = 0.45f
private const val CameraDistance = 6f
private const val EdgeFade = 0.1f

private const val FlingFriction = 0.5f
private const val MinFlingVelocity = 1f // rows per second
private const val DecayStopVelocity = 0.5f // rows per second

private val SnapSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val SyncSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
