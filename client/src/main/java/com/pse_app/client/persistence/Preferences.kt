package com.pse_app.client.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
// Class has same name as the one that is defined here
import androidx.datastore.preferences.core.Preferences as _Preferences

/** Datastore for use by [Preferences] */
val Context.dataStore: DataStore<_Preferences> by preferencesDataStore(name = "settings")

/**
 * Used to access local datastore.
 * Works like a persisted Map<String, String>.
 */
class Preferences(private val context: Context) {
    // Ensures each actual key exists only once
    private val keys: MutableMap<String, _Preferences.Key<String>> = mutableMapOf()
    private val keysLock = Mutex()

    private suspend fun getPrefKey(key: String): _Preferences.Key<String> {
        keysLock.withLock {
            if (!keys.containsKey(key)) {
                keys[key] = stringPreferencesKey(key)
            }
            return keys.getValue(key)
        }
    }

    /**
     * Gets persisted value associated with key.
     * Returns null if no value exists for key.
     */
    suspend fun get(key: String): String? {
        val prefKey = getPrefKey(key)
        return context.dataStore.data.map { preferences -> preferences[prefKey]}.first()
    }

    /**
     * Sets value for key.
     */
    suspend fun set(key: String, value: String) {
        val prefKey = getPrefKey(key)
        context.dataStore.edit { preferences -> preferences[prefKey] = value}
    }
}
