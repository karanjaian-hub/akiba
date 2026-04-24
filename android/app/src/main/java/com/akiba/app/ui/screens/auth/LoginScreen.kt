package com.akiba.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError    by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // ── Section visibility — staggered entry ─────────────────────────────
    var showTop      by remember { mutableStateOf(false) }
    var showForm     by remember { mutableStateOf(false) }
    var showTagline  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showTop     = true
        delay(150); showForm    = true
        delay(150); showTagline = true
    }

    // ── React to login state changes ──────────────────────────────────────
    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is AuthUiState.Success -> {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is AuthUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetLoginState()
            }
            else -> Unit
        }
    }

    val isLoading = loginState is AuthUiState.Loading

    fun validate(): Boolean {
        emailError    = if (email.isBlank())    "Email is required"    else null
        passwordError = if (password.isBlank()) "Password is required" else null
        return emailError == null && passwordError == null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Aurora background at low intensity
            AuroraBackground(
                primary   = MaterialTheme.colorScheme.primary,
                secondary = MaterialTheme.colorScheme.secondary,
                accent    = MaterialTheme.akibaColors.accentGreen,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(64.dp))

                // ── Top — brand section ───────────────────────────────────
                AnimatedVisibility(
                    visible = showTop,
                    enter   = fadeIn(tween(400)) + slideInVertically { -40 },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AkibaLogo(
                            variant  = AkibaLogoVariant.Full,
                            size     = AkibaLogoSize.Lg,
                            animated = true,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text       = "Welcome back.",
                            fontFamily = SoraFontFamily,
                            fontSize   = 22.sp,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign  = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // ── Form section ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = showForm,
                    enter   = fadeIn(tween(400)) + slideInVertically { 40 },
                ) {
                    GlassCard(elevation = GlassElevation.Raised) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            AkibaTextField(
                                value         = email,
                                onValueChange = { email = it; emailError = null },
                                label         = "Email",
                                leadingIcon   = Icons.Rounded.Email,
                                keyboardType  = KeyboardType.Email,
                                imeAction     = ImeAction.Next,
                                error         = emailError,
                            )

                            AkibaTextField(
                                value         = password,
                                onValueChange = { password = it; passwordError = null },
                                label         = "Password",
                                leadingIcon   = Icons.Rounded.Lock,
                                isPassword    = true,
                                imeAction     = ImeAction.Done,
                                onImeAction   = { if (validate()) viewModel.login(email, password) },
                                error         = passwordError,
                            )

                            // Forgot password — right aligned
                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick  = { navController.navigate(Screen.ForgotPassword.route) },
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                ) {
                                    Text(
                                        text       = "Forgot Password?",
                                        color      = MaterialTheme.akibaColors.gold,
                                        fontSize   = 13.sp,
                                        fontFamily = DmSansFontFamily,
                                    )
                                }
                            }

                            AkibaButton(
                                text     = "Sign In",
                                onClick  = { if (validate()) viewModel.login(email, password) },
                                loading  = isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Divider
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.fillMaxWidth(),
                            ) {
                                Divider(modifier = Modifier.weight(1f), color = MaterialTheme.akibaColors.glassBorder)
                                Text(
                                    text       = "  or continue  ",
                                    fontSize   = 13.sp,
                                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontFamily = DmSansFontFamily,
                                )
                                Divider(modifier = Modifier.weight(1f), color = MaterialTheme.akibaColors.glassBorder)
                            }

                            AkibaButton(
                                text    = "Create Account",
                                onClick = { navController.navigate(Screen.Register.route) },
                                variant = AkibaButtonVariant.Outline,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── Tagline at the bottom ─────────────────────────────────
                AnimatedVisibility(
                    visible = showTagline,
                    enter   = fadeIn(tween(600)),
                ) {
                    Text(
                        text       = "Every shilling has a story.",
                        fontFamily = DmSansFontFamily,
                        fontStyle  = FontStyle.Italic,
                        fontSize   = 13.sp,
                        color      = MaterialTheme.akibaColors.gold.copy(alpha = 0.7f),
                        modifier   = Modifier.padding(bottom = 24.dp),
                    )
                }
            }
        }
    }
}
