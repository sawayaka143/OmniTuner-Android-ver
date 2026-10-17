package com.omnituner.android.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.SmoothShape

/**
 * The app's single-choice segmented control: stock M3 segments without the check icon, the
 * selected one filled with the accent, and squircle ends (a 16 dp continuous corner rather than a
 * full pill, whose ends would just be semicircles).
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
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = accent.accent,
        activeContentColor = accent.onAccent,
        activeBorderColor = accent.accent,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size, baseShape = SegmentShape),
                colors = colors,
                icon = {},
            ) {
                Text(label(option), maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

private val SegmentShape = SmoothShape(16.dp)
