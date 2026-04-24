package com.akiba.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Brand primitives ──────────────────────────────────────────────────────────
val Purple950  = Color(0xFF2E0061)
val Purple800  = Color(0xFF4C1D95)
val Purple600  = Color(0xFF7C3AED)
val Purple400  = Color(0xFFA78BFA)
val Blue800    = Color(0xFF1E40AF)
val Blue500    = Color(0xFF3B82F6)
val Green800   = Color(0xFF065F46)
val Green500   = Color(0xFF10B981)
val Gold600    = Color(0xFFD97706)
val Gold400    = Color(0xFFF59E0B)
val Navy950    = Color(0xFF0D0D1A)
val Navy900    = Color(0xFF13132B)
val Gray100    = Color(0xFFF9FAFB)
val Gray900    = Color(0xFF111827)
val OffWhite   = Color(0xFFF8F7FF)
val ErrorRed   = Color(0xFFDC2626)
val ErrorLight = Color(0xFFF87171)

// ── Material 3 schemes ────────────────────────────────────────────────────────
val AkibaDarkColorScheme = darkColorScheme(
    primary        = Purple600,
    onPrimary      = OffWhite,
    secondary      = Blue500,
    onSecondary    = OffWhite,
    background     = Navy950,
    onBackground   = OffWhite,
    surface        = Navy900,
    onSurface      = OffWhite,
    error          = ErrorLight,
    onError        = Navy950,
)

val AkibaLightColorScheme = lightColorScheme(
    primary        = Purple800,
    onPrimary      = OffWhite,
    secondary      = Blue800,
    onSecondary    = OffWhite,
    background     = Gray100,
    onBackground   = Gray900,
    surface        = Color.White,
    onSurface      = Gray900,
    error          = ErrorRed,
    onError        = Color.White,
)

// ── Extra colors not covered by Material 3 ────────────────────────────────────
data class AkibaExtraColors(
    val accentGreen: Color,
    val gold: Color,
    val goldLight: Color,
    // Frosted glass effects — white at very low alpha
    val glassOverlay: Color,
    val glassBorder: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    val successGreen: Color,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
)

val DarkExtraColors = AkibaExtraColors(
    accentGreen      = Green500,
    gold             = Gold400,
    goldLight        = Gold400.copy(alpha = 0.6f),
    glassOverlay     = Color.White.copy(alpha = 0.05f),
    glassBorder      = Color.White.copy(alpha = 0.08f),
    shimmerBase      = Navy900,
    shimmerHighlight = Color.White.copy(alpha = 0.12f),
    successGreen     = Green500,
    cardGradientStart = Purple600.copy(alpha = 0.8f),
    cardGradientEnd   = Blue500.copy(alpha = 0.6f),
)

val LightExtraColors = AkibaExtraColors(
    accentGreen      = Green800,
    gold             = Gold600,
    goldLight        = Gold600.copy(alpha = 0.6f),
    glassOverlay     = Color.Black.copy(alpha = 0.03f),
    glassBorder      = Color.Black.copy(alpha = 0.08f),
    shimmerBase      = Gray100,
    shimmerHighlight = Color.White.copy(alpha = 0.8f),
    successGreen     = Green800,
    cardGradientStart = Purple800.copy(alpha = 0.8f),
    cardGradientEnd   = Blue800.copy(alpha = 0.6f),
)

// CompositionLocal so any composable can access extra colors via AkibaTheme
val LocalAkibaColors = staticCompositionLocalOf { DarkExtraColors }
