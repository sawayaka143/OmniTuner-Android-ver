package com.omnituner.android.ui.tuner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.data.DEFAULT_REFERENCE_PITCH
import com.omnituner.android.data.REFERENCE_PITCH_RANGE
import com.omnituner.android.ui.common.WheelPickerPage

/**
 * Settings -> Reference pitch: the A4 frequency on the metronome's wheel (see [WheelPickerPage]).
 * The wheel edits a draft: Set saves it and returns to Settings, Back leaves the saved value alone,
 * Reset puts the wheel back on 440 Hz.
 */
@Composable
fun ReferencePitchScreen(onDone: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val tuner by viewModel.tuner.collectAsState()
    var pitch by rememberSaveable { mutableIntStateOf(tuner.referencePitch) }

    WheelPickerPage(
        title = "Adjust A4 note tuning",
        unit = "Hz",
        value = pitch,
        range = REFERENCE_PITCH_RANGE,
        label = "Reference pitch",
        valueText = { "$it hertz" },
        onValueChange = { pitch = it },
        onSet = {
            viewModel.setReferencePitch(pitch)
            onDone()
        },
        onReset = { pitch = DEFAULT_REFERENCE_PITCH },
        resetEnabled = pitch != DEFAULT_REFERENCE_PITCH,
    )
}
