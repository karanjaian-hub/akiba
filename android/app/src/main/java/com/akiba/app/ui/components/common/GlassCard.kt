package com.akiba.app.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.akiba.app.ui.theme.AkibaShapes
import com.akiba.app.ui.theme.akibaColors

enum class GlassElevation { Flat, Raised, Elevated, Hero }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: GlassElevation = GlassElevation.Raised,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface     = MaterialTheme.colorScheme.surface
    val glassBorder = MaterialTheme.akibaColors.glassBorder
    val primary     = MaterialTheme.colorScheme.primary

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "cardScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(AkibaShapes.card)
            // Shadow for depth — drawn behind the card
            .drawBehind {
                if (elevation != GlassElevation.Flat) {
                    drawRoundRect(
                        color        = Color.Black.copy(
                            alpha = when (elevation) {
                                GlassElevation.Raised   -> 0.15f
                                GlassElevation.Elevated -> 0.25f
                                GlassElevation.Hero     -> 0.35f
                                else                   -> 0f
                            }
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                        topLeft      = androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx()),
                    )
                }
            }
            .background(surface.copy(alpha = 0.7f))
            // Hero gets a subtle gradient overlay on the top edge
            .then(
                if (elevation == GlassElevation.Hero)
                    Modifier.background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(primary.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
                else Modifier
            )
            .border(1.dp, glassBorder, AkibaShapes.card)
            .then(
                if (onClick != null)
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                                onClick()
                            }
                        )
                    }
                else Modifier
            )
            .padding(16.dp),
        content = content,
    )
}

// Alias so both names work — GlassCard and AkibaCard are the same thing
val AkibaCard = @Composable { modifier: Modifier,
                               onClick: (() -> Unit)?,
                               elevation: GlassElevation,
                               content: @Composable BoxScope.() -> Unit ->
    GlassCard(modifier, onClick, elevation, content)
}
