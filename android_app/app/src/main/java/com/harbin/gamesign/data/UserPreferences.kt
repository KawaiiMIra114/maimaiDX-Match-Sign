package com.harbin.gamesign.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "game_sign_prefs")

class UserPreferences(private val context: Context) {
    
    companion object {
        private val PLAYER_ID = intPreferencesKey("player_id")
        private val PLAYER_NAME = stringPreferencesKey("player_name")
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }
    
    val playerId: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_ID]
    }
    
    val playerName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_NAME]
    }
    
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_LOGGED_IN] ?: false
    }
    
    suspend fun savePlayer(playerId: Int, playerName: String) {
        context.dataStore.edit { prefs ->
            prefs[PLAYER_ID] = playerId
            prefs[PLAYER_NAME] = playerName
            prefs[IS_LOGGED_IN] = true
        }
    }
    
    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(PLAYER_ID)
            prefs.remove(PLAYER_NAME)
            prefs[IS_LOGGED_IN] = false
        }
    }
}
