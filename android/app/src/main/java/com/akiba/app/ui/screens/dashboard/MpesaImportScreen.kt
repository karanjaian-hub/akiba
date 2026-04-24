package com.akiba.app.ui.screens.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpesaImportScreen(navController: NavHostController) {
    val primary    = MaterialTheme.colorScheme.primary
    var activeTab  by remember { mutableIntStateOf(0) }

    // ── Import states ─────────────────────────────────────────────────────
    var isProcessing by remember { mutableStateOf(false) }
    var isSuccess    by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var smsText      by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<String?>(null) }

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
                    Text("Import M-Pesa", fontFamily = SoraFontFamily,
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
            // ── Custom tab row ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AkibaShapes.button)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.button)
                    .padding(4.dp),
            ) {
                listOf("Paste SMS", "Upload PDF").forEachIndexed { index, label ->
                    val isActive = activeTab == index
                    val bgColor by animateColorAsState(
                        targetValue = if (isActive) primary else Color.Transparent,
                        label       = "tabBg$index",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .weight(1f)
                            .clip(AkibaShapes.button)
                            .background(bgColor)
                            .clickable { activeTab = index }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text       = label,
                            color      = if (isActive) Color.White
                                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontFamily = DmSansFontFamily,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize   = 14.sp,
                        )
                    }
                }
            }

            // ── Tab content ───────────────────────────────────────────────
            AnimatedContent(
                targetState   = activeTab,
                transitionSpec = {
                    if (targetState > initialState)
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    else
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                },
                label = "tabContent",
            ) { tab ->
                when (tab) {
                    0 -> SmsTab(smsText = smsText, onSmsChange = { smsText = it })
                    1 -> PdfTab(
                        selectedFile = selectedFile,
                        onPickFile   = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    )
                }
            }

            // ── Processing / success / error states ───────────────────────
            AnimatedVisibility(visible = isProcessing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color    = primary,
                    )
                    Text("Processing your statement...",
                        fontFamily = DmSansFontFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            AnimatedVisibility(visible = isSuccess) {
                GlassCard(elevation = GlassElevation.Raised) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null,
                            tint = MaterialTheme.akibaColors.accentGreen,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Import successful!", fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                color = MaterialTheme.akibaColors.accentGreen)
                            Text("Transactions have been added to your history.",
                                fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            errorMessage?.let { err ->
                GlassCard(elevation = GlassElevation.Flat) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(err, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { errorMessage = null }) {
                                Text("Try again", color = primary)
                            }
                        }
                    }
                }
            }

            // ── Import button ─────────────────────────────────────────────
            AkibaButton(
                text     = "Import",
                loading  = isProcessing,
                enabled  = (activeTab == 0 && smsText.isNotBlank()) ||
                           (activeTab == 1 && selectedFile != null),
                modifier = Modifier.fillMaxWidth(),
                onClick  = {
                    isProcessing = true
                    // TODO: wire to backend import endpoint in Phase 4 backend hookup
                },
            )
        }
    }
}

@Composable
private fun SmsTab(smsText: String, onSmsChange: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Paste your M-Pesa SMS messages below",
            fontFamily = DmSansFontFamily, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AkibaShapes.input)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.input),
        ) {
            OutlinedTextField(
                value         = smsText,
                onValueChange = onSmsChange,
                modifier      = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder   = {
                    Text("FBK9JN3Q Confirmed.\nKsh1,200 sent to JANE DOE...",
                        fontFamily = DmSansFontFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                },
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = DmSansFontFamily, fontSize = 14.sp,
                ),
            )
            if (smsText.isNotEmpty()) {
                Text(
                    text     = "${smsText.length} chars",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    fontSize = 11.sp,
                    color    = primary,
                    fontFamily = DmSansFontFamily,
                )
            }
        }
    }
}

@Composable
private fun PdfTab(selectedFile: String?, onPickFile: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Dashed upload zone
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .fillMaxWidth()
                .height(180.dp)
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
                .clickable(onClick = onPickFile)
                .padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CloudUpload, null,
                    tint     = primary,
                    modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Tap to select PDF", fontFamily = DmSansFontFamily,
                    fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("M-Pesa statement PDF from your bank app",
                    fontFamily = DmSansFontFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }

        // Selected file card
        AnimatedVisibility(
            visible = selectedFile != null,
            enter   = slideInVertically { it } + fadeIn(),
        ) {
            GlassCard(elevation = GlassElevation.Raised) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PictureAsPdf, null,
                        tint = primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedFile ?: "", fontFamily = DmSansFontFamily,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Ready to import", fontFamily = DmSansFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.akibaColors.accentGreen)
                    }
                    Icon(Icons.Rounded.CheckCircle, null,
                        tint = MaterialTheme.akibaColors.accentGreen,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
