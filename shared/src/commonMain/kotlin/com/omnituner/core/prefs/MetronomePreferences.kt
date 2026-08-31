package com.omnituner.core.prefs

import com.omnituner.core.metronome.BPM_MAX
import com.omnituner.core.metronome.BPM_MIN
import com.omnituner.core.metronome.DEFAULT_METRONOME_STATE
import com.omnituner.core.metronome.DIVISIONS_MAX
import com.omnituner.core.metronome.DIVISIONS_MIN
import com.omnituner.core.metronome.MetronomePreset
import com.omnituner.core.metronome.MetronomeSoundRoles
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.metronome.PATTERN_MAX_BARS
import com.omnituner.core.metronome.PATTERN_MIN_BARS
import com.omnituner.core.metronome.POLY_MAX
import com.omnituner.core.metronome.POLY_MIN
import com.omnituner.core.metronome.PRESETS_MAX
import com.omnituner.core.metronome.PolyState
import com.omnituner.core.metronome.RAMP_BARS_MAX
import com.omnituner.core.metronome.RAMP_BARS_MIN
import com.omnituner.core.metronome.SoundRole
import com.omnituner.core.metronome.TempoRamp
import com.omnituner.core.metronome.TimeSignature
import com.omnituner.core.metronome.isDenominator
import com.omnituner.core.sound.MetronomeVoices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val METRONOME_STORAGE_KEY = "omnituner.metronome.v1"
const val METRONOME_PRESET_ID_PREFIX = "mp-"

private fun clampBpm(v: Double): Int = min(BPM_MAX, max(BPM_MIN, v.roundToInt()))
private fun clampVolume(v: Double): Double = min(1.0, max(0.0, v))

object MetronomePreferencesSchema {

    fun readState(root: JsonObject?): MetronomeState {
        val fallback = DEFAULT_METRONOME_STATE
        val state = root?.field("state")?.asObject() ?: return fallback

        val bpm = state.field("bpm").asDouble()?.let(::clampBpm)?.toDouble() ?: fallback.bpm

        var numerator = fallback.timeSignature.numerator
        var denominator = fallback.timeSignature.denominator
        val timeSignature = state.field("timeSignature").asObject()
        if (timeSignature != null) {
            val num = timeSignature.field("numerator").asInt()
            val den = timeSignature.field("denominator").asInt()
            if (num != null && num >= 1 && num <= 32) numerator = num
            if (den != null && isDenominator(den)) denominator = den
        }

        val divisionsPerBeat = state.field("divisionsPerBeat").asInt()
            ?.let { min(DIVISIONS_MAX, max(1, it)) }
            ?: fallback.divisionsPerBeat

        var barPattern: List<Int> = fallback.barPattern
        val barPatternArray = state.field("barPattern").asArray()
            ?: state.field("pattern").asArray()
        if (barPatternArray != null) {
            val arr = barPatternArray.mapNotNull { it.asInt() }.filter { it == 0 || it == 1 }
            if (arr.size >= PATTERN_MIN_BARS && arr.size <= PATTERN_MAX_BARS) barPattern = arr
        } else {
            val boolArray = state.field("barPatternBool").asArray()
            if (boolArray != null) {
                val arr = boolArray.mapNotNull { it.asBoolean() }.map { if (it) 1 else 0 }
                if (arr.size >= PATTERN_MIN_BARS && arr.size <= PATTERN_MAX_BARS) barPattern = arr
            }
        }

        var poly: PolyState = fallback.poly
        val polyObject = state.field("poly").asObject()
        if (polyObject != null) {
            val enabled = polyObject.field("enabled").asBoolean() ?: fallback.poly.enabled
            val eventsRaw = polyObject.field("events").asInt()
            val events = if (eventsRaw != null && eventsRaw >= POLY_MIN && eventsRaw <= POLY_MAX) {
                eventsRaw
            } else {
                fallback.poly.events
            }
            val accentFirst = polyObject.field("accentFirst").asBoolean() ?: fallback.poly.accentFirst
            poly = PolyState(enabled, events, accentFirst)
        } else {
            val polyLegacy = state.field("polyLegacy").asObject()
            if (polyLegacy != null) {
                val enabled = polyLegacy.field("enabled").asBoolean() ?: fallback.poly.enabled
                val ratioB = polyLegacy.field("ratio").asObject()?.field("b")?.asInt()
                val events = ratioB?.let { min(POLY_MAX, max(POLY_MIN, it)) } ?: fallback.poly.events
                poly = PolyState(enabled, events, accentFirst = true)
            }
        }

        var sounds: MetronomeSoundRoles = fallback.sounds
        val soundsObject = state.field("sounds").asObject()
        if (soundsObject != null) {
            fun readRole(key: String, fb: SoundRole): SoundRole {
                val raw = soundsObject.field(key).asObject() ?: return fb
                val id = raw.field("id").asString()?.takeIf(MetronomeVoices::has) ?: fb.id
                val vol = raw.field("vol").asDouble()?.let(::clampVolume) ?: fb.vol
                val accentVol = raw.field("accentVol").asDouble()
                    ?.let(::clampVolume)
                    ?: fb.accentVol
                    ?: 1.0
                return SoundRole(id, vol, accentVol)
            }
            sounds = MetronomeSoundRoles(
                downbeat = readRole("downbeat", fallback.sounds.downbeat),
                beat = readRole("beat", fallback.sounds.beat),
                subdivision = readRole("subdivision", fallback.sounds.subdivision),
                poly = readRole("poly", fallback.sounds.poly),
            )
        }

        val masterVol = state.field("masterVol").asDouble()?.let(::clampVolume)
            ?: state.field("masterVolume").asDouble()?.let(::clampVolume)
            ?: fallback.masterVol

        val countIn = state.field("countIn").asBoolean() ?: fallback.countIn

        var ramp: TempoRamp = fallback.ramp
        val rampObject = state.field("ramp").asObject()
        if (rampObject != null) {
            val enabled = rampObject.field("enabled").asBoolean() ?: fallback.ramp.enabled
            val targetBpm = rampObject.field("targetBpm").asDouble()
                ?.let { clampBpm(it).toDouble() }
                ?: fallback.ramp.targetBpm
            val bars = rampObject.field("bars").asInt()
                ?.let { min(RAMP_BARS_MAX, max(RAMP_BARS_MIN, it)) }
                ?: fallback.ramp.bars
            ramp = TempoRamp(enabled, targetBpm, bars)
        }

        return MetronomeState(
            bpm = bpm,
            timeSignature = TimeSignature(numerator, denominator),
            divisionsPerBeat = divisionsPerBeat,
            barPattern = barPattern,
            poly = poly,
            sounds = sounds,
            masterVol = masterVol,
            countIn = countIn,
            ramp = ramp,
        )
    }

    fun readPresets(root: JsonObject?): List<MetronomePreset> {
        val presets = root?.field("presets")?.asArray() ?: return emptyList()
        val result = mutableListOf<MetronomePreset>()
        for (entry in presets) {
            val obj = entry.asObject() ?: continue
            val id = obj.field("id").asString()
            val name = obj.field("name").asString()?.trim().orEmpty()
            if (id.isNullOrEmpty() || name.isEmpty()) continue
            result.add(MetronomePreset(id, name, readState(obj)))
            if (result.size >= PRESETS_MAX) break
        }
        return result
    }

    fun stateJsonObject(state: MetronomeState): JsonObject = buildJsonObject {
        put("bpm", state.bpm)
        putJsonObject("timeSignature") {
            put("numerator", state.timeSignature.numerator)
            put("denominator", state.timeSignature.denominator)
        }
        put("divisionsPerBeat", state.divisionsPerBeat)
        putJsonArray("barPattern") {
            state.barPattern.forEach { add(it) }
        }
        putJsonObject("poly") {
            put("enabled", state.poly.enabled)
            put("events", state.poly.events)
            put("accentFirst", state.poly.accentFirst)
        }
        putJsonObject("sounds") {
            putJsonObject("downbeat") {
                put("id", state.sounds.downbeat.id)
                put("vol", state.sounds.downbeat.vol)
            }
            putJsonObject("beat") {
                put("id", state.sounds.beat.id)
                put("vol", state.sounds.beat.vol)
            }
            putJsonObject("subdivision") {
                put("id", state.sounds.subdivision.id)
                put("vol", state.sounds.subdivision.vol)
            }
            putJsonObject("poly") {
                put("id", state.sounds.poly.id)
                put("vol", state.sounds.poly.vol)
                put("accentVol", state.sounds.poly.accentVol ?: 1.0)
            }
        }
        put("masterVol", state.masterVol)
        put("countIn", state.countIn)
        putJsonObject("ramp") {
            put("enabled", state.ramp.enabled)
            put("targetBpm", state.ramp.targetBpm)
            put("bars", state.ramp.bars)
        }
    }

    fun serialize(state: MetronomeState, presets: List<MetronomePreset>): String =
        buildJsonObject {
            put("version", 3)
            put("state", stateJsonObject(state))
            putJsonArray("presets") {
                presets.forEach { preset ->
                    add(
                        buildJsonObject {
                            put("id", preset.id)
                            put("name", preset.name)
                            put("state", stateJsonObject(preset.state))
                        },
                    )
                }
            }
        }.toString()
}

class MetronomePreferences(private val storage: KeyValueStorage?) {

    private val state = MutableStateFlow(loadState())
    private val presets = MutableStateFlow(loadPresets())

    val stateFlow: StateFlow<MetronomeState> = state.asStateFlow()
    val presetsFlow: StateFlow<List<MetronomePreset>> = presets.asStateFlow()

    fun setBpm(bpm: Double) {
        if (!bpm.isFinite()) return
        update { it.copy(bpm = clampBpm(bpm).toDouble()) }
    }

    fun setCountIn(enabled: Boolean) = update { it.copy(countIn = enabled) }

    fun setRamp(enabled: Boolean? = null, targetBpm: Double? = null, bars: Int? = null) {
        val current = state.value.ramp
        update {
            it.copy(
                ramp = TempoRamp(
                    enabled = enabled ?: current.enabled,
                    targetBpm = targetBpm?.let(::clampBpm)?.toDouble() ?: current.targetBpm,
                    bars = bars?.let { b -> min(RAMP_BARS_MAX, max(RAMP_BARS_MIN, b)) }
                        ?: current.bars,
                ),
            )
        }
    }

    fun savePreset(name: String, idFactory: () -> String = { defaultPresetId() }): MetronomePreset? {
        val trimmed = name.trim()
        val finalName = trimmed.ifEmpty { "Preset ${presets.value.size + 1}" }
        val preset = MetronomePreset(idFactory(), finalName, state.value)
        presets.value = (listOf(preset) + presets.value).take(PRESETS_MAX)
        persist()
        return preset
    }

    fun applyPreset(id: String) {
        val preset = presets.value.find { it.id == id } ?: return
        update { preset.state }
    }

    fun deletePreset(id: String) {
        presets.value = presets.value.filter { it.id != id }
        persist()
    }

    /** Bulk state replacement for UI-view-model driven stores. */
    fun replaceState(state: MetronomeState) {
        this.state.value = state
        persist()
    }

    fun replacePresets(presets: List<MetronomePreset>) {
        this.presets.value = presets.take(PRESETS_MAX)
        persist()
    }

    fun setTimeSignature(numerator: Int, denominator: Int) {
        if (numerator < 1 || numerator > 32) return
        if (!isDenominator(denominator)) return
        update { it.copy(timeSignature = TimeSignature(numerator, denominator)) }
    }

    fun setDivisionsPerBeat(value: Int) {
        if (value < DIVISIONS_MIN) return
        update { it.copy(divisionsPerBeat = min(DIVISIONS_MAX, max(1, value))) }
    }

    fun setBarPattern(pattern: List<Int>) {
        if (pattern.size < PATTERN_MIN_BARS || pattern.size > PATTERN_MAX_BARS) return
        if (!pattern.all { it == 0 || it == 1 }) return
        update { it.copy(barPattern = pattern.toList()) }
    }

    fun setPoly(enabled: Boolean? = null, events: Int? = null, accentFirst: Boolean? = null) {
        val current = state.value.poly
        update {
            it.copy(
                poly = PolyState(
                    enabled = enabled ?: current.enabled,
                    events = events?.let { e -> min(POLY_MAX, max(POLY_MIN, e)) }
                        ?: current.events,
                    accentFirst = accentFirst ?: current.accentFirst,
                ),
            )
        }
    }

    fun setSoundRole(role: String, id: String, vol: Double? = null, accentVol: Double? = null) {
        if (!MetronomeVoices.has(id)) return
        val current = when (role) {
            "downbeat" -> state.value.sounds.downbeat
            "beat" -> state.value.sounds.beat
            "subdivision" -> state.value.sounds.subdivision
            "poly" -> state.value.sounds.poly
            else -> return
        }
        val next = SoundRole(
            id = id,
            vol = vol?.takeIf { it.isFinite() }?.let(::clampVolume) ?: current.vol,
            accentVol = accentVol?.takeIf { it.isFinite() }?.let(::clampVolume)
                ?: current.accentVol,
        )
        update { s ->
            s.copy(
                sounds = when (role) {
                    "downbeat" -> s.sounds.copy(downbeat = next)
                    "beat" -> s.sounds.copy(beat = next)
                    "subdivision" -> s.sounds.copy(subdivision = next)
                    else -> s.sounds.copy(poly = next)
                },
            )
        }
    }

    fun setMasterVol(volume: Double) {
        if (!volume.isFinite()) return
        update { it.copy(masterVol = clampVolume(volume)) }
    }

    private fun update(transform: (MetronomeState) -> MetronomeState) {
        state.value = transform(state.value)
        persist()
    }

    private fun loadState(): MetronomeState {
        storage ?: return DEFAULT_METRONOME_STATE
        return try {
            val raw = storage.getItem(METRONOME_STORAGE_KEY) ?: return DEFAULT_METRONOME_STATE
            val parsed = parseJsonOrNull(raw)?.asObject() ?: return DEFAULT_METRONOME_STATE
            val version = parsed.field("version").asInt()
            if (version == 3 || version == 2 || version == 1) {
                MetronomePreferencesSchema.readState(parsed)
            } else {
                DEFAULT_METRONOME_STATE
            }
        } catch (_: Exception) {
            DEFAULT_METRONOME_STATE
        }
    }

    private fun loadPresets(): List<MetronomePreset> {
        storage ?: return emptyList()
        return try {
            val raw = storage.getItem(METRONOME_STORAGE_KEY) ?: return emptyList()
            val parsed = parseJsonOrNull(raw)?.asObject() ?: return emptyList()
            if (parsed.field("version").asInt() == 3) {
                MetronomePreferencesSchema.readPresets(parsed)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist() {
        storage ?: return
        try {
            storage.setItem(
                METRONOME_STORAGE_KEY,
                MetronomePreferencesSchema.serialize(state.value, presets.value),
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        private var counter = 0L

        /** Common-main has no clock; the Android layer can inject richer ids via [savePreset]. */
        fun defaultPresetId(): String {
            counter += 1
            return "$METRONOME_PRESET_ID_PREFIX${counter.toString(36)}"
        }
    }
}
