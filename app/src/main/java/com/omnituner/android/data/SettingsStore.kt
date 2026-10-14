package com.omnituner.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

internal val AppJson = Json { ignoreUnknownKeys = true }

class SettingsStore<T>(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    private val defaultValue: T,
    private val read: (Preferences) -> T,
    private val write: (MutablePreferences, T) -> Unit,
) {
    private val snapshots: Flow<T> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map(read)

    val flow: StateFlow<T> = snapshots.stateIn(scope, SharingStarted.Eagerly, defaultValue)

    suspend fun current(): T = snapshots.first()

    suspend fun update(transform: (T) -> T) {
        dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }
}

internal inline fun <reified T> jsonSettingsStore(
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    key: Preferences.Key<String>,
    defaultValue: T,
): SettingsStore<T> = SettingsStore(
    dataStore = dataStore,
    scope = scope,
    defaultValue = defaultValue,
    read = { prefs ->
        prefs[key]?.let { raw -> runCatching { AppJson.decodeFromString<T>(raw) }.getOrNull() }
            ?: defaultValue
    },
    write = { prefs, value -> prefs[key] = AppJson.encodeToString(value) },
)

internal inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, default: E): E =
    this[key]?.let { raw -> enumValues<E>().firstOrNull { it.name == raw } } ?: default
