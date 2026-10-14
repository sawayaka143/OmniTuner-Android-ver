package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope

enum class TunerMode { Auto, Manual }

enum class StartupMode { Remember, Auto, Manual }

data class TunerSettings(
    val mode: TunerMode = TunerMode.Auto,
    val startupMode: StartupMode = StartupMode.Remember,
    val referencePitch: Int = 440,
    val inTuneFeedback: Boolean = true,
    val chime: Boolean = true,
    val haptics: Boolean = true,
    val tolerance: Int = 5,
    val holdMs: Int = 500,
) {
    fun initialMode(): TunerMode = when (startupMode) {
        StartupMode.Remember -> mode
        StartupMode.Auto -> TunerMode.Auto
        StartupMode.Manual -> TunerMode.Manual
    }
}

val REFERENCE_PITCH_RANGE = 415..466
val TOLERANCE_RANGE = 1..15
val HOLD_MS_RANGE = 0..1500
const val HOLD_MS_STEP = 50

private val modeKey = stringPreferencesKey("tuner.mode")
private val startupKey = stringPreferencesKey("tuner.startup")
private val referencePitchKey = intPreferencesKey("tuner.referencePitch")
private val inTuneFeedbackKey = booleanPreferencesKey("tuner.inTune.feedback")
private val chimeKey = booleanPreferencesKey("tuner.inTune.chime")
private val hapticsKey = booleanPreferencesKey("tuner.inTune.haptics")
private val toleranceKey = intPreferencesKey("tuner.inTune.tolerance")
private val holdMsKey = intPreferencesKey("tuner.inTune.holdMs")

internal fun tunerSettingsStore(dataStore: DataStore<Preferences>, scope: CoroutineScope): SettingsStore<TunerSettings> =
    SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = TunerSettings(),
        read = { prefs ->
            TunerSettings(
                mode = prefs.enumOr(modeKey, TunerMode.Auto),
                startupMode = prefs.enumOr(startupKey, StartupMode.Remember),
                referencePitch = (prefs[referencePitchKey] ?: 440).coerceIn(REFERENCE_PITCH_RANGE),
                inTuneFeedback = prefs[inTuneFeedbackKey] ?: true,
                chime = prefs[chimeKey] ?: true,
                haptics = prefs[hapticsKey] ?: true,
                tolerance = (prefs[toleranceKey] ?: 5).coerceIn(TOLERANCE_RANGE),
                holdMs = (prefs[holdMsKey] ?: 500).coerceIn(HOLD_MS_RANGE),
            )
        },
        write = { prefs, settings ->
            prefs[modeKey] = settings.mode.name
            prefs[startupKey] = settings.startupMode.name
            prefs[referencePitchKey] = settings.referencePitch.coerceIn(REFERENCE_PITCH_RANGE)
            prefs[inTuneFeedbackKey] = settings.inTuneFeedback
            prefs[chimeKey] = settings.chime
            prefs[hapticsKey] = settings.haptics
            prefs[toleranceKey] = settings.tolerance.coerceIn(TOLERANCE_RANGE)
            prefs[holdMsKey] = settings.holdMs.coerceIn(HOLD_MS_RANGE)
        },
    )
