package com.akiba.app.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.AkibaLogo
import com.akiba.app.ui.components.common.AkibaLogoSize
import com.akiba.app.ui.components.common.AkibaLogoVariant
import com.akiba.app.ui.theme.akibaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    navController: NavHostController,
    isLoggedIn: Boolean,
) {
    val background = MaterialTheme.colorScheme.background
    val primary    = MaterialTheme.colorScheme.primary
    val secondary  = MaterialTheme.colorScheme.secondary
    val accent     = MaterialTheme.akibaColors.accentGreen

    // ── Aurora background animation — 3 slow-moving color blobs ──────────
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    val blob1X by infiniteTransition.animateFloat(
        initialValue  = 0.1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "blob1X",
    )
    val blob2X by infiniteTransition.animateFloat(
        initialValue  = 0.8f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "blob2X",
    )
    val blob3Y by infiniteTransition.animateFloat(
        initialValue  = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "blob3Y",
    )

    // ── Logo entry animations ─────────────────────────────────────────────
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f)   }

    // ── Loading dots ──────────────────────────────────────────────────────
    val dot1Scale = remember { Animatable(0.6f) }
    val dot2Scale = remember { Animatable(0.6f) }
    val dot3Scale = remember { Animatable(0.6f) }
    val dotsAlpha = remember { Animatable(0f)   }

    // ── Exit alpha — fades everything out before navigating ───────────────
    val exitAlpha = remember { Animatable(1f) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // 1. Logo springs in
        launch { logoScale.animateTo(1f,  spring(stiffness = 80f, dampingRatio = 0.55f)) }
        launch { logoAlpha.animateTo(1f,  tween(400)) }

        delay(1100)

        // 2. Dots appear and breathe
        dotsAlpha.animateTo(1f, tween(300))

        // Dot breathing — staggered, runs in background
        launch {
            while (true) {
                launch { dot1Scale.animateTo(1.3f, tween(500)); dot1Scale.animateTo(0.6f, tween(500)) }
                delay(180)
                launch { dot2Scale.animateTo(1.3f, tween(500)); dot2Scale.animateTo(0.6f, tween(500)) }
                delay(180)
                launch { dot3Scale.animateTo(1.3f, tween(500)); dot3Scale.animateTo(0.6f, tween(500)) }
                delay(640)
            }
        }

        delay(2000)

        // 3. Fade everything out
        exitAlpha.animateTo(0f, tween(400))

        // 4. Navigate — skip onboarding if already logged in
        val destination = if (isLoggedIn) Screen.Dashboard.route else Screen.Onboarding.route
        navController.navigate(destination) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .graphicsLayer(alpha = exitAlpha.value),
    ) {
        // ── Aurora blobs drawn on canvas ──────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Blob 1 — primary purple, top area
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(w * blob1X, h * 0.2f),
                    radius = w * 0.55f,
                ),
                radius = w * 0.55f,
                center = Offset(w * blob1X, h * 0.2f),
            )

            // Blob 2 — secondary blue, right area
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(w * blob2X, h * 0.5f),
                    radius = w * 0.5f,
                ),
                radius = w * 0.5f,
                center = Offset(w * blob2X, h * 0.5f),
            )

            // Blob 3 — accent green, bottom
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(w * 0.3f, h * blob3Y),
                    radius = w * 0.4f,
                ),
                radius = w * 0.4f,
                center = Offset(w * 0.3f, h * blob3Y),
            )
        }

        // ── Logo centered ─────────────────────────────────────────────────
        Column(
            modifier              = Modifier.align(Alignment.Center),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.Center,
        ) {
            AkibaLogo(
                variant  = AkibaLogoVariant.Hero,
                size     = AkibaLogoSize.Hero,
                animated = false, // we control animation manually above
                modifier = Modifier.graphicsLayer(
                    scaleX = logoScale.value,
                    scaleY = logoScale.value,
                    alpha  = logoAlpha.value,
                ),
            )

            Spacer(Modifier.height(48.dp))

            // ── Three breathing dots ──────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.graphicsLayer(alpha = dotsAlpha.value),
            ) {
                listOf(
                    primary   to dot1Scale.value,
                    secondary to dot2Scale.value,
                    accent    to dot3Scale.value,
                ).forEach { (color, scale) ->
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(
                            color  = color,
                            radius = (size.minDimension / 2) * scale,
                        )
                    }
                }
            }
        }
    }
}
