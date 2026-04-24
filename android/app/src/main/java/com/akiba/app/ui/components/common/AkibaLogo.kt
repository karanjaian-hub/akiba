package com.akiba.app.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akiba.app.ui.theme.akibaColors
import com.akiba.app.ui.theme.SoraFontFamily
import com.akiba.app.ui.theme.DmSansFontFamily

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class AkibaLogoSize(val dp: Dp) {
    Sm(32.dp), Md(48.dp), Lg(64.dp), Xl(96.dp), Hero(128.dp)
}

enum class AkibaLogoVariant {
    Icon,       // icon only
    Wordmark,   // icon + "AKIBA" text in a row
    Full,       // Wordmark + tagline below
    Hero        // large centered icon + AKIBA + tagline
}

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun AkibaLogo(
    modifier: Modifier = Modifier,
    size: AkibaLogoSize = AkibaLogoSize.Md,
    variant: AkibaLogoVariant = AkibaLogoVariant.Wordmark,
    animated: Boolean = true,
) {
    val primary = MaterialTheme.colorScheme.primary
    val gold    = MaterialTheme.akibaColors.gold

    // ── Entry animation state ─────────────────────────────────────────────
    val iconScale  = remember { Animatable(if (animated) 0.5f else 1f) }
    val iconAlpha  = remember { Animatable(if (animated) 0f   else 1f) }
    val textAlpha  = remember { Animatable(if (animated) 0f   else 1f) }
    val tagAlpha   = remember { Animatable(if (animated) 0f   else 1f) }

    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        // Icon springs in
        iconScale.animateTo(1f, spring(stiffness = 120f, dampingRatio = 0.6f))
        iconAlpha.animateTo(1f, tween(300))
        // Wordmark fades in after icon settles
        textAlpha.animateTo(1f, tween(400))
        // Tagline last
        tagAlpha.animateTo(1f, tween(300))
    }

    when (variant) {
        AkibaLogoVariant.Icon ->
            AkibaIconCanvas(size, primary, gold, iconScale.value, iconAlpha.value, modifier)

        AkibaLogoVariant.Wordmark ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                AkibaIconCanvas(size, primary, gold, iconScale.value, iconAlpha.value)
                Spacer(Modifier.width(10.dp))
                AkibaWordmark(size, textAlpha.value, primary)
            }

        AkibaLogoVariant.Full ->
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AkibaIconCanvas(size, primary, gold, iconScale.value, iconAlpha.value)
                    Spacer(Modifier.width(10.dp))
                    AkibaWordmark(size, textAlpha.value, primary)
                }
                Spacer(Modifier.height(6.dp))
                AkibaTagline(tagAlpha.value, gold)
            }

        AkibaLogoVariant.Hero ->
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                AkibaIconCanvas(size, primary, gold, iconScale.value, iconAlpha.value)
                Spacer(Modifier.height(12.dp))
                AkibaWordmark(AkibaLogoSize.Xl, textAlpha.value, primary)
                Spacer(Modifier.height(8.dp))
                AkibaTagline(tagAlpha.value, gold)
            }
    }
}

// ── Icon — drawn with Canvas paths ───────────────────────────────────────────

@Composable
private fun AkibaIconCanvas(
    size: AkibaLogoSize,
    primary: Color,
    gold: Color,
    scale: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(size.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
    ) {
        val w = this.size.width
        val h = this.size.height

        // Gradient fill for the A shape
        val brush = Brush.verticalGradient(
            colors = listOf(primary, primary.copy(alpha = 0.6f))
        )

        // ── Draw stylized 'A' with Path ───────────────────────────────────
        val path = Path().apply {
            // Left leg: bottom-left → apex
            moveTo(w * 0.05f, h * 0.92f)
            cubicTo(
                w * 0.1f,  h * 0.7f,
                w * 0.2f,  h * 0.4f,
                w * 0.5f,  h * 0.08f,  // apex
            )
            // Right leg: apex → bottom-right
            cubicTo(
                w * 0.8f,  h * 0.4f,
                w * 0.9f,  h * 0.7f,
                w * 0.95f, h * 0.92f,
            )
            // Right leg inner
            lineTo(w * 0.78f, h * 0.92f)
            cubicTo(
                w * 0.74f, h * 0.72f,
                w * 0.66f, h * 0.52f,
                w * 0.5f,  h * 0.28f,
            )
            // Left leg inner
            cubicTo(
                w * 0.34f, h * 0.52f,
                w * 0.26f, h * 0.72f,
                w * 0.22f, h * 0.92f,
            )
            close()
        }
        drawPath(path, brush = brush)

        // ── Crossbar ──────────────────────────────────────────────────────
        val crossbarY = h * 0.60f
        val crossbarPath = Path().apply {
            moveTo(w * 0.28f, crossbarY)
            lineTo(w * 0.72f, crossbarY)
            lineTo(w * 0.68f, crossbarY + h * 0.07f)
            lineTo(w * 0.32f, crossbarY + h * 0.07f)
            close()
        }
        drawPath(crossbarPath, brush = brush)

        // ── Gold coin at crossbar midpoint ────────────────────────────────
        val coinRadius = w * 0.12f
        val coinCenter = Offset(w * 0.5f, crossbarY + h * 0.035f)

        drawCircle(color = gold, radius = coinRadius, center = coinCenter)

        // Shilling 'S' symbol inside coin — simplified as two arcs + line
        val sPath = Path().apply {
            moveTo(coinCenter.x + coinRadius * 0.35f, coinCenter.y - coinRadius * 0.5f)
            cubicTo(
                coinCenter.x - coinRadius * 0.5f, coinCenter.y - coinRadius * 0.6f,
                coinCenter.x - coinRadius * 0.5f, coinCenter.y,
                coinCenter.x,                     coinCenter.y,
            )
            cubicTo(
                coinCenter.x + coinRadius * 0.5f, coinCenter.y,
                coinCenter.x + coinRadius * 0.5f, coinCenter.y + coinRadius * 0.6f,
                coinCenter.x - coinRadius * 0.35f, coinCenter.y + coinRadius * 0.5f,
            )
        }
        drawPath(
            path   = sPath,
            color  = Color.White,
            style  = Stroke(width = w * 0.03f, cap = StrokeCap.Round),
        )
    }
}

// ── "AKIBA" wordmark ──────────────────────────────────────────────────────────

@Composable
private fun AkibaWordmark(size: AkibaLogoSize, alpha: Float, color: Color) {
    Text(
        text       = "AKIBA",
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = (size.dp.value * 0.45f).sp,
        color      = color.copy(alpha = alpha),
        letterSpacing = 3.sp,
    )
}

// ── Tagline ───────────────────────────────────────────────────────────────────

@Composable
private fun AkibaTagline(alpha: Float, color: Color) {
    Text(
        text       = "Every shilling has a story.",
        fontFamily = DmSansFontFamily,
        fontStyle  = FontStyle.Italic,
        fontSize   = 12.sp,
        color      = color.copy(alpha = alpha),
    )
}
