package com.akiba.app.ui.screens.auth


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@Composable
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun ResetPasswordScreen(
    navController: NavHostController,
    email        : String,
    otp          : String,
    viewModel    : AuthViewModel = hiltViewModel(),
) {
    val resetState   by viewModel.resetState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    var newPassword     by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Shield icon entry animation
    val iconScale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(1f, spring(stiffness = 120f, dampingRatio = 0.55f))
    }

    LaunchedEffect(resetState) {
        when (val state = resetState) {
            is AuthUiState.Success -> {
                snackbarHost.showSnackbar("Password reset successfully!")
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthUiState.Error  -> {
                snackbarHost.showSnackbar(state.message)
                viewModel.resetResetState()
            }
            else -> Unit
        }
    }

    // ── Password strength ─────────────────────────────────────────────────
    val strength = passwordStrength(newPassword)
    val passwordsMatch = newPassword == confirmPassword && newPassword.isNotEmpty()
    val canSubmit = strength == 4 && passwordsMatch
    val isLoading = resetState is AuthUiState.Loading

    val accent  = MaterialTheme.akibaColors.accentGreen
    val primary = MaterialTheme.colorScheme.primary

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AuroraBackground(primary, MaterialTheme.colorScheme.secondary, accent, 0.4f)

            Column(
                modifier              = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                // Shield icon in green gradient circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(80.dp)
                        .graphicsLayer(scaleX = iconScale.value, scaleY = iconScale.value)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(accent, accent.copy(alpha = 0.5f)))),
                ) {
                    Icon(Icons.Rounded.Shield, null,
                        tint     = Color.White,
                        modifier = Modifier.size(36.dp))
                }

                Text(
                    text       = "Create new password",
                    fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 26.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                    textAlign  = TextAlign.Center,
                )

                GlassCard(elevation = GlassElevation.Raised) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        AkibaTextField(
                            value         = newPassword,
                            onValueChange = { newPassword = it },
                            label         = "New Password",
                            leadingIcon   = Icons.Rounded.Lock,
                            isPassword    = true,
                        )

                        // ── Password strength meter ────────────────────────
                        PasswordStrengthMeter(
                            password = newPassword,
                            strength = strength,
                        )

                        AkibaTextField(
                            value         = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label         = "Confirm Password",
                            leadingIcon   = Icons.Rounded.Lock,
                            isPassword    = true,
                            error         = if (confirmPassword.isNotEmpty() && !passwordsMatch)
                                               "Passwords do not match" else null,
                        )

                        AkibaButton(
                            text     = "Reset Password",
                            onClick  = {
                                if (canSubmit) viewModel.resetPassword(email, otp, newPassword)
                            },
                            loading  = isLoading,
                            enabled  = canSubmit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ── Password strength meter ───────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PasswordStrengthMeter(password: String, strength: Int) {
    val accent   = MaterialTheme.akibaColors.accentGreen
    val error    = MaterialTheme.colorScheme.error
    val gold     = MaterialTheme.akibaColors.gold

    val segmentColors = listOf(error, Color(0xFFF97316), gold, accent)
    val labels        = listOf("Weak", "Fair", "Good", "Strong")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 4 segment bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier              = Modifier.fillMaxWidth(),
        ) {
            segmentColors.forEachIndexed { index, color ->
                val isFilled = index < strength
                val segColor by animateColorAsState(
                    targetValue = if (isFilled) color else MaterialTheme.akibaColors.glassBorder,
                    label       = "seg$index",
                )
                val segWidth by animateFloatAsState(
                    targetValue   = if (isFilled) 1f else 0.3f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "segWidth$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(segColor),
                )
            }
        }

        // Strength label
        if (strength > 0) {
            Text(
                text       = labels[strength - 1],
                fontSize   = 12.sp,
                fontFamily = DmSansFontFamily,
                color      = segmentColors[strength - 1],
            )
        }

        // Criteria chips
        val criteria = listOf(
            "8+ chars"     to (password.length >= 8),
            "1 uppercase"  to password.any { it.isUpperCase() },
            "1 number"     to password.any { it.isDigit() },
            "1 special"    to password.any { !it.isLetterOrDigit() },
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement   = Arrangement.spacedBy(6.dp),
        ) {
            criteria.forEach { (label, met) ->
                val chipColor by animateColorAsState(
                    targetValue = if (met) accent else MaterialTheme.akibaColors.glassBorder,
                    label       = "chip$label",
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text       = if (met) "✓ $label" else "· $label",
                        fontSize   = 11.sp,
                        fontFamily = DmSansFontFamily,
                        color      = if (met) accent
                                     else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

// ── Returns 0–4 strength score ────────────────────────────────────────────────
private fun passwordStrength(password: String): Int {
    var score = 0
    if (password.length >= 8)              score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() })     score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score
}
