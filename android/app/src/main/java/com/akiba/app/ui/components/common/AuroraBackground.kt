package com.akiba.app.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*

@Composable
fun AuroraBackground(
    primary  : Color,
    secondary: Color,
    accent   : Color,
    intensity: Float = 1f, // 0f = subtle, 1f = full
) {
    val infinite = rememberInfiniteTransition(label = "aurora")
    val offset by infinite.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000), RepeatMode.Reverse),
        label         = "auroraOffset",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.35f * intensity), Color.Transparent),
                center = Offset(w * (0.2f + offset * 0.3f), h * 0.15f),
                radius = w * 0.6f,
            ),
            radius = w * 0.6f,
            center = Offset(w * (0.2f + offset * 0.3f), h * 0.15f),
        )
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.2f * intensity), Color.Transparent),
                center = Offset(w * 0.8f, h * (0.3f + offset * 0.2f)),
                radius = w * 0.5f,
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.8f, h * (0.3f + offset * 0.2f)),
        )
        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.12f * intensity), Color.Transparent),
                center = Offset(w * 0.3f, h * (0.6f + offset * 0.2f)),
                radius = w * 0.4f,
            ),
            radius = w * 0.4f,
            center = Offset(w * 0.3f, h * (0.6f + offset * 0.2f)),
        )
    }
}
