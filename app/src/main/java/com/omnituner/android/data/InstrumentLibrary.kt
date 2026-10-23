package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnituner.core.audio.midiNoteToFrequency
import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.data.CustomInstrument
import com.omnituner.core.data.CustomTuning
import com.omnituner.core.data.INSTRUMENTS
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.NamedFrequency
import com.omnituner.core.data.Tuning
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InstrumentSelection(
    val instrumentId: String = "guitar",
    val tuningId: String = "standard",
)

data class InstrumentTunings(
    val instrument: Instrument,
    val tunings: List<Tuning>,
)

data class RecentTuning(
    val instrument: Instrument,
    val tuning: Tuning,
)

data class LibraryState(
    val instruments: List<Instrument>,
    val instrument: Instrument,
    val tunings: List<Tuning>,
    val tuning: Tuning,
    val catalog: List<InstrumentTunings>,
    /** Most recently picked first, starting with the current one; deleted entries drop out. */
    val recent: List<RecentTuning> = emptyList(),
)

const val MAX_RECENT_TUNINGS = 5

private val customInstrumentsKey = stringPreferencesKey("instruments.custom")
private val customTuningsKey = stringPreferencesKey("tunings.custom")
private val selectedInstrumentKey = stringPreferencesKey("instruments.selected")
private val selectedTuningKey = stringPreferencesKey("tunings.selected")
// "instrumentId|tuningId" per line, newest first. Ids are preset slugs or UUID-based, so neither
// separator can occur in them.
private val recentTuningsKey = stringPreferencesKey("tunings.recent")

class InstrumentLibrary(dataStore: DataStore<Preferences>, scope: CoroutineScope) {

    private val customInstruments = jsonSettingsStore<List<CustomInstrument>>(
        dataStore,
        scope,
        customInstrumentsKey,
        emptyList(),
    )
    private val customTunings = jsonSettingsStore<List<CustomTuning>>(
        dataStore,
        scope,
        customTuningsKey,
        emptyList(),
    )
    private val selection = SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = InstrumentSelection(),
        read = { prefs ->
            InstrumentSelection(
                instrumentId = prefs[selectedInstrumentKey] ?: "guitar",
                tuningId = prefs[selectedTuningKey] ?: "standard",
            )
        },
        write = { prefs, value ->
            prefs[selectedInstrumentKey] = value.instrumentId
            prefs[selectedTuningKey] = value.tuningId
        },
    )

    private val recentSelections = SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = emptyList<InstrumentSelection>(),
        read = { prefs ->
            prefs[recentTuningsKey].orEmpty().lines().mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size == 2) InstrumentSelection(parts[0], parts[1]) else null
            }
        },
        write = { prefs, value ->
            prefs[recentTuningsKey] = value.joinToString("\n") { "${it.instrumentId}|${it.tuningId}" }
        },
    )

    val state: StateFlow<LibraryState> = combine(
        customInstruments.flow,
        customTunings.flow,
        selection.flow,
        recentSelections.flow,
    ) { instruments, tunings, current, recent -> resolve(instruments, tunings, current, recent) }
        .stateIn(scope, SharingStarted.Eagerly, resolve(emptyList(), emptyList(), InstrumentSelection(), emptyList()))

    // The selection being left joins the recents, so going back to it is one tap.
    private suspend fun choose(next: InstrumentSelection) {
        val previous = selection.flow.value
        selection.update { next }
        recentSelections.update { list ->
            (listOf(next, previous) + list).distinct().take(MAX_RECENT_TUNINGS + 1)
        }
    }

    suspend fun selectInstrument(instrumentId: String) {
        val instrument = state.value.instruments.firstOrNull { it.id == instrumentId } ?: return
        val tunings = instrument.tunings +
            customTunings.flow.value.filter { it.instrumentId == instrumentId }.map { it.toTuning() }
        choose(InstrumentSelection(instrument.id, tunings.first().id))
    }

    suspend fun selectTuning(tuningId: String) {
        choose(selection.flow.value.copy(tuningId = tuningId))
    }

    suspend fun select(instrumentId: String, tuningId: String) {
        choose(InstrumentSelection(instrumentId, tuningId))
    }

    suspend fun saveInstrument(editingId: String?, name: String, stringCount: Int, notes: List<Int>) {
        val id = editingId ?: "instr-${UUID.randomUUID()}"
        val record = CustomInstrument(id, name, stringCount, notes)
        customInstruments.update { list ->
            if (list.any { it.id == id }) list.map { if (it.id == id) record else it } else list + record
        }
        choose(InstrumentSelection(id, "$id-default"))
    }

    suspend fun deleteInstrument(id: String) {
        customInstruments.update { list -> list.filterNot { it.id == id } }
        customTunings.update { list -> list.filterNot { it.instrumentId == id } }
    }

    suspend fun saveTuning(editingId: String?, instrumentId: String, name: String, notes: List<Int>) {
        val id = editingId ?: "custom-${UUID.randomUUID()}"
        val record = CustomTuning(id, instrumentId, name, notes)
        customTunings.update { list ->
            if (list.any { it.id == id }) list.map { if (it.id == id) record else it } else list + record
        }
        choose(selection.flow.value.copy(instrumentId = instrumentId, tuningId = id))
    }

    suspend fun deleteTuning(id: String) {
        customTunings.update { list -> list.filterNot { it.id == id } }
    }
}

private fun resolve(
    instruments: List<CustomInstrument>,
    tunings: List<CustomTuning>,
    current: InstrumentSelection,
    recentSelections: List<InstrumentSelection>,
): LibraryState {
    val all = INSTRUMENTS + instruments.map { it.toInstrument() }
    val catalog = all.map { entry ->
        InstrumentTunings(entry, entry.tunings + tunings.filter { it.instrumentId == entry.id }.map { it.toTuning() })
    }
    val instrument = all.firstOrNull { it.id == current.instrumentId } ?: INSTRUMENTS.first()
    val available = catalog.first { it.instrument.id == instrument.id }.tunings
    val tuning = available.firstOrNull { it.id == current.tuningId } ?: available.first()
    val recent = (listOf(InstrumentSelection(instrument.id, tuning.id)) + recentSelections)
        .distinct()
        .mapNotNull { pick ->
            val entry = catalog.firstOrNull { it.instrument.id == pick.instrumentId } ?: return@mapNotNull null
            entry.tunings.firstOrNull { it.id == pick.tuningId }?.let { RecentTuning(entry.instrument, it) }
        }
        .take(MAX_RECENT_TUNINGS)
    return LibraryState(all, instrument, available, tuning, catalog, recent)
}

private fun CustomInstrument.toInstrument(): Instrument = Instrument(
    id = id,
    label = name,
    stringCount = stringCount,
    tunings = listOf(
        Tuning(
            id = "$id-default",
            label = "Default",
            strings = defaultNotes.map { it.toNamedFrequency() },
            isCustom = true,
        ),
    ),
    isCustom = true,
)

private fun CustomTuning.toTuning(): Tuning = Tuning(
    id = id,
    label = name,
    strings = notes.map { it.toNamedFrequency() },
    isCustom = true,
)

private fun Int.toNamedFrequency(): NamedFrequency = NamedFrequency(midiNoteLabel(this), midiNoteToFrequency(this))
