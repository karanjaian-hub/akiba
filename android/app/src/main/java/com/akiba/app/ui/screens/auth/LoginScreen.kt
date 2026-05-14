package com.akiba.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
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
    viewModel    : AuthViewModel = hiltViewModel(),
) {
    val loginState        by viewModel.loginState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var email         by remember { mutableStateOf("") }
    var password      by remember { mutableStateOf("") }
    var emailError    by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var apiError      by remember { mutableStateOf<String?>(null) }

    // Staggered entry
    var showLogo   by remember { mutableStateOf(false) }
    var showForm   by remember { mutableStateOf(false) }
    var showBottom by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showLogo   = true
        delay(200); showForm   = true
        delay(200); showBottom = true
    }

    // Shake animation for wrong credentials
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is AuthUiState.Success -> {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is AuthUiState.Error -> {
                // Parse the error message for user-friendly display
                apiError = when {
                    state.message.contains("401") || state.message.contains("credentials", ignoreCase = true)
                        -> "Incorrect email or password. Please try again."
                    state.message.contains("429") || state.message.contains("too many", ignoreCase = true)
                        -> "Too many attempts. Please wait a moment."
                    state.message.contains("network", ignoreCase = true) || state.message.contains("connect", ignoreCase = true)
                        -> "Connection failed. Check your internet and try again."
                    state.message.contains("500") || state.message.contains("server", ignoreCase = true)
                        -> "Server error. Please try again shortly."
                    else -> "Incorrect email or password. Please try again."
                }
                // Shake the form
                listOf(-10f, -8f, 8f, -8f, 8f, -4f, 4f, 0f).forEach { offset ->
                    shakeOffset.snapTo(offset)
                    delay(50)
                }
                viewModel.resetLoginState()
            }
            else -> Unit
        }
    }

    fun validate(): Boolean {
        var valid = true
        emailError = when {
            email.isBlank()        -> "Email is required".also { valid = false }
            !email.contains("@")   -> "Enter a valid email address".also { valid = false }
            else                   -> null
        }
        passwordError = when {
            password.isBlank()     -> "Password is required".also { valid = false }
            password.length < 6    -> "Password must be at least 6 characters".also { valid = false }
            else                   -> null
        }
        // Clear api error on new attempt
        if (valid) apiError = null
        return valid
    }

    val isLoading = loginState is AuthUiState.Loading
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val background= MaterialTheme.colorScheme.background

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Aurora background
            AuroraBackground(primary, secondary, accent, intensity = 0.6f)

            // Subtle bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, background.copy(alpha = 0.95f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))

                // ── Logo section ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = showLogo,
                    enter   = fadeIn(tween(500)) + slideInVertically { -30 },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AkibaLogo(variant = AkibaLogoVariant.Icon, size = AkibaLogoSize.Xl, animated = true)
                        Spacer(Modifier.height(12.dp))
                        Text("AKIBA", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 28.sp, letterSpacing = 6.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("Every shilling has a story.", fontFamily = DmSansFontFamily,
                            fontStyle = FontStyle.Italic, fontSize = 13.sp,
                            color = MaterialTheme.akibaColors.gold.copy(alpha = 0.8f))
                    }
                }

                Spacer(Modifier.height(36.dp))

                // ── Form ──────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = showForm,
                    enter   = fadeIn(tween(500)) + slideInVertically { 40 },
                ) {
                    Column(
                        modifier = Modifier.offset(x = shakeOffset.value.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        // Form card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .then(
                                    if (apiError != null)
                                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                    else
                                        Modifier.border(1.dp, MaterialTheme.akibaColors.glassBorder, RoundedCornerShape(24.dp))
                                )
                                .padding(20.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                                // Header text
                                Column {
                                    Text("Welcome back", fontFamily = SoraFontFamily,
                                        fontWeight = FontWeight.Bold, fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("Sign in to your account", fontFamily = DmSansFontFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }

                                // API error banner
                                AnimatedVisibility(
                                    visible = apiError != null,
                                    enter   = expandVertically() + fadeIn(tween(200)),
                                    exit    = shrinkVertically() + fadeOut(tween(150)),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier          = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                    ) {
                                        Icon(Icons.Rounded.ErrorOutline, null,
                                            tint     = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text       = apiError ?: "",
                                            fontFamily = DmSansFontFamily,
                                            fontSize   = 13.sp,
                                            color      = MaterialTheme.colorScheme.error,
                                            lineHeight  = 18.sp,
                                        )
                                    }
                                }

                                AkibaTextField(
                                    value         = email,
                                    onValueChange = { email = it; emailError = null; apiError = null },
                                    label         = "Email address",
                                    leadingIcon   = Icons.Rounded.Email,
                                    keyboardType  = KeyboardType.Email,
                                    imeAction     = ImeAction.Next,
                                    error         = emailError,
                                )

                                AkibaTextField(
                                    value         = password,
                                    onValueChange = { password = it; passwordError = null; apiError = null },
                                    label         = "Password",
                                    leadingIcon   = Icons.Rounded.Lock,
                                    isPassword    = true,
                                    imeAction     = ImeAction.Done,
                                    onImeAction   = { if (validate()) viewModel.login(email, password) },
                                    error         = passwordError,
                                )

                                // Forgot password
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        onClick  = { navController.navigate(Screen.ForgotPassword.route) },
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text("Forgot password?", fontFamily = DmSansFontFamily,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.akibaColors.gold)
                                    }
                                }

                                AkibaButton(
                                    text     = if (isLoading) "Signing in..." else "Sign In",
                                    loading  = isLoading,
                                    enabled  = !isLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick  = { if (validate()) viewModel.login(email, password) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Divider ───────────────────────────────────────────────
                AnimatedVisibility(visible = showBottom, enter = fadeIn(tween(500))) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            modifier              = Modifier.fillMaxWidth(),
                        ) {
                            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.akibaColors.glassBorder)
                            Text("  don't have an account?  ", fontFamily = DmSansFontFamily,
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.akibaColors.glassBorder)
                        }

                        AkibaButton(
                            text    = "Create Account",
                            variant = AkibaButtonVariant.Outline,
                            modifier = Modifier.fillMaxWidth(),
                            onClick  = { navController.navigate(Screen.Register.route) },
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
