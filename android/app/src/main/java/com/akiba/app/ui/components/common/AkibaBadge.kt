package com.akiba.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akiba.app.ui.theme.AkibaShapes
import com.akiba.app.ui.theme.DmSansFontFamily
import com.akiba.app.ui.theme.akibaColors

enum class BadgeVariant { Success, Warning, Danger, Info, Neutral, Purple }
enum class BadgeSize    { Sm, Md }

@Composable
fun AkibaBadge(
    text: String,
    variant: BadgeVariant = BadgeVariant.Neutral,
    size: BadgeSize = BadgeSize.Md,
) {
    val akiba    = MaterialTheme.akibaColors
    val primary  = MaterialTheme.colorScheme.primary
    val error    = MaterialTheme.colorScheme.error
    val secondary= MaterialTheme.colorScheme.secondary

    // Each variant: base color drives both the background (10% alpha) and text
    val baseColor: Color = when (variant) {
        BadgeVariant.Success -> akiba.accentGreen
        BadgeVariant.Warning -> akiba.gold
        BadgeVariant.Danger  -> error
        BadgeVariant.Info    -> secondary
        BadgeVariant.Neutral -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        BadgeVariant.Purple  -> primary
    }

    val verticalPadding   = if (size == BadgeSize.Sm) 4.dp  else 6.dp
    val horizontalPadding = if (size == BadgeSize.Sm) 8.dp  else 12.dp
    val fontSize          = if (size == BadgeSize.Sm) 10.sp else 12.sp

    Text(
        text       = text,
        color      = baseColor,
        fontSize   = fontSize,
        fontFamily = DmSansFontFamily,
        modifier   = Modifier
            .clip(AkibaShapes.badge)
            .background(baseColor.copy(alpha = 0.10f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    )
}
