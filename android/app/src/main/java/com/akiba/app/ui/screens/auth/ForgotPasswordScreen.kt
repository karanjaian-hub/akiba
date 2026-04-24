package com.akiba.app.ui.screens.auth


import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ForgotPasswordScreen(
    navController: NavHostController,
    viewModel    : AuthViewModel = hiltViewModel(),
) {
    val forgotState   by viewModel.forgotState.collectAsStateWithLifecycle()
    val snackbarHost  = remember { SnackbarHostState() }
    var email         by remember { mutableStateOf("") }
    var emailError    by remember { mutableStateOf<String?>(null) }

    // Lock icon bounce animation on mount
    val iconScale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(1f, spring(stiffness = 120f, dampingRatio = 0.55f))
    }

    LaunchedEffect(forgotState) {
        when (val state = forgotState) {
            is AuthUiState.Success -> {
                navController.navigate(
                    Screen.OtpVerification.createRoute(email, "reset")
                )
                viewModel.resetForgotState()
            }
            is AuthUiState.Error  -> {
                snackbarHost.showSnackbar(state.message)
                viewModel.resetForgotState()
            }
            else -> Unit
        }
    }

    val isLoading = forgotState is AuthUiState.Loading
    val primary   = MaterialTheme.colorScheme.primary

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar         = {
            TopAppBar(
                title  = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AuroraBackground(primary, MaterialTheme.colorScheme.secondary,
                MaterialTheme.akibaColors.accentGreen, 0.4f)

            Column(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(16.dp))

                // Lock icon in gradient circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(80.dp)
                        .graphicsLayer(scaleX = iconScale.value, scaleY = iconScale.value)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(primary, primary.copy(alpha = 0.5f))
                            )
                        ),
                ) {
                    Icon(Icons.Rounded.Email, null,
                        tint     = Color.White,
                        modifier = Modifier.size(36.dp))
                }

                // Title + subtitle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "Reset your password",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 26.sp,
                        color      = MaterialTheme.colorScheme.onSurface,
                        textAlign  = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = "Enter your email and we'll send you a code to reset your password.",
                        fontFamily = DmSansFontFamily,
                        fontSize   = 15.sp,
                        lineHeight  = 24.sp,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign  = TextAlign.Center,
                    )
                }

                // Form
                GlassCard(elevation = GlassElevation.Raised) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AkibaTextField(
                            value         = email,
                            onValueChange = { email = it; emailError = null },
                            label         = "Email address",
                            leadingIcon   = Icons.Rounded.Email,
                            keyboardType  = KeyboardType.Email,
                            error         = emailError,
                        )
                        AkibaButton(
                            text     = "Send Reset Code",
                            loading  = isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            onClick  = {
                                if (email.isBlank()) {
                                    emailError = "Email is required"
                                } else {
                                    viewModel.forgotPassword(email)
                                }
                            },
                        )
                        AkibaButton(
                            text    = "Back to Login",
                            onClick = { navController.popBackStack() },
                            variant = AkibaButtonVariant.Ghost,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
