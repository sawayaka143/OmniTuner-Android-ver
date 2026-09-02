package com.omnituner.android.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnituner.core.prefs.KeyValueStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class DataStoreKeyValueStorage(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : KeyValueStorage {

    private val mirror = ConcurrentHashMap<String, String>()

    private val writes = Channel<Write>(Channel.UNLIMITED)

    private sealed interface Write {
        data class Put(val key: String, val value: String) : Write
        data class Remove(val key: String) : Write
    }

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

        scope.launch {
            for (write in writes) {
                try {
                    when (write) {
                        is Write.Put -> dataStore.edit {
                            it[stringPreferencesKey(write.key)] = write.value
                        }
                        is Write.Remove -> dataStore.edit {
                            it.remove(stringPreferencesKey(write.key))
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun getItem(key: String): String? = mirror[key]

    override fun setItem(key: String, value: String) {
        mirror[key] = value
        writes.trySend(Write.Put(key, value))
    }

    override fun removeItem(key: String) {
        mirror.remove(key)
        writes.trySend(Write.Remove(key))
    }
}
