package com.omnituner.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.omnituner.android.audio.NoteAudio
import com.omnituner.android.data.InstrumentLibrary
import com.omnituner.android.data.metronomePresetStore
import com.omnituner.android.data.metronomeStateStore
import com.omnituner.android.data.scaleSettingsStore
import com.omnituner.android.data.themeStore
import com.omnituner.android.data.tunerSettingsStore
import kotlinx.coroutines.CoroutineScope

class AppContainer(
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    context: Context,
) {
    val theme = themeStore(dataStore, scope)
    val tuner = tunerSettingsStore(dataStore, scope)
    val scales = scaleSettingsStore(dataStore, scope)
    val metronome = metronomeStateStore(dataStore, scope)
    val metronomePresets = metronomePresetStore(dataStore, scope)
    val instruments = InstrumentLibrary(dataStore, scope)
    val noteAudio = NoteAudio(context)
}
