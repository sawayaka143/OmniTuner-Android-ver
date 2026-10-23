package com.omnituner.android.ui.tuner

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.DEFAULT_REFERENCE_PITCH
import com.omnituner.android.data.REFERENCE_PITCH_RANGE
import com.omnituner.android.ui.metronome.BpmWheel
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape

/**
 * Settings → Reference pitch: the A4 frequency on the metronome's wheel, large in the middle of the
 * grouped-list page, with Hz under it. The wheel edits a draft: Set saves it and returns to
 * Settings, Back leaves the saved value alone, Reset puts the wheel back on 440 Hz.
 */
@Composable
fun ReferencePitchScreen(onDone: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val tuner by viewModel.tuner.collectAsState()
    var pitch by rememberSaveable { mutableIntStateOf(tuner.referencePitch) }

    BoxWithConstraints(
        modifier = Modifier
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
                text = "Adjust A4 note tuning",
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
                    value = pitch,
                    range = REFERENCE_PITCH_RANGE,
                    onValueChange = { pitch = it },
                    onCenterClick = {},
                    centerSize = numberSize,
                    pitch = (numberSize * 0.4f).coerceIn(24.dp, 36.dp),
                    label = "Reference pitch",
                    valueText = { "$it hertz" },
                    centerClickLabel = null,
                    centerColor = LocalAccentColors.current.accent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = "Hz",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { pitch = DEFAULT_REFERENCE_PITCH },
                    enabled = pitch != DEFAULT_REFERENCE_PITCH,
                    shape = SmoothShape.Full,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = LocalGroupedListColors.current.group,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.weight(1f).height(ButtonHeight),
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = {
                        viewModel.setReferencePitch(pitch)
                        onDone()
                    },
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
