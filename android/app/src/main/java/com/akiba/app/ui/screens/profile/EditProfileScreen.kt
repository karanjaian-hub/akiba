package com.akiba.app.ui.screens.profile

import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavHostController,
    viewModel    : ProfileViewModel = hiltViewModel(),
) {
    val userName  by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val snackbar  = remember { SnackbarHostState() }

    val scope    = rememberCoroutineScope()
    var fullName by remember(userName)  { mutableStateOf(userName)  }
    var email    by remember(userEmail) { mutableStateOf(userEmail) }
    var phone    by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard(elevation = GlassElevation.Raised) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AkibaTextField(
                        value         = fullName,
                        onValueChange = { fullName = it },
                        label         = "Full Name",
                        leadingIcon   = Icons.Rounded.Person,
                    )
                    AkibaTextField(
                        value         = email,
                        onValueChange = { },
                        label         = "Email",
                        leadingIcon   = Icons.Rounded.Email,
                        enabled       = false, // email is read-only
                    )
                    AkibaTextField(
                        value         = phone,
                        onValueChange = { phone = it },
                        label         = "Phone",
                        leadingIcon   = Icons.Rounded.Phone,
                        keyboardType  = androidx.compose.ui.text.input.KeyboardType.Phone,
                    )
                }
            }

            AkibaButton(
                text     = "Save Changes",
                modifier = Modifier.fillMaxWidth(),
                onClick  = {
                    // TODO: wire to updateProfile API in Phase 7 backend hookup
                    scope.launch { snackbar.showSnackbar("Profile updated ✓") }
                },
            )
        }
    }
}
