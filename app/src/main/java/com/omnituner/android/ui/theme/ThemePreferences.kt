package com.omnituner.android.ui.theme

import com.omnituner.core.prefs.KeyValueStorage

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

/** App theme mode (web: html[data-theme]), persisted via DataStore. */
class ThemePreferences(private val storage: KeyValueStorage) {

    private val key = "theme"

    fun mode(): String = when (storage.getItem(key)) {
        THEME_LIGHT -> THEME_LIGHT
        THEME_DARK -> THEME_DARK
        else -> THEME_SYSTEM
    }

    fun setMode(mode: String) {
        storage.setItem(key, mode)
    }
}
