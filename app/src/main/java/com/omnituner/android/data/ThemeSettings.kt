package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope

enum class ThemeMode { System, Light, Dark }

enum class AccentColor { Wallpaper, Blue, Cyan, Green, Lime, Yellow, Orange, Pink, Black }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: AccentColor = AccentColor.Wallpaper,
)

private val themeKey = stringPreferencesKey("theme")
private val accentKey = stringPreferencesKey("theme.accent")

internal fun appearanceStore(dataStore: DataStore<Preferences>, scope: CoroutineScope): SettingsStore<AppearanceSettings> =
    SettingsStore(
        dataStore = dataStore,
        scope = scope,
        defaultValue = AppearanceSettings(),
        read = { prefs ->
            AppearanceSettings(
                themeMode = prefs.enumOr(themeKey, ThemeMode.System),
                accent = prefs.enumOr(accentKey, AccentColor.Wallpaper),
            )
        },
        write = { prefs, settings ->
            prefs[themeKey] = settings.themeMode.name
            prefs[accentKey] = settings.accent.name
        },
    )
