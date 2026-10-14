package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope

enum class Accidental { Sharp, Flat }

enum class LabelMode { NoteNames, ScaleDegrees }

data class ScaleSettings(
    val rootPitchClass: Int = 4,
    val scaleId: String = "major",
    val accidental: Accidental = Accidental.Sharp,
    val fretCount: Int = 12,
    val labelMode: LabelMode = LabelMode.NoteNames,
    val showOutsideScale: Boolean = false,
)

val FRET_COUNT_OPTIONS = listOf(12, 15, 21)

private val rootKey = intPreferencesKey("scale.root")
private val scaleKey = stringPreferencesKey("scale.id")
private val accidentalKey = stringPreferencesKey("scale.accidental")
private val fretCountKey = intPreferencesKey("scale.fretCount")
private val labelModeKey = stringPreferencesKey("scale.labelMode")
private val showOutsideKey = booleanPreferencesKey("scale.showOutside")

internal fun scaleSettingsStore(dataStore: DataStore<Preferences>, scope: CoroutineScope): SettingsStore<ScaleSettings> =
    SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = ScaleSettings(),
        read = { prefs ->
            ScaleSettings(
                rootPitchClass = (prefs[rootKey] ?: 4).coerceIn(0, 11),
                scaleId = prefs[scaleKey] ?: "major",
                accidental = prefs.enumOr(accidentalKey, Accidental.Sharp),
                fretCount = (prefs[fretCountKey] ?: 12).takeIf { it in FRET_COUNT_OPTIONS } ?: 12,
                labelMode = prefs.enumOr(labelModeKey, LabelMode.NoteNames),
                showOutsideScale = prefs[showOutsideKey] ?: false,
            )
        },
        write = { prefs, settings ->
            prefs[rootKey] = settings.rootPitchClass
            prefs[scaleKey] = settings.scaleId
            prefs[accidentalKey] = settings.accidental.name
            prefs[fretCountKey] = settings.fretCount
            prefs[labelModeKey] = settings.labelMode.name
            prefs[showOutsideKey] = settings.showOutsideScale
        },
    )
