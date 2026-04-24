package com.akiba.app.ui.screens.payments

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.AkibaButton
import com.akiba.app.ui.components.common.AkibaButtonVariant
import com.akiba.app.ui.theme.*

@Composable
fun PaymentFailedScreen(
    navController: NavHostController,
    reason       : String,
) {
    val error = MaterialTheme.colorScheme.error

    val iconScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        iconScale.animateTo(1f, spring(stiffness = 120f, dampingRatio = 0.55f))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(error, error.copy(alpha = 0.6f)))),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier            = Modifier.padding(32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(80.dp)
                    .graphicsLayer(scaleX = iconScale.value, scaleY = iconScale.value)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Icon(Icons.Rounded.Close, null,
                    tint = Color.White, modifier = Modifier.size(48.dp))
            }

            Text("Payment Failed", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 26.sp,
                color = Color.White, textAlign = TextAlign.Center)

            Text(reason, fontFamily = DmSansFontFamily, fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
                lineHeight = 24.sp)

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AkibaButton(
                    text     = "Try Again",
                    onClick  = { navController.popBackStack() },
                    modifier = Modifier.weight(1f),
                )
                AkibaButton(
                    text    = "Go Home",
                    variant = AkibaButtonVariant.Ghost,
                    onClick = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
