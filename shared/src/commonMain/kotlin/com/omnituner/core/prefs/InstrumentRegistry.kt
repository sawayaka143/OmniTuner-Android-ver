package com.omnituner.core.prefs

import com.omnituner.core.audio.midiNoteLabel
import com.omnituner.core.audio.midiNoteToFrequency
import com.omnituner.core.data.INSTRUMENTS
import com.omnituner.core.data.Instrument
import com.omnituner.core.data.NamedFrequency
import com.omnituner.core.data.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add

const val INSTRUMENT_REGISTRY_STORAGE_KEY = "omnituner.instruments.v1"
const val OLD_TUNER_PREFERENCES_KEY = "omnituner.tuner-preferences.v1"

data class CustomInstrumentRecord(
    val id: String,
    val name: String,
    val stringCount: Int,
    val defaultNotes: List<Int>,
)

data class PersistedRegistry(
    val customInstruments: List<CustomInstrumentRecord>,
    val customTunings: List<SavedCustomTuning>,
    val selectedInstrumentId: String,
    val selectedTuningId: String,
)

class InstrumentNameException(message: String) : Exception(message)

private val CUSTOM_INSTRUMENT_ID = Regex("^instr-[a-z0-9-]+$", RegexOption.IGNORE_CASE)
private val CUSTOM_TUNING_ID = Regex("^custom-[a-z0-9-]+$", RegexOption.IGNORE_CASE)

private fun isMidiNote(value: Int): Boolean = value in MIN_TUNER_MIDI_NOTE..MAX_TUNER_MIDI_NOTE

private fun isStringCount(value: Int): Boolean = value in MIN_STRING_COUNT..MAX_STRING_COUNT

object InstrumentRegistrySchema {

    fun readCustomInstruments(value: JsonElement?): List<CustomInstrumentRecord> {
        val array = value?.asArray() ?: return emptyList()
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<CustomInstrumentRecord>()

        for (entry in array) {
            val obj = entry.asObject() ?: continue
            val id = obj.field("id").asString() ?: continue
            if (!CUSTOM_INSTRUMENT_ID.matches(id) || id in seenIds) continue
            val name = obj.field("name").asString() ?: continue
            val stringCount = obj.field("stringCount").asInt() ?: continue
            if (!isStringCount(stringCount)) continue
            val defaultNotes = obj.field("defaultNotes").asArray() ?: continue
            val notes = defaultNotes.mapNotNull { it.asInt() }
            if (notes.size != stringCount || !notes.all(::isMidiNote)) continue

            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_CUSTOM_INSTRUMENT_NAME_LENGTH) continue

            seenIds.add(id)
            result.add(CustomInstrumentRecord(id, trimmed, stringCount, notes))
        }

        return result
    }

    fun readCustomTunings(value: JsonElement?): List<SavedCustomTuning> {
        val array = value?.asArray() ?: return emptyList()
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<SavedCustomTuning>()

        for (entry in array) {
            val obj = entry.asObject() ?: continue
            val id = obj.field("id").asString() ?: continue
            if (!CUSTOM_TUNING_ID.matches(id) || id in seenIds) continue
            val instrumentId = obj.field("instrumentId").asString() ?: continue
            val name = obj.field("name").asString() ?: continue
            val notesArray = obj.field("notes").asArray() ?: continue
            val notes = notesArray.mapNotNull { it.asInt() }
            if (!notes.all(::isMidiNote)) continue

            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_CUSTOM_TUNING_NAME_LENGTH) continue

            seenIds.add(id)
            result.add(SavedCustomTuning(id, instrumentId, trimmed, notes))
        }

        return result
    }

    fun serialize(registry: PersistedRegistry): String = buildJsonObject {
        put("version", 1)
        putJsonArray("customInstruments") {
            registry.customInstruments.forEach { record ->
                add(
                    buildJsonObject {
                        put("id", record.id)
                        put("name", record.name)
                        put("stringCount", record.stringCount)
                        putJsonArray("defaultNotes") {
                            record.defaultNotes.forEach { add(it) }
                        }
                    },
                )
            }
        }
        putJsonArray("customTunings") {
            registry.customTunings.forEach { tuning ->
                add(
                    buildJsonObject {
                        put("id", tuning.id)
                        put("instrumentId", tuning.instrumentId)
                        put("name", tuning.name)
                        putJsonArray("notes") {
                            tuning.notes.forEach { add(it) }
                        }
                    },
                )
            }
        }
        put("selectedInstrumentId", registry.selectedInstrumentId)
        put("selectedTuningId", registry.selectedTuningId)
    }.toString()
}

class InstrumentRegistry(private val storage: KeyValueStorage?) {

    private val persisted: PersistedRegistry = load()

    private val customInstruments =
        MutableStateFlow(persisted.customInstruments)
    private val customTunings = MutableStateFlow(persisted.customTunings)
    private val selectedInstrumentId = MutableStateFlow(persisted.selectedInstrumentId)
    private val selectedTuningId = MutableStateFlow(persisted.selectedTuningId)

    val selectedInstrumentIdFlow: StateFlow<String> = selectedInstrumentId.asStateFlow()
    val selectedTuningIdFlow: StateFlow<String> = selectedTuningId.asStateFlow()
    val customTuningsFlow: StateFlow<List<SavedCustomTuning>> = customTunings.asStateFlow()

    fun instruments(): List<Instrument> = INSTRUMENTS + customInstruments.value.map { toInstrument(it) }

    fun selectedInstrument(): Instrument {
        val id = selectedInstrumentId.value
        return instruments().find { it.id == id } ?: INSTRUMENTS.first()
    }

    fun availableTunings(): List<Tuning> {
        val instrument = selectedInstrument()
        val custom = customTunings.value
            .filter { it.instrumentId == instrument.id }
            .map { toRuntimeTuning(it) }
        return instrument.tunings + custom
    }

    fun selectedTuning(): Tuning {
        val tunings = availableTunings()
        val id = selectedTuningId.value
        return tunings.find { it.id == id } ?: tunings.first()
    }

    fun selectInstrument(instrumentId: String) {
        val instrument = instruments().find { it.id == instrumentId } ?: return
        selectedInstrumentId.value = instrumentId

        val tunings = instrument.tunings + customTunings.value
            .filter { it.instrumentId == instrumentId }
            .map { toRuntimeTuning(it) }
        if (tunings.none { it.id == selectedTuningId.value }) {
            selectedTuningId.value = tunings.firstOrNull()?.id ?: "standard"
        }
        persist()
    }

    fun selectTuning(tuningId: String) {
        if (availableTunings().none { it.id == tuningId }) return
        selectedTuningId.value = tuningId
        persist()
    }

    fun createInstrument(name: String, stringCount: Int, defaultNotes: List<Int>): Instrument {
        val validName = requireInstrumentName(name)
        val validCount = requireStringCount(stringCount)
        val validNotes = requireNotes(defaultNotes, validCount)

        val record = CustomInstrumentRecord(createInstrumentId(), validName, validCount, validNotes)
        customInstruments.value = customInstruments.value + record
        persist()
        return toInstrument(record)
    }

    fun updateInstrument(id: String, name: String, stringCount: Int, defaultNotes: List<Int>): Instrument {
        val instruments = customInstruments.value.toMutableList()
        val index = instruments.indexOfFirst { it.id == id }
        if (index == -1) throw InstrumentNameException("Custom instrument does not exist.")

        val updated = CustomInstrumentRecord(
            id,
            requireInstrumentName(name),
            requireStringCount(stringCount),
            requireNotes(defaultNotes, stringCount),
        )
        instruments[index] = updated
        customInstruments.value = instruments
        persist()
        return toInstrument(updated)
    }

    fun deleteInstrument(id: String) {
        val next = customInstruments.value.filter { it.id != id }
        if (next.size == customInstruments.value.size) return

        customInstruments.value = next
        customTunings.value = customTunings.value.filter { it.instrumentId != id }

        if (selectedInstrumentId.value == id) {
            selectedInstrumentId.value = "guitar"
            selectedTuningId.value = "standard"
        }
        persist()
    }

    fun tuningsForInstrument(instrumentId: String): List<SavedCustomTuning> =
        customTunings.value.filter { it.instrumentId == instrumentId }

    fun createTuning(instrumentId: String, name: String, notes: List<Int>): SavedCustomTuning {
        val instrument = instruments().find { it.id == instrumentId }
            ?: throw InstrumentNameException("Instrument does not exist.")

        val tuning = SavedCustomTuning(
            id = createTuningId(),
            instrumentId = instrumentId,
            name = requireTuningName(name),
            notes = requireNotes(notes, instrument.stringCount),
        )
        customTunings.value = customTunings.value + tuning
        persist()
        return tuning
    }

    fun updateTuning(id: String, name: String, notes: List<Int>): SavedCustomTuning {
        val tunings = customTunings.value.toMutableList()
        val index = tunings.indexOfFirst { it.id == id }
        if (index == -1) throw InstrumentNameException("Custom tuning does not exist.")

        val existing = tunings[index]
        val instrument = instruments().find { it.id == existing.instrumentId }
        val stringCount = instrument?.stringCount ?: notes.size

        val updated = existing.copy(
            name = requireTuningName(name),
            notes = requireNotes(notes, stringCount),
        )
        tunings[index] = updated
        customTunings.value = tunings
        persist()
        return updated
    }

    fun deleteTuning(id: String) {
        val next = customTunings.value.filter { it.id != id }
        if (next.size == customTunings.value.size) return

        customTunings.value = next

        if (selectedTuningId.value == id) {
            val instrument = selectedInstrument()
            selectedTuningId.value = instrument.tunings.firstOrNull()?.id ?: "standard"
        }
        persist()
    }

    private fun toInstrument(record: CustomInstrumentRecord): Instrument = Instrument(
        id = record.id,
        label = record.name,
        stringCount = record.stringCount,
        kind = "custom",
        tunings = listOf(
            Tuning(
                id = "${record.id}-default",
                label = "DEFAULT",
                strings = record.defaultNotes.map { note ->
                    NamedFrequency(midiNoteLabel(note), midiNoteToFrequency(note))
                },
            ),
        ),
    )

    private fun toRuntimeTuning(tuning: SavedCustomTuning): Tuning = Tuning(
        id = tuning.id,
        label = tuning.name,
        kind = "custom",
        strings = tuning.notes.map { note ->
            NamedFrequency(midiNoteLabel(note), midiNoteToFrequency(note))
        },
    )

    private fun requireInstrumentName(name: String): String {
        val normalized = name.trim()
        if (normalized.isEmpty()) {
            throw InstrumentNameException("An instrument name is required.")
        }
        if (normalized.length > MAX_CUSTOM_INSTRUMENT_NAME_LENGTH) {
            throw InstrumentNameException(
                "Instrument names must be $MAX_CUSTOM_INSTRUMENT_NAME_LENGTH characters or fewer.",
            )
        }
        return normalized
    }

    private fun requireTuningName(name: String): String {
        val normalized = name.trim()
        if (normalized.isEmpty()) {
            throw InstrumentNameException("A custom tuning name is required.")
        }
        if (normalized.length > MAX_CUSTOM_TUNING_NAME_LENGTH) {
            throw InstrumentNameException(
                "Custom tuning names must be $MAX_CUSTOM_TUNING_NAME_LENGTH characters or fewer.",
            )
        }
        return normalized
    }

    private fun requireStringCount(count: Int): Int {
        if (!isStringCount(count)) {
            throw InstrumentNameException(
                "String count must be between $MIN_STRING_COUNT and $MAX_STRING_COUNT.",
            )
        }
        return count
    }

    private fun requireNotes(notes: List<Int>, expectedCount: Int): List<Int> {
        if (notes.size != expectedCount || !notes.all(::isMidiNote)) {
            throw InstrumentNameException(
                "Tuning requires $expectedCount MIDI notes from " +
                    "$MIN_TUNER_MIDI_NOTE to $MAX_TUNER_MIDI_NOTE.",
            )
        }
        return notes.toList()
    }

    private var idCounter = 0L

    private fun createInstrumentId(): String {
        idCounter += 1
        return "instr-${idCounter.toString(36)}"
    }

    private fun createTuningId(): String {
        idCounter += 1
        return "custom-${idCounter.toString(36)}"
    }

    private fun fallbackRegistry(): PersistedRegistry = PersistedRegistry(
        customInstruments = emptyList(),
        customTunings = emptyList(),
        selectedInstrumentId = "guitar",
        selectedTuningId = "standard",
    )

    private fun load(): PersistedRegistry {
        storage ?: return fallbackRegistry()

        return try {
            val raw = storage.getItem(INSTRUMENT_REGISTRY_STORAGE_KEY)
                ?: return migrateOldTunings(fallbackRegistry())

            val parsed = parseJsonOrNull(raw)?.asObject() ?: return fallbackRegistry()
            if (parsed.field("version").asInt() != 1) return fallbackRegistry()

            val customInstruments = InstrumentRegistrySchema.readCustomInstruments(
                parsed.field("customInstruments"),
            )
            val customTunings = InstrumentRegistrySchema.readCustomTunings(
                parsed.field("customTunings"),
            )

            val rawInstrumentId = parsed.field("selectedInstrumentId").asString() ?: "guitar"
            val selectedInstrumentIdValue =
                if (INSTRUMENTS.any { it.id == rawInstrumentId } ||
                    customInstruments.any { it.id == rawInstrumentId }
                ) {
                    rawInstrumentId
                } else {
                    "guitar"
                }

            val builtInTunings =
                INSTRUMENTS.find { it.id == selectedInstrumentIdValue }?.tunings ?: emptyList()
            val validTuningIds = buildSet {
                addAll(builtInTunings.map { it.id })
                customInstruments
                    .filter { it.id == selectedInstrumentIdValue }
                    .forEach { add("${it.id}-default") }
                customTunings
                    .filter { it.instrumentId == selectedInstrumentIdValue }
                    .forEach { add(it.id) }
            }
            val rawTuningId = parsed.field("selectedTuningId").asString() ?: "standard"
            val resolvedFallback = builtInTunings.firstOrNull()?.id
                ?: customTunings
                    .filter { it.instrumentId == selectedInstrumentIdValue }
                    .map { it.id }
                    .firstOrNull()
                ?: "standard"
            val selectedTuningIdValue =
                if (rawTuningId in validTuningIds) rawTuningId else resolvedFallback

            PersistedRegistry(
                customInstruments = customInstruments,
                customTunings = customTunings,
                selectedInstrumentId = selectedInstrumentIdValue,
                selectedTuningId = selectedTuningIdValue,
            )
        } catch (_: Exception) {
            fallbackRegistry()
        }
    }

    private fun migrateOldTunings(fallback: PersistedRegistry): PersistedRegistry {
        storage ?: return fallback

        return try {
            val oldRaw = storage.getItem(OLD_TUNER_PREFERENCES_KEY) ?: return fallback
            val oldParsed = parseJsonOrNull(oldRaw)?.asObject() ?: return fallback
            val oldTunings = oldParsed.field("tunings").asArray() ?: return fallback

            val migrated = InstrumentRegistrySchema.readCustomTunings(oldTunings)
            if (migrated.isEmpty()) return fallback

            val result = fallback.copy(customTunings = migrated)
            storage.setItem(INSTRUMENT_REGISTRY_STORAGE_KEY, InstrumentRegistrySchema.serialize(result))
            result
        } catch (_: Exception) {
            fallback
        }
    }

    private fun persist() {
        storage ?: return
        try {
            val value = PersistedRegistry(
                customInstruments = customInstruments.value,
                customTunings = customTunings.value,
                selectedInstrumentId = selectedInstrumentId.value,
                selectedTuningId = selectedTuningId.value,
            )
            storage.setItem(INSTRUMENT_REGISTRY_STORAGE_KEY, InstrumentRegistrySchema.serialize(value))
        } catch (_: Exception) {
        }
    }
}
