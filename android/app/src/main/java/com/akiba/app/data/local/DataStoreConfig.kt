package com.akiba.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// Single DataStore instance for the entire app
val Context.dataStore by preferencesDataStore(name = "akiba_prefs")

object PrefKeys {
    val ACCESS_TOKEN  = stringPreferencesKey("akiba_access_token")
    val REFRESH_TOKEN = stringPreferencesKey("akiba_refresh_token")
    val USER_JSON     = stringPreferencesKey("akiba_user_json")
}
