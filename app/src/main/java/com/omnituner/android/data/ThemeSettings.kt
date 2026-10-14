package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope

enum class ThemeMode { System, Light, Dark }

private val themeKey = stringPreferencesKey("theme")

internal fun themeStore(dataStore: DataStore<Preferences>, scope: CoroutineScope): SettingsStore<ThemeMode> =
    SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = ThemeMode.System,
        read = { prefs -> prefs.enumOr(themeKey, ThemeMode.System) },
        write = { prefs, mode -> prefs[themeKey] = mode.name },
    )
