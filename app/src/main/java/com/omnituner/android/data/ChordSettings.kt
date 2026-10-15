package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnituner.core.theory.DEFAULT_VOICING_COUNT
import com.omnituner.core.theory.MAX_VOICING_COUNT
import com.omnituner.core.theory.MIN_VOICING_COUNT
import kotlinx.coroutines.CoroutineScope

enum class VoicingView { Fretboard, Tab }

enum class DiagramLabels { Notes, Degrees }

data class ChordSettings(
    val voicingCount: Int = DEFAULT_VOICING_COUNT,
    val view: VoicingView = VoicingView.Fretboard,
    val labels: DiagramLabels = DiagramLabels.Notes,
)

private val voicingCountKey = intPreferencesKey("chords.voicingCount")
private val viewKey = stringPreferencesKey("chords.view")
private val labelsKey = stringPreferencesKey("chords.labels")

internal fun chordSettingsStore(dataStore: DataStore<Preferences>, scope: CoroutineScope): SettingsStore<ChordSettings> =
    SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = ChordSettings(),
        read = { prefs ->
            ChordSettings(
                voicingCount = (prefs[voicingCountKey] ?: DEFAULT_VOICING_COUNT)
                    .coerceIn(MIN_VOICING_COUNT, MAX_VOICING_COUNT),
                view = prefs.enumOr(viewKey, VoicingView.Fretboard),
                labels = prefs.enumOr(labelsKey, DiagramLabels.Notes),
            )
        },
        write = { prefs, settings ->
            prefs[voicingCountKey] = settings.voicingCount
            prefs[viewKey] = settings.view.name
            prefs[labelsKey] = settings.labels.name
        },
    )
