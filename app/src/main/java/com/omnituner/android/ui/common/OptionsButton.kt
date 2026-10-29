package com.omnituner.android.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The one way into a tool's options sheet: the same tune button on every tab, in the top bar
 * upright and in the panel or card header in landscape (where the rail replaces the bar).
 * On a grey panel or card pass the page color as [containerColor], or the tonal circle disappears
 * into the panel.
 */
@Composable
fun OptionsButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = if (containerColor == Color.Unspecified) {
            IconButtonDefaults.filledTonalIconButtonColors()
        } else {
            IconButtonDefaults.filledTonalIconButtonColors(containerColor = containerColor)
        },
    ) {
        Icon(Icons.Filled.Tune, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
    }
}
