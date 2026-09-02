package com.omnituner.android.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnituner.android.ui.theme.appTextStyle
import com.omnituner.android.ui.theme.currentWebPalette

private val SegShape = RoundedCornerShape(9999.dp)
private val SegTrackPadding = 4.dp
private val SegGap = 2.dp
private val SegHeight = 28.dp
private val SegMinSegmentWidth = 44.dp

private val SegSpring = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

data class WebSegmentedOption<T : Any>(
    val label: String,
    val key: T,
)

@Composable
fun <T : Any> WebSegmented(
    options: List<WebSegmentedOption<T>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = currentWebPalette()
    val density = LocalDensity.current
    val measured: SnapshotStateMap<Any, IntSize> = remember { mutableStateMapOf() }

    val widthsDp = options.associate { option ->
        val size = measured[option.key]
        option.key to (size?.let { with(density) { it.width.toDp() } } ?: 0.dp)
    }

    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val indicatorLeft = options.take(selectedIndex)
        .fold(0.dp) { acc, option -> acc + (widthsDp[option.key] ?: 0.dp) + SegGap }
    val indicatorWidth = widthsDp[selectedKey] ?: 0.dp
    val left by animateDpAsState(indicatorLeft, SegSpring, label = "segIndicatorLeft")
    val width by animateDpAsState(indicatorWidth, SegSpring, label = "segIndicatorWidth")

    Box(
        modifier = modifier
            .height(SegHeight)
            .background(palette.segTrackBg, SegShape)
            .border(1.dp, palette.borderSubtle, SegShape)
            .padding(SegTrackPadding),
    ) {
        if (width > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = left)
                    .width(width)
                    .fillMaxHeight()
                    .background(palette.segPillBg, SegShape),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { index, option ->
                if (index > 0) Spacer(Modifier.width(SegGap))
                val selected = option.key == selectedKey
                Box(
                    modifier = Modifier
                        .onSizeChanged { measured[option.key] = it }
                        .defaultMinSize(minWidth = SegMinSegmentWidth)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option.key) },
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = appTextStyle { copy(fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                        color = if (selected) palette.text else palette.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
