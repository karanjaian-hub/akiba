package com.akiba.app.ui.screens.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankImportScreen(navController: NavHostController) {
    val primary    = MaterialTheme.colorScheme.primary
    val secondary  = MaterialTheme.colorScheme.secondary

    val banks = listOf("KCB", "Equity", "Co-op", "NCBA", "Absa", "Standard Chartered", "DTB", "I&M")
    val bankColors = listOf(primary, secondary, MaterialTheme.akibaColors.accentGreen,
        MaterialTheme.akibaColors.gold, primary.copy(alpha = 0.7f), secondary.copy(alpha = 0.7f),
        primary, secondary)

    var selectedBank by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSuccess    by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedFile = it.lastPathSegment ?: "statement.pdf" }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Import Bank Statement", fontFamily = SoraFontFamily,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Select your bank", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface)

            // Bank chip row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(banks.zip(bankColors)) { (bank, color) ->
                    val isSelected = selectedBank == bank
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) color else Color.Transparent,
                        label       = "bankBg$bank",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .clip(AkibaShapes.chip)
                            .background(bgColor)
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent
                                else MaterialTheme.akibaColors.glassBorder,
                                AkibaShapes.chip,
                            )
                            .clickable { selectedBank = bank }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White.copy(alpha = 0.3f) else color.copy(alpha = 0.15f)),
                        ) {
                            Text(bank.first().toString(),
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = if (isSelected) Color.White else color,
                                fontFamily = SoraFontFamily)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(bank, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                            color = if (isSelected) Color.White
                                    else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // PDF upload zone
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(AkibaShapes.card)
                    .drawBehind {
                        drawRoundRect(
                            color        = primary,
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style        = Stroke(
                                width      = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)),
                            ),
                        )
                    }
                    .clickable { pdfLauncher.launch(arrayOf("application/pdf")) }
                    .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CloudUpload, null,
                        tint = primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to select bank statement PDF",
                        fontFamily = DmSansFontFamily, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Selected file
            AnimatedVisibility(visible = selectedFile != null, enter = fadeIn() + expandVertically()) {
                GlassCard(elevation = GlassElevation.Raised) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PictureAsPdf, null,
                            tint = primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(selectedFile ?: "", fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.CheckCircle, null,
                            tint = MaterialTheme.akibaColors.accentGreen,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Processing
            AnimatedVisibility(visible = isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color    = primary,
                )
            }

            // Success
            AnimatedVisibility(visible = isSuccess) {
                GlassCard(elevation = GlassElevation.Raised) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null,
                            tint = MaterialTheme.akibaColors.accentGreen,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Import successful!", fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            color = MaterialTheme.akibaColors.accentGreen)
                    }
                }
            }

            AkibaButton(
                text     = "Import Statement",
                loading  = isProcessing,
                enabled  = selectedBank != null && selectedFile != null,
                modifier = Modifier.fillMaxWidth(),
                onClick  = {
                    isProcessing = true
                    // TODO: wire to backend import endpoint
                },
            )
        }
    }
}
