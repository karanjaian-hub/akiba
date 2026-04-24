package com.akiba.app.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.akiba.app.ui.theme.*

enum class AkibaButtonVariant { Primary, Secondary, Success, Outline, Ghost, Danger }
enum class AkibaButtonSize(val height: Dp, val fontSize: TextUnit) {
    Sm(32.dp, 13.sp), Md(48.dp, 15.sp), Lg(56.dp, 16.sp)
}

@Composable
fun AkibaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AkibaButtonVariant = AkibaButtonVariant.Primary,
    size: AkibaButtonSize = AkibaButtonSize.Md,
    loading: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val error     = MaterialTheme.colorScheme.error

    // ── Press scale with spring ───────────────────────────────────────────
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label         = "buttonScale",
    )

    val backgroundBrush: Brush? = when (variant) {
        AkibaButtonVariant.Primary   -> Brush.horizontalGradient(listOf(primary, primary.copy(alpha = 0.7f)))
        AkibaButtonVariant.Secondary -> Brush.horizontalGradient(listOf(secondary, secondary.copy(alpha = 0.7f)))
        AkibaButtonVariant.Success   -> Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.7f)))
        AkibaButtonVariant.Danger    -> Brush.horizontalGradient(listOf(error, error.copy(alpha = 0.8f)))
        else                         -> null
    }

    val contentColor = when (variant) {
        AkibaButtonVariant.Outline, AkibaButtonVariant.Ghost -> primary
        else -> Color.White
    }

    val isInteractive = enabled && !loading
    val radius = 14.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(size.height)
            .clip(AkibaShapes.button)
            // Glow: drawn as a slightly larger semi-transparent rect behind the button
            .drawBehind {
                if (variant == AkibaButtonVariant.Primary && isInteractive) {
                    val glowInset = -6.dp.toPx()
                    drawRoundRect(
                        color        = primary.copy(alpha = 0.25f),
                        topLeft      = Offset(glowInset, glowInset),
                        size         = Size(this.size.width - glowInset * 2, this.size.height - glowInset * 2),
                        cornerRadius = CornerRadius(radius.toPx()),
                    )
                }
                // Outline border
                if (variant == AkibaButtonVariant.Outline) {
                    drawRoundRect(
                        color        = primary,
                        cornerRadius = CornerRadius(radius.toPx()),
                        style        = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
            .then(
                if (backgroundBrush != null) Modifier.background(backgroundBrush)
                else Modifier.background(Color.Transparent)
            )
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .pointerInput(isInteractive) {
                if (!isInteractive) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        onClick()
                    }
                )
            }
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(18.dp),
                color       = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text       = text,
                    color      = contentColor.copy(alpha = if (enabled) 1f else 0.4f),
                    fontSize   = size.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = DmSansFontFamily,
                )
            }
        }
    }
}
