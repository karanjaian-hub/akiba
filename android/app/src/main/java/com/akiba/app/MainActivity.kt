package com.akiba.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.akiba.app.data.local.dataStore
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
            // Read login state from DataStore — recomposes if token changes
            val isLoggedIn by dataStore.data
                .map { it[PrefKeys.ACCESS_TOKEN] != null }
                .collectAsState(initial = false)

            AkibaTheme {
                RootNavGraph(isLoggedIn = isLoggedIn)
            }
        }
    }
}
