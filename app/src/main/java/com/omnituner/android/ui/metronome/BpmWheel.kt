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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import kotlin.math.cosh
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

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
 *
 * [collapse] folds the wheel into a plain readout: at 1 the neighbours have slid into the centre and
 * faded out, the centre value is drawn [CompactScale] larger and only a tap on it (or a drag, which
 * the caller should answer by unfolding) does anything. [onInteractionChange] reports when a drag,
 * fling or row tap starts (true) and when the wheel has settled and committed (false).
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
    collapse: () -> Float = { 0f },
    onInteractionChange: (Boolean) -> Unit = {},
    centerColor: Color = Color.Unspecified,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnCenterClick by rememberUpdatedState(onCenterClick)
    val latestOnInteraction by rememberUpdatedState(onInteractionChange)
    val latestCollapse by rememberUpdatedState(collapse)

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

    fun setInteracting(active: Boolean) {
        if (interacting == active) return
        interacting = active
        latestOnInteraction(active)
    }

    fun commit() {
        val settled = range.first + position.value.roundToInt()
        setInteracting(false)
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
        // Folded there is no drum to roll, only the value: rolling would flick through every
        // number in between, so it just changes.
        if (position.value != target) {
            if (latestCollapse() > 0.5f) position.snapTo(target) else position.animateTo(target, SyncSpring)
        }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch { position.snapTo(position.value - delta / pitchPx) }
    }

    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = with(density) { centerSize.toSp() },
        lineHeight = with(density) { centerSize.toSp() },
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )

    BoxWithConstraints(
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
                onDragStarted = { setInteracting(true) },
                onDragStopped = { velocity -> scope.launch { fling(-velocity / pitchPx) } },
            )
            .pointerInput(bigPx, pitchPx, range) {
                detectTapGestures { offset ->
                    val dy = offset.y - size.height / 2f
                    val folded = latestCollapse() > 0.5f
                    val centerHalf = bigPx / 2f * if (folded) CompactScale else 1f
                    if (abs(dy) <= centerHalf) {
                        latestOnCenterClick()
                        return@detectTapGestures
                    }
                    // Folded, there are no neighbours to tap.
                    if (folded) return@detectTapGestures
                    val tapGeometry = DrumGeometry(bigPx, pitchPx, size.height / 2f)
                    val first = (position.value.roundToInt() - tapGeometry.visibleRows).coerceAtLeast(0)
                    val last = (position.value.roundToInt() + tapGeometry.visibleRows).coerceAtMost(maxIndex)
                    val row = (first..last).minBy { abs(tapGeometry.offset(it - position.value) - dy) }
                    setInteracting(true)
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
        val geometry = remember(bigPx, pitchPx, constraints.maxHeight) {
            DrumGeometry(bigPx, pitchPx, constraints.maxHeight / 2f)
        }
        val window = (centerRow - geometry.visibleRows).coerceAtLeast(0)..
            (centerRow + geometry.visibleRows).coerceAtMost(maxIndex)
        for (row in window) {
            key(row) {
                Text(
                    text = "${range.first + row}",
                    style = style,
                    color = if (row == centerRow) centerColor else Color.Unspecified,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        val d = row - position.value
                        val fold = collapse()
                        val focus = geometry.focus(d)
                        val angle = geometry.angle(d)
                        // Folding, the neighbours slide into the centre row and fade (faster than
                        // they slide, so they are gone before reaching it) while the centre grows.
                        val isCenter = abs(d) < 0.5f
                        translationY = geometry.offset(d) * (1f - fold)
                        val foldScale = if (isCenter) lerp(1f, CompactScale, fold) else lerp(1f, FoldedNeighbourScale, fold)
                        val scale = geometry.scale(d) * geometry.depth(angle) * lerp(1f, centerScale(), focus) * foldScale
                        scaleX = scale
                        scaleY = scale
                        rotationX = -Math.toDegrees(angle.toDouble()).toFloat()
                        cameraDistance = CameraDistance * this.density
                        val foldAlpha = if (isCenter) 1f else (1f - fold) * (1f - fold)
                        alpha = lerp(NeighbourAlpha, 1f, focus) * cos(angle).coerceAtLeast(0f) * foldAlpha
                    },
                )
            }
        }
    }
}

/**
 * Where and how large each row is drawn, for a row `d` rows from the centre (fractional while the
 * wheel moves).
 *
 * Size follows a bell curve ([focus]) instead of jumping over the last row, so a number grows
 * steadily as it approaches the centre. Rows are stacked by that size, so the spacing opens up
 * around the large number exactly as much as the numbers need: the distance along the drum is the
 * integral of row height (pitch + the extra size), and the drum then bends that onto a curve,
 * tilting rows away, pushing them back (smaller) and hiding them past 90°. The drum is a little
 * narrower than the wheel ([DrumRadiusShare]) so it curls visibly right next to the centre row; the
 * last rows bunch up inside the edge fade.
 */
private class DrumGeometry(private val bigPx: Float, private val pitchPx: Float, halfHeight: Float) {
    private val radius = halfHeight * DrumRadiusShare
    private val smallPx = NeighbourRatio * pitchPx

    /** 1 at the centre, easing to 0 a few rows out: how much of the centre's size a row has. */
    fun focus(d: Float): Float {
        val c = cosh(d / SizeFalloff)
        return 1f / (c * c)
    }

    // ∫ sech²(x/s) dx = s·tanh(x/s): the extra room the growing rows take, summed up to d.
    private fun arc(d: Float): Float =
        sign(d) * (pitchPx * abs(d) + (bigPx - smallPx) * SizeFalloff * tanh(abs(d) / SizeFalloff))

    fun angle(d: Float): Float = (arc(d) / radius).coerceIn(-MaxAngle, MaxAngle)

    fun offset(d: Float): Float = radius * sin(angle(d))

    fun scale(d: Float): Float = lerp(smallPx / bigPx, 1f, focus(d))

    /** Perspective shrink of a row turned [angle] away: it sits further back on the drum. */
    fun depth(angle: Float): Float = 1f / (1f + DepthShrink * (1f - cos(angle)))

    /** Rows each side of the centre that are still on the visible half of the drum. */
    val visibleRows: Int = run {
        var rows = 1
        while (rows < MaxVisibleRows && abs(arc(rows.toFloat())) < radius * MaxAngle) rows++
        rows
    }
}

private const val MaxAngle = (PI / 2).toFloat()
private const val MaxVisibleRows = 30

// How many rows out the size bell reaches: about half the centre's extra size is left one row out,
// a tenth two rows out. Larger values start the growth earlier.
private const val SizeFalloff = 1.2f

// Far rows' text height as a share of the row pitch, and their opacity.
private const val NeighbourRatio = 0.62f
private const val NeighbourAlpha = 0.45f
// Lower = stronger perspective on the tilted rows.
private const val CameraDistance = 6f
private const val EdgeFade = 0.12f

// Drum radius as a share of half the wheel's height, and how much smaller a row gets once it has
// turned 90° back (1 / (1 + DepthShrink)).
private const val DrumRadiusShare = 0.9f
private const val DepthShrink = 0.4f

// Folded readout: the centre value's growth and the size the neighbours shrink to as they leave.
const val CompactScale = 1.15f
private const val FoldedNeighbourScale = 0.6f

// Lower than 1 so a fling travels further than a stock list's, closer to the iOS picker.
private const val FlingFriction = 0.5f
private const val MinFlingVelocity = 1f // rows per second
private const val DecayStopVelocity = 0.5f // rows per second

private val SnapSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val SyncSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
