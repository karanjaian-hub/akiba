package com.akiba.app.ui.components.skeleton

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akiba.app.ui.theme.AkibaShapes
import com.akiba.app.ui.theme.akibaColors

// ── Shared shimmer brush ──────────────────────────────────────────────────────
// Extracted so all shimmer composables share one InfiniteTransition
@Composable
private fun shimmerBrush(): Brush {
    val shimmerBase      = MaterialTheme.akibaColors.shimmerBase
    val shimmerHighlight = MaterialTheme.akibaColors.shimmerHighlight

    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue   = 0f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    // Diagonal sweep — more premium than a flat left-to-right
    return Brush.linearGradient(
        colors     = listOf(shimmerBase, shimmerBase, shimmerHighlight, shimmerBase, shimmerBase),
        start      = Offset(offset * 1000f - 500f, offset * 500f - 250f),
        end        = Offset(offset * 1000f,         offset * 500f),
    )
}

// ── Building blocks ───────────────────────────────────────────────────────────

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(AkibaShapes.card)
            .background(shimmerBrush())
    )
}

@Composable
fun ShimmerText(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.6f,
    height: Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
            .background(shimmerBrush())
    )
}

@Composable
fun ShimmerAvatar(size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

// ── Full balance card placeholder ─────────────────────────────────────────────

@Composable
fun ShimmerBalanceCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AkibaShapes.card)
            .background(MaterialTheme.akibaColors.shimmerBase)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Label line
        ShimmerText(widthFraction = 0.3f, height = 12.dp)
        // Balance amount — larger
        ShimmerText(widthFraction = 0.6f, height = 36.dp)
        Spacer(Modifier.height(4.dp))
        // Two stat pills side by side
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerText(modifier = Modifier.weight(1f), height = 48.dp)
            ShimmerText(modifier = Modifier.weight(1f), height = 48.dp)
        }
    }
}
