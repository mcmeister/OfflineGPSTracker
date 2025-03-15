package com.example.offlinegpstracker

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define the DataStore instance
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        const val SKIN_CLASSIC_GAUGE = 0
        const val SKIN_NEON_GAUGE = 1
        const val SKIN_MINIMAL_GAUGE = 2
        const val SKIN_SHIP = 3  // New skin for Default & Static compass
        const val SKIN_MINIMAL = 4 // New skin for Default & Static compass

        private val COMPASS_TYPE_KEY = intPreferencesKey("compass_type")
        private val COMPASS_SKIN_KEY = intPreferencesKey("compass_skin")
    }

    val compassType: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COMPASS_TYPE_KEY] ?: 0
    }

    val compassSkin: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COMPASS_SKIN_KEY] ?: SKIN_CLASSIC_GAUGE
    }

    suspend fun saveCompassType(type: Int) {
        context.dataStore.edit { preferences ->
            preferences[COMPASS_TYPE_KEY] = type
        }
    }

    suspend fun saveCompassSkin(skin: Int) {
        context.dataStore.edit { preferences ->
            preferences[COMPASS_SKIN_KEY] = skin
        }
    }
}