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
        private val COMPASS_TYPE_KEY = intPreferencesKey("compass_type")
    }

    // Retrieve stored compass type as a Flow
    val compassType: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COMPASS_TYPE_KEY] ?: 0 // Default to CompassView (0)
    }

    // Save the selected compass type
    suspend fun saveCompassType(type: Int) {
        context.dataStore.edit { preferences ->
            preferences[COMPASS_TYPE_KEY] = type
        }
    }

}