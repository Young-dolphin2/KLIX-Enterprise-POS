package com.example.barandgrillpos.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "user_settings")

class ThemePreferences(private val context: Context) {
    companion object {
        val SEED_COLOR = longPreferencesKey("seed_color")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val LOGO_URI = stringPreferencesKey("custom_logo_uri")
    }

    val seedColor: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[SEED_COLOR] ?: 0xFFFF8C00 // Default PrimaryOrange
        }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_FIRST_LAUNCH] ?: true
        }
        
    val logoUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LOGO_URI]
        }

    suspend fun setSeedColor(colorValue: Long) {
        context.dataStore.edit { preferences ->
            preferences[SEED_COLOR] = colorValue
        }
    }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { preferences ->
            preferences[IS_FIRST_LAUNCH] = false
        }
    }
    
    suspend fun setLogoUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[LOGO_URI] = uri
        }
    }
}
