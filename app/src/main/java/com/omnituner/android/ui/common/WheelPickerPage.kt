package com.omnituner.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.omnituner.android.ui.metronome.BpmWheel
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape

/**
 * A one-number picker page: [title] over the metronome's wheel (large, in a selection band) with
 * [unit] under it, and a Set button, plus a Reset beside it when [onReset] is given. The wheel edits
 * [value], which the caller keeps as a draft; Set commits it. Used by Settings -> Reference pitch
 * and the metronome's Target BPM.
 */
@Composable
fun WheelPickerPage(
    title: String,
    unit: String,
    value: Int,
    range: IntRange,
    label: String,
    valueText: (Int) -> String,
    onValueChange: (Int) -> Unit,
    onSet: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    resetEnabled: Boolean = true,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Landscape leaves little height, so the wheel takes a share of it rather than a fixed size.
        val wheelHeight = min(WheelMaxHeight, maxHeight * 0.55f)
        val numberSize = (wheelHeight * 0.3f).coerceIn(48.dp, 88.dp)
        Column(
            modifier = Modifier.widthIn(max = MaxWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(wheelHeight),
                contentAlignment = Alignment.Center,
            ) {
                // Selection band behind the centre row, as on the metronome.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(numberSize + 20.dp)
                        .background(LocalGroupedListColors.current.group, BandShape),
                )
                BpmWheel(
                    value = value,
                    range = range,
                    onValueChange = onValueChange,
                    onCenterClick = {},
                    centerSize = numberSize,
                    pitch = (numberSize * 0.4f).coerceIn(24.dp, 36.dp),
                    label = label,
                    valueText = valueText,
                    centerClickLabel = null,
                    centerColor = LocalAccentColors.current.accent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = unit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onReset != null) {
                    FilledTonalButton(
                        onClick = onReset,
                        enabled = resetEnabled,
                        shape = SmoothShape.Full,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LocalGroupedListColors.current.group,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.weight(1f).height(ButtonHeight),
                    ) {
                        Text("Reset")
                    }
                }
                Button(
                    onClick = onSet,
                    shape = SmoothShape.Full,
                    modifier = Modifier.weight(1f).height(ButtonHeight),
                ) {
                    Text("Set")
                }
            }
        }
    }
}

private val WheelMaxHeight = 320.dp
private val ButtonHeight = 52.dp
private val MaxWidth = 640.dp
private val BandShape = SmoothShape(20.dp)
