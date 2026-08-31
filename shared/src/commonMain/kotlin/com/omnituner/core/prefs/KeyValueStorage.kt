package com.omnituner.core.prefs

/**
 * Minimal storage abstraction so the preference schemas stay platform-free.
 * Android binds this to DataStore-backed preferences; tests use an in-memory map.
 */
interface KeyValueStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
}

class MemoryKeyValueStorage : KeyValueStorage {
    private val values = mutableMapOf<String, String>()

    override fun getItem(key: String): String? = values[key]
    override fun setItem(key: String, value: String) {
        values[key] = value
    }
    override fun removeItem(key: String) {
        values.remove(key)
    }
}

class ThrowingKeyValueStorage(private val delegate: KeyValueStorage) : KeyValueStorage {
    class StorageFullException : Exception("Storage full")

    override fun getItem(key: String): String? = delegate.getItem(key)

    override fun setItem(key: String, value: String) {
        throw StorageFullException()
    }

    override fun removeItem(key: String) {
        delegate.removeItem(key)
    }
}
