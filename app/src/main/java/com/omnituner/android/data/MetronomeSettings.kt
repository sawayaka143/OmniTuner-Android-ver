package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnituner.core.metronome.BPM_MAX
import com.omnituner.core.metronome.BPM_MIN
import com.omnituner.core.metronome.DEFAULT_METRONOME_STATE
import com.omnituner.core.metronome.DIVISIONS_MAX
import com.omnituner.core.metronome.DIVISIONS_MIN
import com.omnituner.core.metronome.MetronomePreset
import com.omnituner.core.metronome.MetronomeSoundRoles
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.metronome.PATTERN_MAX_BARS
import com.omnituner.core.metronome.POLY_MAX
import com.omnituner.core.metronome.POLY_MIN
import com.omnituner.core.metronome.PolyState
import com.omnituner.core.metronome.RAMP_BARS_MAX
import com.omnituner.core.metronome.RAMP_BARS_MIN
import com.omnituner.core.metronome.SoundRole
import com.omnituner.core.metronome.TempoRamp
import com.omnituner.core.metronome.TimeSignature
import com.omnituner.core.metronome.isDenominator
import com.omnituner.core.sound.MetronomeVoices
import kotlinx.coroutines.CoroutineScope

private const val NUMERATOR_MIN = 1
private const val NUMERATOR_MAX = 32

private val bpmKey = doublePreferencesKey("metronome.bpm")
private val numeratorKey = intPreferencesKey("metronome.numerator")
private val denominatorKey = intPreferencesKey("metronome.denominator")
private val divisionsKey = intPreferencesKey("metronome.divisions")
private val patternKey = stringPreferencesKey("metronome.pattern")
private val polyEnabledKey = booleanPreferencesKey("metronome.poly.enabled")
private val polyEventsKey = intPreferencesKey("metronome.poly.events")
private val polyAccentFirstKey = booleanPreferencesKey("metronome.poly.accentFirst")
private val masterVolKey = doublePreferencesKey("metronome.masterVol")
private val countInKey = booleanPreferencesKey("metronome.countIn")
private val rampEnabledKey = booleanPreferencesKey("metronome.ramp.enabled")
private val rampTargetKey = doublePreferencesKey("metronome.ramp.targetBpm")
private val rampBarsKey = intPreferencesKey("metronome.ramp.bars")
private val presetsKey = stringPreferencesKey("metronome.presets")

private class SoundKeys(role: String) {
    val id = stringPreferencesKey("metronome.sound.$role.id")
    val vol = doublePreferencesKey("metronome.sound.$role.vol")
    val accentVol = doublePreferencesKey("metronome.sound.$role.accentVol")
}

private val downbeatKeys = SoundKeys("downbeat")
private val beatKeys = SoundKeys("beat")
private val subdivisionKeys = SoundKeys("subdivision")
private val polyKeys = SoundKeys("poly")

internal fun metronomeStateStore(dataStore: DataStore<Preferences>, scope: CoroutineScope): SettingsStore<MetronomeState> =
    SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = DEFAULT_METRONOME_STATE,
        read = ::readMetronomeState,
        write = ::writeMetronomeState,
    )

internal fun metronomePresetStore(
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
): SettingsStore<List<MetronomePreset>> = jsonSettingsStore(dataStore, scope, presetsKey, emptyList())

private fun readMetronomeState(prefs: Preferences): MetronomeState {
    val defaults = DEFAULT_METRONOME_STATE
    val denominator = (prefs[denominatorKey] ?: defaults.timeSignature.denominator)
        .takeIf(::isDenominator) ?: defaults.timeSignature.denominator
    val pattern = prefs[patternKey]
        ?.split(",")
        ?.mapNotNull { it.toIntOrNull() }
        ?.filter { it == 0 || it == 1 }
        ?.take(PATTERN_MAX_BARS)
        ?.takeIf { it.isNotEmpty() }
        ?: defaults.barPattern

    return MetronomeState(
        bpm = (prefs[bpmKey] ?: defaults.bpm).coerceIn(BPM_MIN.toDouble(), BPM_MAX.toDouble()),
        timeSignature = TimeSignature(
            numerator = (prefs[numeratorKey] ?: defaults.timeSignature.numerator).coerceIn(NUMERATOR_MIN, NUMERATOR_MAX),
            denominator = denominator,
        ),
        divisionsPerBeat = (prefs[divisionsKey] ?: defaults.divisionsPerBeat).coerceIn(DIVISIONS_MIN, DIVISIONS_MAX),
        barPattern = pattern,
        poly = PolyState(
            enabled = prefs[polyEnabledKey] ?: defaults.poly.enabled,
            events = (prefs[polyEventsKey] ?: defaults.poly.events).coerceIn(POLY_MIN, POLY_MAX),
            accentFirst = prefs[polyAccentFirstKey] ?: defaults.poly.accentFirst,
        ),
        sounds = MetronomeSoundRoles(
            downbeat = readRole(prefs, downbeatKeys, defaults.sounds.downbeat),
            beat = readRole(prefs, beatKeys, defaults.sounds.beat),
            subdivision = readRole(prefs, subdivisionKeys, defaults.sounds.subdivision),
            poly = readRole(prefs, polyKeys, defaults.sounds.poly),
        ),
        masterVol = (prefs[masterVolKey] ?: defaults.masterVol).coerceIn(0.0, 1.0),
        countIn = prefs[countInKey] ?: defaults.countIn,
        ramp = TempoRamp(
            enabled = prefs[rampEnabledKey] ?: defaults.ramp.enabled,
            targetBpm = (prefs[rampTargetKey] ?: defaults.ramp.targetBpm)
                .coerceIn(BPM_MIN.toDouble(), BPM_MAX.toDouble()),
            bars = (prefs[rampBarsKey] ?: defaults.ramp.bars).coerceIn(RAMP_BARS_MIN, RAMP_BARS_MAX),
        ),
    )
}

private fun writeMetronomeState(prefs: MutablePreferences, state: MetronomeState) {
    prefs[bpmKey] = state.bpm
    prefs[numeratorKey] = state.timeSignature.numerator
    prefs[denominatorKey] = state.timeSignature.denominator
    prefs[divisionsKey] = state.divisionsPerBeat
    prefs[patternKey] = state.barPattern.joinToString(",")
    prefs[polyEnabledKey] = state.poly.enabled
    prefs[polyEventsKey] = state.poly.events
    prefs[polyAccentFirstKey] = state.poly.accentFirst
    writeRole(prefs, downbeatKeys, state.sounds.downbeat)
    writeRole(prefs, beatKeys, state.sounds.beat)
    writeRole(prefs, subdivisionKeys, state.sounds.subdivision)
    writeRole(prefs, polyKeys, state.sounds.poly)
    prefs[masterVolKey] = state.masterVol
    prefs[countInKey] = state.countIn
    prefs[rampEnabledKey] = state.ramp.enabled
    prefs[rampTargetKey] = state.ramp.targetBpm
    prefs[rampBarsKey] = state.ramp.bars
}

private fun readRole(prefs: Preferences, keys: SoundKeys, fallback: SoundRole): SoundRole = SoundRole(
    id = prefs[keys.id]?.takeIf(MetronomeVoices::has) ?: fallback.id,
    vol = (prefs[keys.vol] ?: fallback.vol).coerceIn(0.0, 1.0),
    accentVol = prefs[keys.accentVol]?.coerceIn(0.0, 1.0) ?: fallback.accentVol,
)

private fun writeRole(prefs: MutablePreferences, keys: SoundKeys, role: SoundRole) {
    prefs[keys.id] = role.id
    prefs[keys.vol] = role.vol
    val accent = role.accentVol
    if (accent != null) prefs[keys.accentVol] = accent else prefs.remove(keys.accentVol)
}
