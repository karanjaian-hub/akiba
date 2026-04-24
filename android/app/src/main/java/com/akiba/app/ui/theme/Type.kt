package com.akiba.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Font families ─────────────────────────────────────────────────────────────
// Placeholder FontFamily objects — replace Font() refs with actual font files
// once you add them to res/font/. The app will use system default until then.
val SoraFontFamily = FontFamily.Default       // TODO: replace with Sora font files
val DmSansFontFamily = FontFamily.Default     // TODO: replace with DM Sans font files
val JetBrainsMonoFamily = FontFamily.Monospace // TODO: replace with JetBrains Mono files

// ── Typography ────────────────────────────────────────────────────────────────
val AkibaTypography = Typography(
    displayLarge  = TextStyle(fontFamily = SoraFontFamily,     fontWeight = FontWeight.Bold,   fontSize = 48.sp),
    displayMedium = TextStyle(fontFamily = SoraFontFamily,     fontWeight = FontWeight.Bold,   fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = SoraFontFamily,     fontWeight = FontWeight.Bold,   fontSize = 28.sp),
    headlineMedium= TextStyle(fontFamily = SoraFontFamily,     fontWeight = FontWeight.SemiBold,fontSize = 24.sp),
    titleLarge    = TextStyle(fontFamily = SoraFontFamily,     fontWeight = FontWeight.SemiBold,fontSize = 20.sp),
    titleMedium   = TextStyle(fontFamily = DmSansFontFamily,   fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge     = TextStyle(fontFamily = DmSansFontFamily,   fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium    = TextStyle(fontFamily = DmSansFontFamily,   fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall    = TextStyle(fontFamily = DmSansFontFamily,   fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

// Extra style for financial amounts — monospaced so digits don't shift width
val MoneyDisplayStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize   = 36.sp,
)
