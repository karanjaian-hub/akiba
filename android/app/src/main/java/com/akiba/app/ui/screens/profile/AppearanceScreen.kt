package com.akiba.app.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(navController: NavHostController) {
    val primary = MaterialTheme.colorScheme.primary
    var selected by remember { mutableStateOf("auto") }

    val options = listOf(
        Triple("auto",  Icons.Rounded.Brightness4, "Matches your device setting"),
        Triple("light", Icons.Rounded.WbSunny,     "Always light mode"),
        Triple("dark",  Icons.Rounded.DarkMode,    "Always dark mode"),
    )
    val labels = mapOf("auto" to "Auto", "light" to "Light", "dark" to "Dark")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Appearance", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { (mode, icon, description) ->
                val isSelected = selected == mode
                val borderColor by animateColorAsState(
                    targetValue   = if (isSelected) primary else MaterialTheme.akibaColors.glassBorder,
                    animationSpec = tween(200),
                    label         = "borderColor$mode",
                )
                val scale by animateFloatAsState(
                    targetValue   = if (isSelected) 1f else 0.98f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "scale$mode",
                )

                GlassCard(
                    elevation = if (isSelected) GlassElevation.Raised else GlassElevation.Flat,
                    onClick   = { selected = mode },
                    modifier  = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = borderColor,
                            shape = AkibaShapes.card,
                        ),
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null,
                                tint     = if (isSelected) primary
                                           else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(labels[mode] ?: mode, fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(description, fontFamily = DmSansFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        if (isSelected) {
                            Icon(Icons.Rounded.CheckCircle, null,
                                tint     = primary,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}
