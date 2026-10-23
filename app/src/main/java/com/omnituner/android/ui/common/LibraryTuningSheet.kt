package com.omnituner.android.ui.common

import androidx.compose.runtime.Composable
import com.omnituner.android.data.InstrumentTunings

/**
 * The instrument-and-tuning picker shared by Chords and Scales: every instrument is a section with
 * its tunings (string notes underneath), so one tap sets both.
 */
@Composable
fun LibraryTuningSheet(
    catalog: List<InstrumentTunings>,
    instrumentId: String,
    tuningId: String,
    onSelect: (instrumentId: String, tuningId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = catalog.flatMap { entry ->
        entry.tunings.map { tuning ->
            PickerOption(
                key = entry.instrument.id to tuning.id,
                label = tuning.label,
                supporting = tuning.strings.joinToString(" ") { it.name },
                section = "${entry.instrument.label} ${entry.instrument.stringCount}-string",
            )
        }
    }
    OptionSheet(
        title = "Tuning",
        options = options,
        selected = instrumentId to tuningId,
        onSelect = { (instrument, tuning) -> onSelect(instrument, tuning) },
        onDismiss = onDismiss,
    )
}
