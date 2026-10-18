package com.omnituner.android.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.SmoothShape

/**
 * The app's single-choice segmented control: equal segments on a soft neutral track, with one
 * accent pill behind the selected label that springs to the new segment (the bottom nav's sliding
 * pill, scaled down). No outline or dividers; squircle track and a concentric squircle pill.
 */
@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccentColors.current
    // Animate the index rather than a Dp offset, so a width change (rotation) moves the pill with
    // the layout instead of sliding it across from the old position.
    val pillIndex by animateFloatAsState(
        targetValue = options.indexOf(selected).coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = PillDamping, stiffness = PillStiffness),
        label = "segmentPill",
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha), TrackShape)
            .padding(TrackInset),
    ) {
        val slot = maxWidth / options.size
        Box(
            Modifier
                .offset { IntOffset((slot * pillIndex).roundToPx(), 0) }
                .width(slot)
                .fillMaxHeight()
                .background(accent.accent, PillShape),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) accent.onAccent else MaterialTheme.colorScheme.onSurface,
                    label = "segmentText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(option) },
                            role = Role.RadioButton,
                            interactionSource = null,
                            indication = ripple(),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// A little snappier than the nav pill (stiffness 120): these controls are smaller and used more
// often, so a ~400 ms settle with a hint of overshoot reads as responsive rather than floaty.
private const val PillStiffness = Spring.StiffnessLow
private const val PillDamping = 0.75f

// A translucent onSurface wash, so the track reads on both the page and a card in either theme.
private const val TrackAlpha = 0.08f

private val TrackHeight = 44.dp
private val TrackInset = 4.dp
private val TrackShape = SmoothShape(16.dp)
private val PillShape = SmoothShape(12.dp)
