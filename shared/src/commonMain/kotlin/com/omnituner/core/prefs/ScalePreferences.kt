package com.omnituner.core.prefs

import com.omnituner.core.data.SCALES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

const val SCALE_PREFERENCES_STORAGE_KEY = "omnituner.scales.v1"

const val MIN_TUNING_MIDI_NOTE = 24
const val MAX_TUNING_MIDI_NOTE = 84

const val ACCIDENTAL_SHARP = "sharp"
const val ACCIDENTAL_FLAT = "flat"
const val LABEL_MODE_NOTE_NAMES = "note-names"
const val LABEL_MODE_SCALE_DEGREES = "scale-degrees"

private val FRET_COUNTS = setOf(12, 15, 21)

private const val WORKBENCH_SCALE_MIN = 0.75
private const val WORKBENCH_SCALE_MAX = 1.3
const val WORKBENCH_SCALE_STEP = 0.05

data class ScalePreferencesState(
    val rootPitchClass: Int = 4,
    val scaleId: String = "major",
    val accidental: String = ACCIDENTAL_SHARP,
    val fretCount: Int = 12,
    val labelMode: String = LABEL_MODE_NOTE_NAMES,
    val showOutsideScale: Boolean = false,
    val accent: String = "#ede8d0",
    val rootNoteColor: String = "#ede8d0",
    val noteColor: String = "#3b3b3b",
    val bgColor: String? = null,
    val cardColor: String? = null,
    val workbenchScale: Double = 1.0,
    val chordRandomProgression: Boolean = true,
)

val DEFAULT_SCALE_PREFERENCES: ScalePreferencesState = ScalePreferencesState()

private fun clampWorkbenchScale(v: Double): Double =
    v.coerceIn(WORKBENCH_SCALE_MIN, WORKBENCH_SCALE_MAX)

private object LegacyColorDefaults {
    const val ACCENT = "#ffffff"
    const val ROOT_NOTE_COLOR = "#ffffff"
    const val NOTE_COLOR = "#2e2e28"
}

fun upgradeLegacyColors(state: ScalePreferencesState): ScalePreferencesState = state.copy(
    accent = if (state.accent == LegacyColorDefaults.ACCENT) {
        DEFAULT_SCALE_PREFERENCES.accent
    } else {
        state.accent
    },
    rootNoteColor = if (state.rootNoteColor == LegacyColorDefaults.ROOT_NOTE_COLOR) {
        DEFAULT_SCALE_PREFERENCES.rootNoteColor
    } else {
        state.rootNoteColor
    },
    noteColor = if (state.noteColor == LegacyColorDefaults.NOTE_COLOR) {
        DEFAULT_SCALE_PREFERENCES.noteColor
    } else {
        state.noteColor
    },
)

object ScalePreferencesSchema {

    fun parseState(root: JsonElement?): ScalePreferencesState? {
        val value = root?.asObject() ?: return null
        if (value.field("version").asInt() != 1) return null
        val state = value.field("state").asObject() ?: return null
        val defaults = DEFAULT_SCALE_PREFERENCES

        val rootPitchClass = state.field("rootPitchClass").asInt()
        val scaleId = state.field("scaleId").asString()
        val accidental = state.field("accidental").asString()
        val fretCount = state.field("fretCount").asInt()
        val accent = state.field("accent").asString()
        val rootNoteColor = state.field("rootNoteColor").asString()
        val noteColor = state.field("noteColor").asString()

        return ScalePreferencesState(
            rootPitchClass = if (rootPitchClass != null && rootPitchClass in 0..11) {
                rootPitchClass
            } else {
                defaults.rootPitchClass
            },
            scaleId = if (scaleId != null && SCALES.any { it.id == scaleId }) {
                scaleId
            } else {
                defaults.scaleId
            },
            accidental = if (accidental == ACCIDENTAL_FLAT || accidental == ACCIDENTAL_SHARP) {
                accidental
            } else {
                defaults.accidental
            },
            fretCount = if (fretCount != null && fretCount in FRET_COUNTS) fretCount else defaults.fretCount,
            labelMode = when (state.field("labelMode").asString()) {
                LABEL_MODE_NOTE_NAMES, LABEL_MODE_SCALE_DEGREES ->
                    state.field("labelMode").asString()!!
                else -> defaults.labelMode
            },
            showOutsideScale = state.field("showOutsideScale").asBoolean()
                ?: defaults.showOutsideScale,
            accent = accent?.hexColorOrNull() ?: defaults.accent,
            rootNoteColor = rootNoteColor?.hexColorOrNull() ?: defaults.rootNoteColor,
            noteColor = noteColor?.hexColorOrNull() ?: defaults.noteColor,
            bgColor = state.field("bgColor").asString()?.hexColorOrNull(),
            cardColor = state.field("cardColor").asString()?.hexColorOrNull(),
            workbenchScale = state.field("workbenchScale").asDouble()
                ?.let(::clampWorkbenchScale)
                ?: defaults.workbenchScale,
            chordRandomProgression = state.field("chordRandomProgression").asBoolean()
                ?: defaults.chordRandomProgression,
        )
    }

    fun serialize(state: ScalePreferencesState): String = buildJsonObject {
        put("version", 1)
        putJsonObject("state") {
            put("rootPitchClass", state.rootPitchClass)
            put("scaleId", state.scaleId)
            put("accidental", state.accidental)
            put("fretCount", state.fretCount)
            put("labelMode", state.labelMode)
            put("showOutsideScale", state.showOutsideScale)
            put("accent", state.accent)
            put("rootNoteColor", state.rootNoteColor)
            put("noteColor", state.noteColor)
            put("bgColor", state.bgColor)
            put("cardColor", state.cardColor)
            put("workbenchScale", state.workbenchScale)
            put("chordRandomProgression", state.chordRandomProgression)
        }
    }.toString()
}

class ScalePreferences(private val storage: KeyValueStorage?) {

    private val state = MutableStateFlow(load())

    val stateFlow: StateFlow<ScalePreferencesState> = state.asStateFlow()

    fun setRootPitchClass(rootPitchClass: Int) {
        if (rootPitchClass !in 0..11) return
        update { it.copy(rootPitchClass = rootPitchClass) }
    }

    fun setScaleId(scaleId: String) {
        if (scaleId.isEmpty() || SCALES.none { it.id == scaleId }) return
        update { it.copy(scaleId = scaleId) }
    }

    fun setAccidental(accidental: String) = update { it.copy(accidental = accidental) }

    fun setFretCount(fretCount: Int) {
        if (fretCount !in FRET_COUNTS) return
        update { it.copy(fretCount = fretCount) }
    }

    fun setLabelMode(labelMode: String) {
        if (labelMode != LABEL_MODE_NOTE_NAMES && labelMode != LABEL_MODE_SCALE_DEGREES) return
        update { it.copy(labelMode = labelMode) }
    }

    fun setShowOutsideScale(showOutsideScale: Boolean) =
        update { it.copy(showOutsideScale = showOutsideScale) }

    fun setAccent(accent: String) {
        val normalized = accent.hexColorOrNull() ?: return
        update { it.copy(accent = normalized) }
    }

    fun setRootNoteColor(rootNoteColor: String) {
        val normalized = rootNoteColor.hexColorOrNull() ?: return
        update { it.copy(rootNoteColor = normalized) }
    }

    fun setNoteColor(noteColor: String) {
        val normalized = noteColor.hexColorOrNull() ?: return
        update { it.copy(noteColor = normalized) }
    }

    fun setBgColor(bgColor: String?) {
        if (bgColor == null) {
            update { it.copy(bgColor = null) }
            return
        }
        val normalized = bgColor.hexColorOrNull() ?: return
        update { it.copy(bgColor = normalized) }
    }

    fun setCardColor(cardColor: String?) {
        if (cardColor == null) {
            update { it.copy(cardColor = null) }
            return
        }
        val normalized = cardColor.hexColorOrNull() ?: return
        update { it.copy(cardColor = normalized) }
    }

    fun setWorkbenchScale(scale: Double) {
        if (!scale.isFinite()) return
        val stepsPerUnit = 1.0 / WORKBENCH_SCALE_STEP
        val snapped =
            kotlin.math.round(clampWorkbenchScale(scale) * stepsPerUnit) / stepsPerUnit
        update { it.copy(workbenchScale = snapped) }
    }

    fun resetWorkbenchScale() = update { it.copy(workbenchScale = 1.0) }

    private fun update(transform: (ScalePreferencesState) -> ScalePreferencesState) {
        state.value = transform(state.value)
        persist()
    }

    private fun load(): ScalePreferencesState {
        storage ?: return DEFAULT_SCALE_PREFERENCES
        return try {
            val raw = storage.getItem(SCALE_PREFERENCES_STORAGE_KEY)
                ?: return DEFAULT_SCALE_PREFERENCES
            val parsed = parseJsonOrNull(raw)
            val parsedState = ScalePreferencesSchema.parseState(parsed)
            parsedState?.let(::upgradeLegacyColors) ?: DEFAULT_SCALE_PREFERENCES
        } catch (_: Exception) {
            DEFAULT_SCALE_PREFERENCES
        }
    }

    private fun persist() {
        storage ?: return
        try {
            storage.setItem(
                SCALE_PREFERENCES_STORAGE_KEY,
                ScalePreferencesSchema.serialize(state.value),
            )
        } catch (_: Exception) {
        }
    }
}
