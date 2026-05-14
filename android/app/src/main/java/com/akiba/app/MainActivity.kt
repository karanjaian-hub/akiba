package com.akiba.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.akiba.app.data.local.dataStore
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import com.akiba.app.data.local.PrefKeys
import com.akiba.app.navigation.RootNavGraph
import com.akiba.app.ui.theme.AkibaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Read token once on IO — fast, non-blocking
            val isLoggedIn by produceState(initialValue = false) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val prefs   = dataStore.data.first()
                        val token   = prefs[PrefKeys.ACCESS_TOKEN]
                        if (token == null) { value = false; return@withContext }
                        val payload = token.split(".").getOrNull(1) ?: run { value = false; return@withContext }
                        val padded  = payload + "=".repeat((4 - payload.length % 4) % 4)
                        val decoded = String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE))
                        val exp     = com.google.gson.JsonParser.parseString(decoded)
                            .asJsonObject.get("exp")?.asLong ?: 0L
                        val now     = System.currentTimeMillis() / 1000L
                        if (exp > now) {
                            value = true
                        } else {
                            dataStore.edit { it.clear() }
                            value = false
                        }
                    } catch (e: Exception) {
                        dataStore.edit { it.clear() }
                        value = false
                    }
                }
            }

            AkibaTheme {
                RootNavGraph(isLoggedIn = isLoggedIn)
            }
        }
    }
}
