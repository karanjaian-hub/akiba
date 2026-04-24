package com.akiba.app.ui.screens.payments

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import kotlin.random.Random

@Composable
fun PaymentSuccessScreen(
    navController: NavHostController,
    paymentId    : String,
) {
    val accent   = MaterialTheme.akibaColors.accentGreen
    val primary  = MaterialTheme.colorScheme.primary
    val secondary= MaterialTheme.colorScheme.secondary
    val gold     = MaterialTheme.akibaColors.gold

    // Checkmark spring
    val checkScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        checkScale.animateTo(1f, spring(stiffness = 120f, dampingRatio = 0.55f))
    }

    // ── Confetti particles ────────────────────────────────────────────────
    val particleColors = listOf(primary, secondary, accent, gold)
    val particles = remember {
        List(25) {
            Triple(
                Random.nextFloat(),                          // x fraction
                Random.nextLong(1200, 2000),                 // duration ms
                particleColors[Random.nextInt(particleColors.size)], // color
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f)))),
    ) {
        // Confetti canvas
        val infiniteTransition = rememberInfiniteTransition(label = "confetti")
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEachIndexed { index, (xFrac, _, color) ->
                drawCircle(
                    color  = color.copy(alpha = 0.7f),
                    radius = Random.nextFloat() * 8.dp.toPx() + 4.dp.toPx(),
                    center = Offset(
                        x = size.width * xFrac,
                        y = (System.currentTimeMillis() % 2000) / 2000f * size.height,
                    ),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            // Checkmark circle
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(80.dp)
                    .graphicsLayer(scaleX = checkScale.value, scaleY = checkScale.value)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(0.3f), Color.Transparent))),
            ) {
                Icon(Icons.Rounded.Check, null,
                    tint = Color.White, modifier = Modifier.size(48.dp))
            }

            Text("Payment Successful", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 26.sp,
                color = Color.White, textAlign = TextAlign.Center)

            // Summary card
            GlassCard(elevation = GlassElevation.Raised) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Payment ID" to paymentId,
                        "Status"     to "Completed",
                    ).forEach { (label, value) ->
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(value, fontFamily = JetBrainsMonoFamily, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AkibaButton(
                    text    = "Done",
                    onClick = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                AkibaButton(
                    text    = "New Payment",
                    variant = AkibaButtonVariant.Ghost,
                    onClick = {
                        navController.navigate(Screen.Payments.route) {
                            popUpTo("payment_success/$paymentId") { inclusive = true }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
