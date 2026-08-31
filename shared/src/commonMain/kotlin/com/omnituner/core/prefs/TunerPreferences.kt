package com.omnituner.core.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

const val TUNER_PREFERENCES_STORAGE_KEY = "omnituner.tuner-preferences.v1"
const val TUNER_PREFERENCES_VERSION = 4

const val MIN_TUNER_MIDI_NOTE = 23
const val MAX_TUNER_MIDI_NOTE = 86
const val MAX_CUSTOM_TUNING_NAME_LENGTH = 40
const val MAX_CUSTOM_INSTRUMENT_NAME_LENGTH = 30
const val MIN_STRING_COUNT = 1
const val MAX_STRING_COUNT = 12

const val REFERENCE_PITCH_MIN = 415
const val REFERENCE_PITCH_MAX = 466
const val REFERENCE_PITCH_DEFAULT = 440

const val TUNER_TOLERANCE_MIN = 1
const val TUNER_TOLERANCE_MAX = 15
const val TUNER_HOLD_MIN = 0
const val TUNER_HOLD_MAX = 1500
const val TUNER_HOLD_STEP = 50

const val TUNER_MODE_AUTO = "auto"
const val TUNER_MODE_MANUAL = "manual"
const val TUNER_STARTUP_REMEMBER = "remember"

data class InTunePreferences(
    val enabled: Boolean = true,
    val sound: Boolean = true,
    val glow: Boolean = true,
    val color: String = "#7ecba8",
    val outOfTuneColor: String = "#ff8aab",
    val tolerance: Int = 5,
    val holdMs: Int = 500,
)

data class TunerSettings(
    val mode: String = TUNER_MODE_AUTO,
    val startupMode: String = TUNER_STARTUP_REMEMBER,
    val inTune: InTunePreferences = InTunePreferences(),
    val referencePitch: Int = REFERENCE_PITCH_DEFAULT,
)

val DEFAULT_TUNER_SETTINGS: TunerSettings = TunerSettings()

data class SavedCustomTuning(
    val id: String,
    val instrumentId: String,
    val name: String,
    val notes: List<Int>,
)

fun isTunerMode(value: String): Boolean = value == TUNER_MODE_AUTO || value == TUNER_MODE_MANUAL

fun isStartupMode(value: String): Boolean =
    value == TUNER_STARTUP_REMEMBER || isTunerMode(value)

fun clampTolerance(value: Double): Int =
    TUNER_TOLERANCE_MAX.coerceAtMost(TUNER_TOLERANCE_MIN.coerceAtLeast(value.roundToInt()))

fun clampHoldMs(value: Double): Int =
    TUNER_HOLD_MAX.coerceAtMost(TUNER_HOLD_MIN.coerceAtLeast(value.roundToInt()))

fun clampReferencePitch(value: Double): Int =
    REFERENCE_PITCH_MAX.coerceAtMost(REFERENCE_PITCH_MIN.coerceAtLeast(value.roundToInt()))

object TunerPreferencesSchema {

    fun readTunerSettings(root: JsonElement?): TunerSettings {
        val tuner = root?.asObject()?.field("tuner")?.asObject()
            ?: return DEFAULT_TUNER_SETTINGS
        val rawInTune = tuner.field("inTune").asObject()
        val defaults = DEFAULT_TUNER_SETTINGS

        val color = rawInTune.field("color").asString()?.hexColorOrNull()
            ?: defaults.inTune.color
        val outOfTuneColor = rawInTune.field("outOfTuneColor").asString()?.hexColorOrNull()
            ?: defaults.inTune.outOfTuneColor
        val tolerance = rawInTune.field("tolerance").asDouble()
            ?.let(::clampTolerance)
            ?: defaults.inTune.tolerance
        val holdMs = rawInTune.field("holdMs").asDouble()
            ?.let(::clampHoldMs)
            ?: defaults.inTune.holdMs
        val referencePitch = tuner.field("referencePitch").asDouble()
            ?.let(::clampReferencePitch)
            ?: defaults.referencePitch

        return TunerSettings(
            mode = tuner.field("mode").asString()?.takeIf(::isTunerMode) ?: defaults.mode,
            startupMode = tuner.field("startupMode").asString()?.takeIf(::isStartupMode)
                ?: defaults.startupMode,
            inTune = InTunePreferences(
                enabled = rawInTune.field("enabled").asBoolean() ?: defaults.inTune.enabled,
                sound = rawInTune.field("sound").asBoolean() ?: defaults.inTune.sound,
                glow = rawInTune.field("glow").asBoolean() ?: defaults.inTune.glow,
                color = color,
                outOfTuneColor = outOfTuneColor,
                tolerance = tolerance,
                holdMs = holdMs,
            ),
            referencePitch = referencePitch,
        )
    }

    fun serialize(settings: TunerSettings): String =
        buildJsonObject {
            put("version", TUNER_PREFERENCES_VERSION)
            putJsonObject("tuner") {
                put("mode", settings.mode)
                put("startupMode", settings.startupMode)
                put("referencePitch", settings.referencePitch)
                putJsonObject("inTune") {
                    put("enabled", settings.inTune.enabled)
                    put("sound", settings.inTune.sound)
                    put("glow", settings.inTune.glow)
                    put("color", settings.inTune.color)
                    put("outOfTuneColor", settings.inTune.outOfTuneColor)
                    put("tolerance", settings.inTune.tolerance)
                    put("holdMs", settings.inTune.holdMs)
                }
            }
        }.toString()
}

class TunerPreferences(private val storage: KeyValueStorage?) {

    private val state = MutableStateFlow(load())

    val tunerSettings: StateFlow<TunerSettings> = state.asStateFlow()

    fun setMode(mode: String) {
        if (!isTunerMode(mode)) return
        update { it.copy(mode = mode) }
    }

    fun setStartupMode(startupMode: String) {
        if (!isStartupMode(startupMode)) return
        update { it.copy(startupMode = startupMode) }
    }

    fun setReferencePitch(referencePitch: Double) {
        if (!referencePitch.isFinite()) return
        update { it.copy(referencePitch = clampReferencePitch(referencePitch)) }
    }

    fun setInTuneEnabled(enabled: Boolean) = updateInTune { it.copy(enabled = enabled) }

    fun setInTuneSound(sound: Boolean) = updateInTune { it.copy(sound = sound) }

    fun setInTuneGlow(glow: Boolean) = updateInTune { it.copy(glow = glow) }

    fun setInTuneColor(color: String) {
        val normalized = color.hexColorOrNull() ?: return
        updateInTune { it.copy(color = normalized) }
    }

    fun setOutOfTuneColor(color: String) {
        val normalized = color.hexColorOrNull() ?: return
        updateInTune { it.copy(outOfTuneColor = normalized) }
    }

    fun setInTuneTolerance(tolerance: Double) {
        if (!tolerance.isFinite()) return
        updateInTune { it.copy(tolerance = clampTolerance(tolerance)) }
    }

    fun setInTuneHoldMs(holdMs: Double) {
        if (!holdMs.isFinite()) return
        updateInTune { it.copy(holdMs = clampHoldMs(holdMs)) }
    }

    private fun update(transform: (TunerSettings) -> TunerSettings) {
        state.value = transform(state.value)
        persist()
    }

    private fun updateInTune(transform: (InTunePreferences) -> InTunePreferences) {
        state.value = state.value.let { it.copy(inTune = transform(it.inTune)) }
        persist()
    }

    private fun load(): TunerSettings {
        storage ?: return DEFAULT_TUNER_SETTINGS
        return try {
            val raw = storage.getItem(TUNER_PREFERENCES_STORAGE_KEY) ?: return DEFAULT_TUNER_SETTINGS
            readTunerSettings(raw)
        } catch (_: Exception) {
            DEFAULT_TUNER_SETTINGS
        }
    }

    private fun readTunerSettings(raw: String): TunerSettings =
        TunerPreferencesSchema.readTunerSettings(parseJsonOrNull(raw))

    private fun persist() {
        storage ?: return
        try {
            storage.setItem(TUNER_PREFERENCES_STORAGE_KEY, TunerPreferencesSchema.serialize(state.value))
        } catch (_: Exception) {
        }
    }
}
