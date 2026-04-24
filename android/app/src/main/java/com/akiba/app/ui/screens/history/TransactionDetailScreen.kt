package com.akiba.app.ui.screens.history

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.data.remote.dto.TransactionDto
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.components.skeleton.ShimmerCard
import com.akiba.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    navController  : NavHostController,
    transactionId  : String,
    viewModel      : TransactionViewModel = hiltViewModel(),
) {
    val primary = MaterialTheme.colorScheme.primary
    val accent  = MaterialTheme.akibaColors.accentGreen
    val error   = MaterialTheme.colorScheme.error

    // Find the transaction from the already-loaded list
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val tx: TransactionDto? = remember(transactions, transactionId) {
        transactions.find { it.id == transactionId }
    }

    var showRaw by remember { mutableStateOf(false) }

    val isCredit = tx?.type == "credit"
    val amountColor = if (isCredit) accent else error

    // Category style
    val (categoryColor, emoji) = remember(tx?.category) {
        when (tx?.category?.lowercase()) {
            "food"          -> accent                to "🍽"
            "transport"     -> primary               to "🚗"
            "rent"          -> primary               to "🏠"
            "bills"         -> Color(0xFFF59E0B)     to "⚡"
            "entertainment" -> Color(0xFF8B5CF6)     to "🎬"
            "health"        -> Color(0xFF10B981)     to "💊"
            "income"        -> accent                to "💵"
            else            -> primary               to "💳"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Transaction", fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                },
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
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(4) { ShimmerCard(height = 80.dp) }
            }
        } else if (tx == null) {
            // Transaction not found in loaded list
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Transaction not found", fontFamily = DmSansFontFamily,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else {
            Column(
                modifier              = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(16.dp),
            ) {
                // Category circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.15f)),
                ) {
                    Text(emoji, fontSize = 32.sp)
                }

                Text(tx.merchant, fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface)

                Text(
                    "${if (isCredit) "+" else "-"}Ksh ${"%,.2f".format(tx.amount)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 36.sp,
                    color      = amountColor,
                )

                // Details card
                GlassCard(elevation = GlassElevation.Raised) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            "Reference" to (tx.reference ?: "—"),
                            "Source"    to tx.source.replaceFirstChar { it.uppercase() },
                            "Date"      to tx.date.take(10),
                            "Type"      to tx.type.replaceFirstChar { it.uppercase() },
                        ).forEachIndexed { index, (label, value) ->
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(label, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(value, fontFamily = JetBrainsMonoFamily, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (index < 3) Divider(color = MaterialTheme.akibaColors.glassBorder)
                        }
                    }
                }

                // Category row
                GlassCard(elevation = GlassElevation.Flat) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Category", fontFamily = DmSansFontFamily, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AkibaBadge(tx.category, variant = BadgeVariant.Info)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { /* TODO: category edit sheet */ }) {
                                Text("Change", color = primary, fontFamily = DmSansFontFamily)
                            }
                        }
                    }
                }

                // Anomalous flag
                if (tx.isAnomalous) {
                    GlassCard(elevation = GlassElevation.Flat) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning,
                                null, tint = MaterialTheme.akibaColors.gold,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("This transaction was flagged as unusual.",
                                fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                color = MaterialTheme.akibaColors.gold)
                        }
                    }
                }

                // Raw SMS toggle — only shown if rawText exists
                if (tx.rawText != null) {
                    GlassCard(elevation = GlassElevation.Flat) {
                        Column {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text("Original message", fontFamily = DmSansFontFamily,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                                TextButton(onClick = { showRaw = !showRaw }) {
                                    Text(if (showRaw) "Hide" else "Show",
                                        color = primary, fontFamily = DmSansFontFamily)
                                }
                            }
                            AnimatedVisibility(visible = showRaw) {
                                Text(tx.rawText, fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp, lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}
