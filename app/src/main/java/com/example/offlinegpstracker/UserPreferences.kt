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
        const val NO_SKIN = -1
        const val SKIN_CLASSIC = 0
        const val SKIN_NEON = 1
        const val SKIN_MINIMAL = 2

        private val COMPASS_TYPE_KEY = intPreferencesKey("compass_type")
        private val COMPASS_SKIN_KEY = intPreferencesKey("compass_skin")
    }

    // Retrieve stored compass type as a Flow (existing logic)
    val compassType: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COMPASS_TYPE_KEY] ?: 0 // Default compass type
    }

    // Retrieve stored compass skin as a Flow (new addition)
    val compassSkin: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COMPASS_SKIN_KEY] ?: SKIN_CLASSIC // Default to Classic skin
    }

    // Save the selected compass type (existing functionality)
    suspend fun saveCompassType(type: Int) {
        context.dataStore.edit { preferences ->
            preferences[COMPASS_TYPE_KEY] = type
        }
    }

    // Save the selected compass skin (new addition)
    suspend fun saveCompassSkin(skin: Int) {
        context.dataStore.edit { preferences ->
            preferences[COMPASS_SKIN_KEY] = skin
        }
    }
}