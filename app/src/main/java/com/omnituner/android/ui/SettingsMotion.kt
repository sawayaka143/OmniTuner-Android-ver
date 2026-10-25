package com.omnituner.android.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Fraction of the page transition where the fade-through hands over: the old page has just faded
// out and the new one has not begun, so the layout can change there without a visible jump.
private const val HandOverFraction = FadeOutMillis.toFloat() / PageTransitionMillis

// Over the last third of the trip the gear cross-fades into the arrow.
private const val CrossFadeFrom = 2f / 3f

// Material's emphasized curve (the motion spec's path), so the trip starts briskly and settles.
private val EmphasizedEasing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

/**
 * How far the app stands between a tab (0) and the Settings family (1), tied to the NavHost's page
 * transition so the predictive back gesture seeks it along with the pages. Each page reports a
 * linear visibility on its own enter/exit transition; the motion runs from the leaving page's level
 * to the entering one's, so tab <-> tab and Settings <-> its sub-pages stay put.
 *
 * Read [progress] in draw, layout or `derivedStateOf` lambdas, so following it never recomposes.
 */
@Stable
internal class SettingsMotion(private val restingLevel: Float) {
    private class Page(val level: Float, val transition: Transition<EnterExitState>, val visibility: State<Float>)

    private val pages = mutableStateMapOf<String, Page>()

    /** Call first thing in each destination's `composable {}`. */
    @Composable
    fun Track(scope: AnimatedVisibilityScope, key: String, level: Float) {
        val transition = scope.transition
        val visibility = transition.animateFloat(
            transitionSpec = { tween(PageTransitionMillis, easing = LinearEasing) },
            label = "settingsMotion",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        DisposableEffect(key, transition) {
            pages[key] = Page(level, transition, visibility)
            onDispose { pages.remove(key) }
        }
    }

    private inline fun <R> resolve(block: (from: Float, to: Float, fraction: Float) -> R): R {
        var entering: Page? = null
        var leaving: Page? = null
        for (page in pages.values) {
            if (page.transition.targetState == EnterExitState.Visible) {
                entering = page
            } else if (leaving == null || page.visibility.value > leaving.visibility.value) {
                leaving = page
            }
        }
        return when {
            entering != null && leaving != null -> block(leaving.level, entering.level, entering.visibility.value)
            entering != null -> block(entering.level, entering.level, 1f)
            // The next page registers a frame after the leaving one starts to go: hold still.
            leaving != null -> block(leaving.level, leaving.level, 1f)
            else -> block(restingLevel, restingLevel, 1f)
        }
    }

    /** 0 on a tab, 1 on Settings and its sub-pages, eased in between. */
    val progress: Float
        get() = resolve { from, to, fraction -> from + (to - from) * EmphasizedEasing.transform(fraction) }

    /**
     * Whether the page on screen is a tab. It flips at the fade-through's hand-over, when neither
     * page is visible, so the space the rail and bars take can change unseen.
     */
    val tabPageShowing: Boolean
        get() = resolve { from, to, fraction -> (if (fraction < HandOverFraction) from else to) < 0.5f }
}

/**
 * Where the travelling button rests: its spot on the tabs (top bar or rail header) and the Back
 * spot on the Settings bar, as centres in root coordinates. Unknown until the slot is measured.
 */
@Stable
internal class ButtonSpots {
    var tab by mutableStateOf(Offset.Unspecified)
    var settings by mutableStateOf(Offset.Unspecified)

    // The rail slides on a layer translation, which its header slot's position would include, so
    // the slot is measured inside the rail's layer and added to the rail's untranslated position.
    var railOuter: LayoutCoordinates? = null
    var railInner: LayoutCoordinates? = null
    var railSlot: LayoutCoordinates? = null

    fun updateRail() {
        val outer = railOuter ?: return
        val inner = railInner ?: return
        val slot = railSlot ?: return
        if (!outer.isAttached || !inner.isAttached || !slot.isAttached) return
        tab = outer.localToRoot(inner.localPositionOf(slot, slot.center))
    }

    /** The button's centre at [progress]; falls back to whichever spot is known. */
    fun at(progress: Float): Offset {
        val from = tab
        val to = settings
        return when {
            from.isSpecified && to.isSpecified -> lerp(from, to, progress)
            from.isSpecified -> from
            else -> to
        }
    }
}

internal val LayoutCoordinates.center: Offset
    get() = Offset(size.width / 2f, size.height / 2f)

internal fun LayoutCoordinates.centerInRoot(): Offset = localToRoot(center)

// The stock small icon button: a 40 dp container inside a 48 dp touch target.
private val ButtonContainerSize = 40.dp

/** An empty spot the size of an icon button, reporting where it sits; the button travels over it. */
@Composable
internal fun ButtonSlot(onPlaced: (LayoutCoordinates) -> Unit) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(ButtonContainerSize)
            .onGloballyPositioned(onPlaced),
    )
}

/**
 * The one Settings / Back button, drawn above everything. It rolls counter-clockwise from its tab
 * spot to the Back spot over [motion] (a whole number of [turns], so it lands upright), the gear
 * cross-fading into the arrow over the last third, its container blending from the tab color to
 * the Settings one.
 */
@Composable
internal fun SettingsBackButton(
    motion: SettingsMotion,
    spots: ButtonSpots,
    turns: Int,
    settingsOpen: Boolean,
    settingsContainer: Color,
    onClick: () -> Unit,
) {
    val tabContainer = IconButtonDefaults.filledTonalIconButtonColors().containerColor
    val tint = MaterialTheme.colorScheme.onSurface
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                val centre = spots.at(motion.progress)
                // Left unplaced (so neither drawn nor tappable) until a spot has been measured.
                if (centre.isSpecified) {
                    placeable.place(
                        (centre.x - placeable.width / 2f).roundToInt(),
                        (centre.y - placeable.height / 2f).roundToInt(),
                    )
                }
            }
        },
        // The container is drawn below instead, so its color can follow the motion without
        // recomposing.
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawCircle(lerp(tabContainer, settingsContainer, motion.progress)) }
                .graphicsLayer { rotationZ = -360f * turns * motion.progress },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = if (settingsOpen) null else "Settings",
                tint = tint,
                modifier = Modifier.graphicsLayer { alpha = 1f - arrowAlpha(motion.progress) },
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (settingsOpen) "Back" else null,
                tint = tint,
                modifier = Modifier.graphicsLayer { alpha = arrowAlpha(motion.progress) },
            )
        }
    }
}

private fun arrowAlpha(progress: Float): Float = ((progress - CrossFadeFrom) / (1f - CrossFadeFrom)).coerceIn(0f, 1f)
