package com.akiba.app.ui.screens.payments

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.ui.components.common.AkibaButton
import com.akiba.app.ui.components.common.AkibaButtonVariant
import com.akiba.app.ui.components.common.AkibaLogo
import com.akiba.app.ui.components.common.AkibaLogoSize
import com.akiba.app.ui.components.common.AkibaLogoVariant
import com.akiba.app.ui.theme.*

@Composable
fun PaymentPendingScreen(
    navController: NavHostController,
    paymentId    : String,
    viewModel    : PaymentViewModel = hiltViewModel(),
) {
    val paymentState by viewModel.paymentState.collectAsStateWithLifecycle()
    val primary      = MaterialTheme.colorScheme.primary

    // Navigate when polling resolves
    LaunchedEffect(paymentState) {
        when (val s = paymentState) {
            is PaymentUiState.Completed ->
                navController.navigate("payment_success/${s.status.paymentId}") {
                    popUpTo("payment_pending/$paymentId") { inclusive = true }
                }
            is PaymentUiState.Failed ->
                navController.navigate("payment_failed/${s.reason}") {
                    popUpTo("payment_pending/$paymentId") { inclusive = true }
                }
            else -> Unit
        }
    }

    // ── 3 concentric pulsing rings ────────────────────────────────────────
    val ring1 = rememberInfiniteTransition(label = "ring1")
    val ring2 = rememberInfiniteTransition(label = "ring2")
    val ring3 = rememberInfiniteTransition(label = "ring3")

    val scale1 by ring1.animateFloat(1f, 1.4f,
        infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "s1")
    val alpha1 by ring1.animateFloat(1f, 0f,
        infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "a1")

    val scale2 by ring2.animateFloat(1f, 1.4f,
        infiniteRepeatable(tween(1800, delayMillis = 200), RepeatMode.Restart), label = "s2")
    val alpha2 by ring2.animateFloat(1f, 0f,
        infiniteRepeatable(tween(1800, delayMillis = 200), RepeatMode.Restart), label = "a2")

    val scale3 by ring3.animateFloat(1f, 1.4f,
        infiniteRepeatable(tween(1800, delayMillis = 400), RepeatMode.Restart), label = "s3")
    val alpha3 by ring3.animateFloat(1f, 0f,
        infiniteRepeatable(tween(1800, delayMillis = 400), RepeatMode.Restart), label = "a3")

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.6f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)))
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            AkibaLogo(variant = AkibaLogoVariant.Icon, size = AkibaLogoSize.Md, animated = false)

            // Rings + center icon
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                listOf(scale1 to alpha1, scale2 to alpha2, scale3 to alpha3).forEach { (s, a) ->
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer(scaleX = s, scaleY = s, alpha = a)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(primary, primary.copy(alpha = 0.5f)))),
                ) {
                    Icon(Icons.Rounded.PhoneAndroid, null,
                        tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            Text("Check your M-Pesa", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = Color.White, textAlign = TextAlign.Center)

            Text("Enter your M-Pesa PIN on your phone to complete the payment.",
                fontFamily = DmSansFontFamily, fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                lineHeight = 24.sp)

            Spacer(Modifier.height(16.dp))

            AkibaButton(
                text    = "Cancel",
                variant = AkibaButtonVariant.Ghost,
                onClick = {
                    viewModel.resetState()
                    navController.popBackStack()
                },
            )
        }
    }
}
