package com.akiba.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.data.remote.dto.*
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.components.skeleton.*
import com.akiba.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    viewModel    : DashboardViewModel = hiltViewModel(),
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val balanceVisible by viewModel.balanceVisible.collectAsStateWithLifecycle()
    val scope          = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()

    // ── Staggered section visibility ──────────────────────────────────────
    val sectionCount = 7
    val sectionVisible = remember { List(sectionCount) { mutableStateOf(false) } }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            sectionVisible.forEachIndexed { index, state ->
                delay(index * 80L)
                state.value = true
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh    = { viewModel.refresh() },
        state        = pullRefreshState,
        modifier     = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding      = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = sectionVisible[0].value,
                    enter   = fadeIn(tween(400)) + slideInVertically { -20 },
                ) {
                    HeaderSection(
                        userName    = uiState.summary?.let { "User" } ?: "...",
                        unreadCount = uiState.unreadCount,
                        onBellClick = { navController.navigate(Screen.Notifications.route) },
                    )
                }
            }

            // ── Balance card ──────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = sectionVisible[1].value,
                    enter   = fadeIn(tween(400)) + slideInVertically { 20 },
                ) {
                    if (uiState.isLoading) {
                        ShimmerBalanceCard(modifier = Modifier.padding(horizontal = 16.dp))
                    } else {
                        BalanceCard(
                            summary        = uiState.summary,
                            balanceVisible = balanceVisible,
                            onToggleBalance= { viewModel.toggleBalance() },
                        )
                    }
                }
            }

            // ── Quick actions ─────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = sectionVisible[2].value,
                    enter   = fadeIn(tween(400)),
                ) {
                    QuickActionsGrid(navController)
                }
            }

            // ── AI insight ────────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = sectionVisible[3].value,
                    enter   = fadeIn(tween(300)) + slideInHorizontally { it },
                ) {
                    AiInsightCard(
                        onClick = { navController.navigate(Screen.AiChat.route) }
                    )
                }
            }

            // ── Budget overview ───────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = sectionVisible[4].value,
                    enter   = fadeIn(tween(400)) + slideInVertically { 20 },
                ) {
                    BudgetOverviewSection(
                        overview  = uiState.budgetOverview,
                        isLoading = uiState.isLoading,
                        onSeeAll  = { navController.navigate(Screen.Budgets.route) },
                    )
                }
            }

            // ── Savings goals ─────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = sectionVisible[5].value,
                    enter   = fadeIn(tween(400)) + slideInVertically { 20 },
                ) {
                    SavingsGoalsSection(
                        goals    = uiState.goals,
                        isLoading= uiState.isLoading,
                        onSeeAll = { navController.navigate(Screen.Savings.route) },
                    )
                }
            }

            // ── Recent transactions ───────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = sectionVisible[6].value,
                    enter   = fadeIn(tween(400)) + slideInVertically { 20 },
                ) {
                    RecentTransactionsSection(
                        isLoading = uiState.isLoading,
                        onSeeAll  = { navController.navigate(Screen.History.route) },
                    )
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
@Composable
private fun HeaderSection(
    userName   : String,
    unreadCount: Int,
    onBellClick: () -> Unit,
) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11  -> "Good morning"
        in 12..17 -> "Good afternoon"
        else      -> "Good evening"
    }
    val firstName = userName.split(" ").firstOrNull() ?: userName
    val primary   = MaterialTheme.colorScheme.primary

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Avatar initials
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(primary, primary.copy(alpha = 0.6f)))),
        ) {
            Text(
                text       = firstName.take(1).uppercase(),
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = androidx.compose.ui.graphics.Color.White,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Greeting
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "$greeting, $firstName",
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 17.sp,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text       = remember { java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()) + " · Nairobi" },
                fontFamily = DmSansFontFamily,
                fontSize   = 12.sp,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        // Bell with unread badge
        Box {
            IconButton(onClick = onBellClick) {
                Icon(Icons.Rounded.Notifications, "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            if (unreadCount > 0) {
                val badgeScale by animateFloatAsState(
                    targetValue   = 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label         = "badgeScale",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .graphicsLayer(scaleX = badgeScale, scaleY = badgeScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                ) {
                    Text(
                        text     = "$unreadCount",
                        fontSize = 8.sp,
                        color    = androidx.compose.ui.graphics.Color.White,
                        fontFamily = DmSansFontFamily,
                    )
                }
            }
        }
    }
}

// ── Balance card ──────────────────────────────────────────────────────────────
@Composable
private fun BalanceCard(
    summary        : TransactionSummaryDto?,
    balanceVisible : Boolean,
    onToggleBalance: () -> Unit,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val gold      = MaterialTheme.akibaColors.gold

    // Animate balance count-up
    val animatedBalance by animateIntAsState(
        targetValue   = summary?.totalBalance?.toInt() ?: 0,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label         = "balance",
    )
    val animatedIncome by animateIntAsState(
        targetValue   = summary?.totalIncome?.toInt() ?: 0,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "income",
    )
    val animatedExpenses by animateIntAsState(
        targetValue   = summary?.totalExpenses?.toInt() ?: 0,
        animationSpec = tween(1000, 200, FastOutSlowInEasing),
        label         = "expenses",
    )
    val animatedSaved by animateIntAsState(
        targetValue   = summary?.totalSaved?.toInt() ?: 0,
        animationSpec = tween(1000, 400, FastOutSlowInEasing),
        label         = "saved",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(AkibaShapes.card)
            .background(
                Brush.linearGradient(
                    colors = listOf(primary, primary.copy(alpha = 0.7f), secondary.copy(alpha = 0.8f)),
                    start  = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end    = androidx.compose.ui.geometry.Offset(1000f, 1000f),
                )
            )
            .padding(24.dp),
    ) {
        // Decorative circles for depth
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                radius = 120.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, -20f))
            drawCircle(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f),
                radius = 80.dp.toPx(), center = androidx.compose.ui.geometry.Offset(-20f, size.height * 0.7f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Label + toggle
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Total Balance", fontFamily = DmSansFontFamily, fontSize = 13.sp,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                IconButton(onClick = onToggleBalance, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (balanceVisible) Icons.Rounded.Visibility
                                      else Icons.Rounded.VisibilityOff,
                        contentDescription = "Toggle balance",
                        tint     = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Amount
            AnimatedContent(targetState = balanceVisible, label = "balanceAnim") { visible ->
                if (visible) {
                    Text(
                        text       = "Ksh ${"%,d".format(animatedBalance)}",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 36.sp,
                        color      = androidx.compose.ui.graphics.Color.White,
                    )
                } else {
                    Text(
                        text       = "Ksh ••••••",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 36.sp,
                        color      = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }

            Text(summary?.month ?: java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                fontFamily = DmSansFontFamily, fontSize = 13.sp,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f))

            Divider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f))

            // Stats row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem("↑", "%,d".format(animatedIncome),  "Income",  accent)
                StatItem("↓", "%,d".format(animatedExpenses), "Expenses",
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                StatItem("⬡", "%,d".format(animatedSaved),   "Saved",   gold)
            }
        }
    }
}

@Composable
private fun StatItem(icon: String, value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(AkibaShapes.card)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Text(icon, fontSize = 14.sp, color = color)
        Text(value, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
        Text(label, fontFamily = DmSansFontFamily, fontSize = 10.sp,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
    }
}

// ── Quick actions ─────────────────────────────────────────────────────────────
@Composable
private fun QuickActionsGrid(navController: NavHostController) {
    val haptic  = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary= MaterialTheme.colorScheme.secondary
    val accent  = MaterialTheme.akibaColors.accentGreen
    val gold    = MaterialTheme.akibaColors.gold

    val actions = listOf(
        Triple("Send",     Icons.Rounded.Send,          Brush.horizontalGradient(listOf(primary,    primary.copy(alpha = 0.6f)))),
        Triple("Receive",  Icons.Rounded.CallReceived,  Brush.horizontalGradient(listOf(secondary,  secondary.copy(alpha = 0.6f)))),
        Triple("Pay Bill", Icons.Rounded.Receipt,       Brush.horizontalGradient(listOf(accent,     accent.copy(alpha = 0.6f)))),
        Triple("Goals",    Icons.Rounded.TrackChanges,  Brush.horizontalGradient(listOf(gold,       gold.copy(alpha = 0.6f)))),
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Quick Actions", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            actions.forEachIndexed { index, (label, icon, brush) ->
                var pressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue   = if (pressed) 0.88f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label         = "actionScale$index",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier
                        .weight(1f)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pressed = true
                            when (label) {
                                "Send"     -> navController.navigate(Screen.Payments.route)
                                "Goals"    -> navController.navigate(Screen.Goals.route)
                            }
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(brush),
                    ) {
                        Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, fontFamily = DmSansFontFamily, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ── AI insight card ───────────────────────────────────────────────────────────
@Composable
private fun AiInsightCard(onClick: () -> Unit) {
    val gold = MaterialTheme.akibaColors.gold

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(AkibaShapes.card)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.card)
            // Left accent border
            .drawBehind {
                drawRoundRect(
                    color        = gold,
                    size         = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Gold AI avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(gold, gold.copy(alpha = 0.6f)))),
            ) {
                Text("AI", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Akiba AI", fontFamily = DmSansFontFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(
                    text       = "You've spent 68% of your food budget. Consider cooking at home this weekend.",
                    fontFamily = DmSansFontFamily,
                    fontSize   = 14.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

// ── Budget overview ───────────────────────────────────────────────────────────
@Composable
private fun BudgetOverviewSection(
    overview : BudgetOverviewDto?,
    isLoading: Boolean,
    onSeeAll : () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("Budgets", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onSeeAll) {
                Text("See all →", color = primary, fontFamily = DmSansFontFamily, fontSize = 13.sp)
            }
        }

        if (isLoading) {
            repeat(3) { ShimmerCard(height = 72.dp) }
        } else {
            val budgets = overview?.budgets?.take(3) ?: emptyList()
            if (budgets.isEmpty()) {
                EmptyBudgetState()
            } else {
                budgets.forEach { budget ->
                    BudgetItem(budget)
                }
            }
        }
    }
}

@Composable
private fun BudgetItem(budget: BudgetDto) {
    val primary    = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val isOverLimit = budget.percentage >= 90.0

    val progressColor by animateColorAsState(
        targetValue   = if (isOverLimit) errorColor else primary,
        animationSpec = tween(500),
        label         = "budgetColor",
    )
    val progress by animateFloatAsState(
        targetValue   = (budget.percentage / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "budgetProgress",
    )

    GlassCard(elevation = GlassElevation.Raised) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(progressColor))
                    Spacer(Modifier.width(8.dp))
                    Text(budget.category, fontFamily = DmSansFontFamily,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text("Ksh ${"%,.0f".format(budget.spent)} / ${"%,.0f".format(budget.limit)}",
                    fontFamily = JetBrainsMonoFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            LinearProgressIndicator(
                progress     = { progress },
                modifier     = Modifier.fillMaxWidth().height(4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                color        = progressColor,
                trackColor   = MaterialTheme.akibaColors.glassBorder,
            )
        }
    }
}

// ── Savings goals ─────────────────────────────────────────────────────────────
@Composable
private fun SavingsGoalsSection(
    goals    : List<SavingsGoalDto>,
    isLoading: Boolean,
    onSeeAll : () -> Unit,
) {
    val accent = MaterialTheme.akibaColors.accentGreen
    val gold   = MaterialTheme.akibaColors.gold

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("Goals", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onSeeAll) {
                Text("See all →", color = accent, fontFamily = DmSansFontFamily, fontSize = 13.sp)
            }
        }

        if (isLoading) {
            repeat(2) { ShimmerCard(height = 100.dp) }
        } else {
            goals.take(2).forEach { goal ->
                GoalItem(goal, gold)
            }
        }
    }
}

@Composable
private fun GoalItem(goal: SavingsGoalDto, gold: androidx.compose.ui.graphics.Color) {
    val progress by animateFloatAsState(
        targetValue   = (goal.percentage / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label         = "goalProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AkibaShapes.card)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.card)
            .drawBehind {
                drawRoundRect(
                    color        = gold,
                    size         = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(goal.name, fontFamily = SoraFontFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                AkibaBadge("${goal.percentage.toInt()}%", BadgeVariant.Warning)
            }
            Text(
                "Ksh ${"%,.0f".format(goal.savedAmount)} / Ksh ${"%,.0f".format(goal.targetAmount)}",
                fontFamily = DmSansFontFamily, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(6.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                color      = gold,
                trackColor = MaterialTheme.akibaColors.glassBorder,
            )
            goal.daysRemaining?.let { days ->
                Box(
                    modifier = Modifier
                        .clip(AkibaShapes.chip)
                        .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.chip)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("$days days remaining", fontFamily = DmSansFontFamily, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ── Recent transactions ───────────────────────────────────────────────────────
@Composable
private fun RecentTransactionsSection(
    isLoading: Boolean,
    onSeeAll : () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("Recent", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onSeeAll) {
                Text("See all →", color = primary, fontFamily = DmSansFontFamily, fontSize = 13.sp)
            }
        }

        if (isLoading) {
            repeat(3) { ShimmerCard(height = 64.dp) }
        } else {
            // Placeholder rows until backend is connected in Phase 4 checklist
            EmptyTransactionState()
        }
    }
}

// ── Empty states ──────────────────────────────────────────────────────────────
@Composable
private fun EmptyBudgetState() {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxWidth().padding(24.dp),
    ) {
        Text("No budgets yet — add one to track spending",
            fontFamily = DmSansFontFamily, fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun EmptyTransactionState() {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxWidth().padding(24.dp),
    ) {
        Text("No transactions yet — import your M-Pesa statement to get started",
            fontFamily = DmSansFontFamily, fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}
