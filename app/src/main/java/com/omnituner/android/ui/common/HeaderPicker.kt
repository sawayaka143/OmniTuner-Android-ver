package com.omnituner.android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnituner.core.data.Instrument

/**
 * The one header menu trigger: a small caption over the value it opens a sheet for, with a
 * drop-down arrow. Used in the top bar upright and on panel or card headers in landscape.
 *
 * The colors are set here rather than inherited: the top bar's actions slot hands its content the
 * grey action-icon color, which once left a tuning in the actions greyed out next to a white title.
 */
@Composable
fun HeaderPicker(
    caption: String,
    value: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    verticalPadding: Dp = 6.dp,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    // Set on the text too, not only the column: a value cut off with an ellipsis fills the column's
    // width, and would otherwise start at the left edge under a right-aligned caption.
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    Surface(
        onClick = onClick,
        // 20 dp: concentric with the 32 dp tool panels, whose headers sit 12 dp in.
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = ink,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = verticalPadding, bottom = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
            }
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = contentDescription, tint = ink)
        }
    }
}

/**
 * The instrument and tuning picker, read the same way on every tab: the instrument as the
 * caption, the tuning (what you actually tune to) as the value.
 */
@Composable
fun TuningHeaderPicker(
    instrument: Instrument,
    tuningLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    HeaderPicker(
        caption = instrument.menuTitle(),
        value = tuningLabel,
        onClick = onClick,
        contentDescription = "Choose instrument and tuning",
        modifier = modifier,
        alignEnd = alignEnd,
    )
}

/**
 * The title slot when a second picker shares the bar: [title] at the start, [secondary] against
 * the end. Both keep their full width when they fit; when they don't, the longer one gives way
 * (ending in an ellipsis), the title always keeping at least half. Both live in the title slot,
 * not the actions, because the top bar measures its actions without a width limit: a long tuning
 * name there once pushed the title and the buttons off the screen.
 */
@Composable
fun HeaderPickerRow(
    title: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(title, secondary), modifier = modifier) { (titleItems, secondaryItems), constraints ->
        val titleItem = titleItems.first()
        val secondaryItem = secondaryItems.first()
        val titleWanted = titleItem.maxIntrinsicWidth(constraints.maxHeight)
        val secondaryWanted = secondaryItem.maxIntrinsicWidth(constraints.maxHeight)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else titleWanted + secondaryWanted
        val titleWidth = if (titleWanted + secondaryWanted <= width) {
            titleWanted
        } else {
            minOf(titleWanted, maxOf(width / 2, width - secondaryWanted))
        }
        val titlePlaceable = titleItem.measure(constraints.copy(minWidth = 0, maxWidth = titleWidth))
        val secondaryPlaceable = secondaryItem.measure(
            constraints.copy(minWidth = 0, maxWidth = (width - titlePlaceable.width).coerceAtLeast(0)),
        )
        val height = maxOf(titlePlaceable.height, secondaryPlaceable.height)
        layout(width, height) {
            titlePlaceable.place(0, (height - titlePlaceable.height) / 2)
            secondaryPlaceable.place(width - secondaryPlaceable.width, (height - secondaryPlaceable.height) / 2)
        }
    }
}

internal fun Instrument.menuTitle(): String = "$label $stringCount-string"
