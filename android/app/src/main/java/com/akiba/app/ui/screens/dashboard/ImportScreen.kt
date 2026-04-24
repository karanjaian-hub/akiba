package com.akiba.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(navController: NavHostController) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    var showMpesa by remember { mutableStateOf(false) }
    var showBank  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100); showMpesa = true
        kotlinx.coroutines.delay(100); showBank  = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Import Statements", fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AuroraBackground(primary, secondary, MaterialTheme.akibaColors.accentGreen, 0.3f)

            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── M-Pesa import card ────────────────────────────────────
                AnimatedVisibility(
                    visible = showMpesa,
                    enter   = fadeIn(tween(400)) + slideInVertically { -20 },
                ) {
                    ImportOptionCard(
                        title       = "Import M-Pesa",
                        subtitle    = "Paste SMS or upload PDF statement",
                        lastImport  = "Never imported",
                        accentColor = primary,
                        icon        = Icons.Rounded.PhoneAndroid,
                        onClick     = { navController.navigate("mpesa_import") },
                    )
                }

                // ── Bank import card ──────────────────────────────────────
                AnimatedVisibility(
                    visible = showBank,
                    enter   = fadeIn(tween(400)) + slideInVertically { -20 },
                ) {
                    ImportOptionCard(
                        title       = "Import Bank Statement",
                        subtitle    = "Upload PDF from your bank's app",
                        lastImport  = "Never imported",
                        accentColor = secondary,
                        icon        = Icons.Rounded.AccountBalance,
                        onClick     = { navController.navigate("bank_import") },
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Info note
                GlassCard(elevation = GlassElevation.Flat) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null,
                            tint     = MaterialTheme.akibaColors.gold,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = "Your data is encrypted and processed locally. " +
                                         "Akiba never shares your financial data.",
                            fontFamily = DmSansFontFamily,
                            fontSize   = 13.sp,
                            lineHeight  = 20.sp,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportOptionCard(
    title      : String,
    subtitle   : String,
    lastImport : String,
    accentColor: Color,
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    onClick    : () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AkibaShapes.card)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.card)
            .drawBehind {
                drawRoundRect(
                    color        = accentColor,
                    size         = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier              = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, fontFamily = SoraFontFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    Text(lastImport, fontFamily = DmSansFontFamily, fontSize = 11.sp,
                        color = accentColor.copy(alpha = 0.7f))
                }
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}
