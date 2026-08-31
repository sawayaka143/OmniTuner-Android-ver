package com.omnituner.android.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnituner.core.prefs.KeyValueStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Synchronous facade over DataStore for the shared preference schemas.
 * Reads are served from an in-memory mirror (hydrated once); writes update the
 * mirror immediately and persist asynchronously.
 */
class DataStoreKeyValueStorage(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : KeyValueStorage {

    private val mirror = ConcurrentHashMap<String, String>()

    init {
        val initial: Preferences? = runBlocking {
            try {
                dataStore.data.first()
            } catch (_: Exception) {
                null
            }
        }
        val map = initial?.asMap()
        if (map != null) {
            for (entry in map) {
                val value = entry.value
                if (value is String) mirror[entry.key.name] = value
            }
        }
    }

    override fun getItem(key: String): String? = mirror[key]

    override fun setItem(key: String, value: String) {
        mirror[key] = value
        scope.launch {
            try {
                dataStore.edit { it[stringPreferencesKey(key)] = value }
            } catch (_: Exception) {
            }
        }
    }

    override fun removeItem(key: String) {
        mirror.remove(key)
        scope.launch {
            try {
                dataStore.edit { it.remove(stringPreferencesKey(key)) }
            } catch (_: Exception) {
            }
        }
    }
}
