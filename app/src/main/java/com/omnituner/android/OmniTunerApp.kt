package com.omnituner.android

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omnituner.android.prefs.DataStoreKeyValueStorage
import com.omnituner.core.prefs.InstrumentRegistry
import com.omnituner.core.prefs.MetronomePreferences
import com.omnituner.core.prefs.ScalePreferences
import com.omnituner.core.prefs.TunerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnituner")

class OmniTunerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        container = AppContainer(
            tunerPreferences = TunerPreferences(DataStoreKeyValueStorage(dataStore, appScope)),
            instrumentRegistry = InstrumentRegistry(DataStoreKeyValueStorage(dataStore, appScope)),
            scalePreferences = ScalePreferences(DataStoreKeyValueStorage(dataStore, appScope)),
            metronomePreferences = MetronomePreferences(
                DataStoreKeyValueStorage(dataStore, appScope),
            ),
        )
    }
}

data class AppContainer(
    val tunerPreferences: TunerPreferences,
    val instrumentRegistry: InstrumentRegistry,
    val scalePreferences: ScalePreferences,
    val metronomePreferences: MetronomePreferences,
)
