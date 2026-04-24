package com.akiba.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.AkibaButton
import com.akiba.app.ui.components.common.AkibaButtonVariant
import com.akiba.app.ui.components.common.AuroraBackground
import com.akiba.app.ui.components.common.AkibaLogo
import com.akiba.app.ui.components.common.AkibaLogoSize
import com.akiba.app.ui.components.common.AkibaLogoVariant
import com.akiba.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OtpVerificationScreen(
    navController: NavHostController,
    email        : String,
    type         : String, // "verify" or "reset"
    viewModel    : AuthViewModel = hiltViewModel(),
) {
    val otpState      by viewModel.otpState.collectAsStateWithLifecycle()
    val snackbarHost  = remember { SnackbarHostState() }
    val scope         = rememberCoroutineScope()
    val haptic        = LocalHapticFeedback.current

    var otpValue      by remember { mutableStateOf("") }
    var resendSeconds by remember { mutableIntStateOf(60) }
    var canResend     by remember { mutableStateOf(false) }

    // Shake animation for wrong OTP
    val shakeOffset   = remember { Animatable(0f) }

    // Box background flash on error
    var flashError    by remember { mutableStateOf(false) }

    // Success state
    var showSuccess   by remember { mutableStateOf(false) }

    // ── Countdown timer ───────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (resendSeconds > 0) {
            delay(1000)
            resendSeconds--
        }
        canResend = true
    }

    // ── Auto-submit when 6 digits entered ────────────────────────────────
    LaunchedEffect(otpValue) {
        if (otpValue.length == 6) {
            delay(300)
            viewModel.verifyOtp(email, otpValue, type)
        }
    }

    // ── React to OTP state ────────────────────────────────────────────────
    LaunchedEffect(otpState) {
        when (val state = otpState) {
            is AuthUiState.Success -> {
                showSuccess = true
                delay(600)
                val destination = if (type == "verify") Screen.Dashboard.route
                                  else Screen.Login.route
                navController.navigate(destination) {
                    popUpTo(Screen.OtpVerification.route) { inclusive = true }
                }
            }
            is AuthUiState.Error  -> {
                // Shake all boxes
                scope.launch {
                    listOf(-12f, -8f, 8f, -8f, 8f, -4f, 4f, 0f).forEach { offset ->
                        shakeOffset.snapTo(offset)
                        delay(50)
                    }
                }
                flashError = true
                delay(400)
                flashError = false
                otpValue   = ""
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                snackbarHost.showSnackbar(state.message)
                viewModel.resetOtpState()
            }
            else -> Unit
        }
    }

    val isLoading = otpState is AuthUiState.Loading
    val primary   = MaterialTheme.colorScheme.primary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val error     = MaterialTheme.colorScheme.error

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AuroraBackground(primary, MaterialTheme.colorScheme.secondary, accent, 0.5f)

            Column(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.SpaceBetween,
            ) {

                // ── Top section ───────────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(32.dp))
                    AkibaLogo(variant = AkibaLogoVariant.Icon, size = AkibaLogoSize.Md)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text       = "Check your email",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 26.sp,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = "We sent a 6-digit code to",
                        fontFamily = DmSansFontFamily,
                        fontSize   = 15.sp,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text       = email.maskEmail(),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize   = 14.sp,
                        color      = primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Info chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.akibaColors.gold.copy(alpha = 0.15f))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text       = "📧  Check spam if not received",
                            fontSize   = 12.sp,
                            fontFamily = DmSansFontFamily,
                            color      = MaterialTheme.akibaColors.gold,
                        )
                    }
                }

                // ── OTP boxes ─────────────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Hidden text field captures all input
                    BasicTextField(
                        value         = otpValue,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        cursorBrush   = SolidColor(Color.Transparent),
                        modifier      = Modifier.size(0.dp), // invisible
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.offset(x = shakeOffset.value.dp),
                    ) {
                        repeat(6) { index ->
                            OtpDigitBox(
                                digit      = otpValue.getOrNull(index)?.toString() ?: "",
                                isActive   = otpValue.length == index,
                                isFilled   = index < otpValue.length,
                                isSuccess  = showSuccess,
                                flashError = flashError,
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    AkibaButton(
                        text     = "Confirm Code",
                        onClick  = { viewModel.verifyOtp(email, otpValue, type) },
                        loading  = isLoading,
                        enabled  = otpValue.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ── Resend section ────────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!canResend) {
                        // Circular countdown arc
                        CountdownArc(secondsLeft = resendSeconds, totalSeconds = 60)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text       = "Resend code in 0:${resendSeconds.toString().padStart(2, '0')}",
                            fontSize   = 13.sp,
                            fontFamily = DmSansFontFamily,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    } else {
                        AnimatedVisibility(visible = canResend, enter = fadeIn(tween(300))) {
                            TextButton(onClick = {
                                canResend     = false
                                resendSeconds = 60
                                otpValue      = ""
                                // TODO: call resend endpoint in Phase 3 backend hookup
                                scope.launch {
                                    snackbarHost.showSnackbar("Code resent to $email")
                                }
                            }) {
                                Text(
                                    "Resend code →",
                                    color      = primary,
                                    fontFamily = DmSansFontFamily,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ── Single OTP digit box ──────────────────────────────────────────────────────
@Composable
private fun OtpDigitBox(
    digit      : String,
    isActive   : Boolean,
    isFilled   : Boolean,
    isSuccess  : Boolean,
    flashError : Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent  = MaterialTheme.akibaColors.accentGreen
    val error   = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surface

    val borderColor by animateColorAsState(
        targetValue = when {
            flashError -> error
            isSuccess  -> accent
            isFilled   -> accent
            isActive   -> primary
            else       -> MaterialTheme.akibaColors.glassBorder
        },
        label = "otpBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            flashError -> error.copy(alpha = 0.15f)
            isSuccess  -> accent.copy(alpha = 0.1f)
            else       -> surface.copy(alpha = 0.6f)
        },
        animationSpec = tween(400),
        label         = "otpBg",
    )
    val scale by animateFloatAsState(
        targetValue   = if (isFilled) 1f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label         = "otpScale",
    )

    // Blinking cursor
    val cursorAlpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue  = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label         = "cursorAlpha",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(width = 46.dp, height = 56.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isActive || isFilled) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            // Glow on active box
            .drawBehind {
                if (isActive) {
                    drawRoundRect(
                        color        = primary.copy(alpha = 0.2f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                }
            },
    ) {
        if (digit.isNotEmpty()) {
            Text(
                text       = digit,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize   = 24.sp,
                color      = MaterialTheme.colorScheme.onSurface,
                textAlign  = TextAlign.Center,
            )
        } else if (isActive) {
            // Blinking cursor line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(primary.copy(alpha = cursorAlpha)),
            )
        }
    }
}

// ── Circular countdown arc ────────────────────────────────────────────────────
@Composable
private fun CountdownArc(secondsLeft: Int, totalSeconds: Int) {
    val primary  = MaterialTheme.colorScheme.primary
    val progress = secondsLeft.toFloat() / totalSeconds.toFloat()

    androidx.compose.foundation.Canvas(modifier = Modifier.size(48.dp)) {
        val stroke = 3.dp.toPx()
        val inset  = stroke / 2

        // Track
        drawArc(
            color      = primary.copy(alpha = 0.15f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter  = false,
            style      = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            topLeft    = androidx.compose.ui.geometry.Offset(inset, inset),
            size       = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        )
        // Progress
        drawArc(
            color      = primary,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter  = false,
            style      = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap   = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
            topLeft    = androidx.compose.ui.geometry.Offset(inset, inset),
            size       = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        )
    }
}

// ── Mask email — k***@gmail.com ───────────────────────────────────────────────
private fun String.maskEmail(): String {
    val parts = split("@")
    if (parts.size != 2) return this
    val name   = parts[0]
    val domain = parts[1]
    val masked = name.first() + "***"
    return "$masked@$domain"
}
