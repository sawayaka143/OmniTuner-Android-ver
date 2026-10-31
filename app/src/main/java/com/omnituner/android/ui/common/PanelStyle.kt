package com.omnituner.android.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape

/** Corner radius and header inset of the Tuner and Metronome tool panels; menus inside are concentric (corner - inset). */
val PanelCorner = 32.dp
val PanelHeaderInset = 12.dp

/**
 * Header of a stock M3 card (16 dp corner, 16 dp content padding): the header's controls sit 4 dp
 * in, so their press highlight is concentric with the corner (12 dp), and a [HeaderPicker] starts
 * its text 12 dp further in, on the card's 16 dp content edge.
 */
val CardHeaderInset = 4.dp
val CardHeaderPickerStart = 12.dp

/** End padding of a card header ending in a 48 dp icon button: its 40 dp circle lands 12 dp in. */
val CardHeaderButtonEnd = 8.dp

/** A tonal pill button on the grouped-list group color, for pages and dialogs sitting on the grouped-list page color. */
@Composable
fun GroupTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = SmoothShape.Full,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = LocalGroupedListColors.current.group,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        interactionSource = interactionSource,
        content = content,
    )
}
