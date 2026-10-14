package com.omnituner.core.data

import kotlinx.serialization.Serializable

const val MIN_STRING_COUNT = 1
const val MAX_STRING_COUNT = 12
const val MAX_CUSTOM_INSTRUMENT_NAME_LENGTH = 30
const val MAX_CUSTOM_TUNING_NAME_LENGTH = 40
const val MIN_TUNER_MIDI_NOTE = 23
const val MAX_TUNER_MIDI_NOTE = 86

class InvalidInstrumentException(message: String) : Exception(message)

@Serializable
data class CustomInstrument(
    val id: String,
    val name: String,
    val stringCount: Int,
    val defaultNotes: List<Int>,
)

@Serializable
data class CustomTuning(
    val id: String,
    val instrumentId: String,
    val name: String,
    val notes: List<Int>,
)

fun validateInstrumentName(name: String): String {
    val normalized = name.trim()
    if (normalized.isEmpty()) throw InvalidInstrumentException("An instrument name is required.")
    if (normalized.length > MAX_CUSTOM_INSTRUMENT_NAME_LENGTH) {
        throw InvalidInstrumentException(
            "Instrument names must be $MAX_CUSTOM_INSTRUMENT_NAME_LENGTH characters or fewer.",
        )
    }
    return normalized
}

fun validateTuningName(name: String): String {
    val normalized = name.trim()
    if (normalized.isEmpty()) throw InvalidInstrumentException("A tuning name is required.")
    if (normalized.length > MAX_CUSTOM_TUNING_NAME_LENGTH) {
        throw InvalidInstrumentException(
            "Tuning names must be $MAX_CUSTOM_TUNING_NAME_LENGTH characters or fewer.",
        )
    }
    return normalized
}

fun validateStringCount(count: Int): Int {
    if (count !in MIN_STRING_COUNT..MAX_STRING_COUNT) {
        throw InvalidInstrumentException(
            "String count must be between $MIN_STRING_COUNT and $MAX_STRING_COUNT.",
        )
    }
    return count
}

fun validateNotes(notes: List<Int>, expectedCount: Int): List<Int> {
    val inRange = notes.all { it in MIN_TUNER_MIDI_NOTE..MAX_TUNER_MIDI_NOTE }
    if (notes.size != expectedCount || !inRange) {
        throw InvalidInstrumentException(
            "Tuning requires $expectedCount notes from MIDI $MIN_TUNER_MIDI_NOTE to $MAX_TUNER_MIDI_NOTE.",
        )
    }
    return notes.toList()
}
