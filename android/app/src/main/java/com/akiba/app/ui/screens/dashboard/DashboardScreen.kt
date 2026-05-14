package com.akiba.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.data.local.PrefKeys
import com.akiba.app.data.local.dataStore
import com.akiba.app.data.remote.dto.*
import com.akiba.app.navigation.AkibaBottomBar
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.components.skeleton.*
import com.akiba.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    viewModel    : DashboardViewModel = hiltViewModel(),
) {
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val balanceVisible   by viewModel.balanceVisible.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()
    val context          = LocalContext.current

    val userName by remember {
        context.dataStore.data.map { prefs ->
            val userJson = prefs[PrefKeys.USER_JSON]
            if (userJson != null) {
                try {
                    val obj = com.google.gson.JsonParser.parseString(userJson).asJsonObject
                    // Backend returns fullName (camelCase)
                    obj.get("fullName")?.asString?.takeIf { it.isNotBlank() }
                        ?: obj.get("email")?.asString?.substringBefore("@")
                        ?: "User"
                } catch (e: Exception) { "User" }
            } else "User"
        }
    }.collectAsState(initial = "User")

    val sectionCount   = 5
    val sectionVisible = remember { List(sectionCount) { mutableStateOf(false) } }

    // Show sections immediately with stagger — shimmer handles loading state
    LaunchedEffect(Unit) {
        sectionVisible.forEachIndexed { i, state ->
            delay(i * 80L)
            state.value = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar      = { AkibaBottomBar(navController) },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh    = { viewModel.refresh() },
            state        = pullRefreshState,
            modifier     = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            LazyColumn(
                modifier            = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding      = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {

                item {
                    AnimatedVisibility(visible = sectionVisible[0].value, enter = fadeIn(tween(500))) {
                        HeroSection(
                            userName        = userName,
                            summary         = uiState.summary,
                            balanceVisible  = balanceVisible,
                            unreadCount     = uiState.unreadCount,
                            isLoading       = uiState.isLoading,
                            onToggleBalance = { viewModel.toggleBalance() },
                            onBellClick     = { navController.navigate(Screen.Notifications.route) },
                        )
                    }
                }

                item {
                    AnimatedVisibility(visible = sectionVisible[1].value, enter = fadeIn(tween(400)) + slideInVertically { 30 }) {
                        QuickActionsRow(navController)
                    }
                }

                item {
                    AnimatedVisibility(visible = sectionVisible[2].value, enter = fadeIn(tween(400)) + slideInHorizontally { it }) {
                        AiNudgeCard(onClick = { navController.navigate(Screen.AiChat.route) })
                    }
                }

                item {
                    AnimatedVisibility(visible = sectionVisible[3].value, enter = fadeIn(tween(400)) + slideInVertically { 30 }) {
                        BudgetSection(
                            overview  = uiState.budgetOverview,
                            isLoading = uiState.isLoading,
                            onSeeAll  = { navController.navigate(Screen.Budgets.route) },
                        )
                    }
                }

                item {
                    AnimatedVisibility(visible = sectionVisible[4].value, enter = fadeIn(tween(400)) + slideInVertically { 30 }) {
                        GoalsSection(
                            goals     = uiState.goals,
                            isLoading = uiState.isLoading,
                            onSeeAll  = { navController.navigate(Screen.Goals.route) },
                        )
                    }
                }

                item {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(
                            "Every shilling has a story.",
                            fontFamily = DmSansFontFamily,
                            fontStyle  = FontStyle.Italic,
                            fontSize   = 12.sp,
                            color      = MaterialTheme.akibaColors.gold.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    userName       : String,
    summary        : TransactionSummaryDto?,
    balanceVisible : Boolean,
    unreadCount    : Int,
    isLoading      : Boolean,
    onToggleBalance: () -> Unit,
    onBellClick    : () -> Unit,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val gold      = MaterialTheme.akibaColors.gold
    val firstName = userName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "there"
    val greeting  = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11  -> "Good morning"
        in 12..17 -> "Good afternoon"
        else      -> "Good evening"
    }

    val animatedBalance  by animateIntAsState(summary?.totalBalance?.toInt()  ?: 0, tween(1400, easing = FastOutSlowInEasing), label = "bal")
    val animatedIncome   by animateIntAsState(summary?.totalIncome?.toInt()   ?: 0, tween(1200, easing = FastOutSlowInEasing), label = "inc")
    val animatedExpenses by animateIntAsState(summary?.totalExpenses?.toInt() ?: 0, tween(1200, 150, FastOutSlowInEasing),     label = "exp")
    val animatedSaved    by animateIntAsState(summary?.totalSaved?.toInt()    ?: 0, tween(1200, 300, FastOutSlowInEasing),     label = "sav")

    Box(modifier = Modifier.fillMaxWidth().background(
        Brush.linearGradient(
            colorStops = arrayOf(0f to primary, 0.55f to primary.copy(0.85f), 1f to secondary.copy(0.9f)),
            start = Offset(0f, 0f), end = Offset(1200f, 700f),
        )
    )) {
        // Decorative circles
        Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
            drawCircle(color = androidx.compose.ui.graphics.Color.White.copy(0.06f), radius = 170.dp.toPx(), center = Offset(size.width * 0.88f, -30f))
            drawCircle(color = secondary.copy(0.12f), radius = 100.dp.toPx(), center = Offset(-20f, size.height * 0.85f))
            drawCircle(color = gold.copy(0.08f), radius = 60.dp.toPx(), center = Offset(size.width * 0.5f, size.height * 1.1f))
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            // Top row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(greeting, fontFamily = DmSansFontFamily, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White.copy(0.6f))
                    Text(firstName, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = androidx.compose.ui.graphics.Color.White)
                }
                Box {
                    IconButton(
                        onClick  = onBellClick,
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White.copy(0.12f)),
                    ) {
                        Icon(Icons.Rounded.Notifications, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                    }
                    if (unreadCount > 0) {
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(gold)) {
                            Text("$unreadCount", fontSize = 8.sp, fontFamily = DmSansFontFamily,
                                color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // Balance
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total Balance", fontFamily = DmSansFontFamily, fontSize = 11.sp,
                        color = androidx.compose.ui.graphics.Color.White.copy(0.55f), letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(4.dp))
                    if (isLoading) {
                        Box(modifier = Modifier.width(160.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.White.copy(0.15f)))
                    } else {
                        AnimatedContent(targetState = balanceVisible, label = "balContent") { visible ->
                            Text(
                                text       = if (visible) "Ksh ${"%,d".format(animatedBalance)}" else "Ksh ••••••",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 28.sp,
                                color      = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
                Box(contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(0.12f))
                        .clickable(onClick = onToggleBalance)) {
                    Icon(
                        imageVector = if (balanceVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White.copy(0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Modifier.weight(1f), "↑", "Income",   "Ksh ${"%,d".format(animatedIncome)}",   accent,                                              isLoading)
                StatPill(Modifier.weight(1f), "↓", "Spent",    "Ksh ${"%,d".format(animatedExpenses)}", MaterialTheme.colorScheme.error.copy(alpha = 0.85f), isLoading)
                StatPill(Modifier.weight(1f), "★", "Saved",    "Ksh ${"%,d".format(animatedSaved)}",    gold,                                                isLoading)
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun StatPill(modifier: Modifier, icon: String, label: String, value: String, color: androidx.compose.ui.graphics.Color, isLoading: Boolean) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp))
        .background(androidx.compose.ui.graphics.Color.White.copy(0.10f))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 9.sp, color = color)
            Spacer(Modifier.width(4.dp))
            Text(label, fontFamily = DmSansFontFamily, fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.White.copy(0.55f))
        }
        Spacer(Modifier.height(3.dp))
        if (isLoading) {
            Box(modifier = Modifier.width(56.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color.White.copy(0.15f)))
        } else {
            Text(value, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun QuickActionsRow(navController: NavHostController) {
    val haptic    = LocalHapticFeedback.current
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val gold      = MaterialTheme.akibaColors.gold

    val actions = listOf(
        Triple("Send",    Icons.Rounded.Send,         primary),
        Triple("History", Icons.Rounded.History,      secondary),
        Triple("Budgets", Icons.Rounded.PieChart,     accent),
        Triple("Goals",   Icons.Rounded.TrackChanges, gold),
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            actions.forEachIndexed { index, (label, icon, color) ->
                var pressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "qs$index")

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).graphicsLayer(scaleX = scale, scaleY = scale).clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        pressed = true
                        when (label) {
                            "Send"    -> navController.navigate(Screen.Payments.route)
                            "History" -> navController.navigate(Screen.History.route)
                            "Budgets" -> navController.navigate(Screen.Budgets.route)
                            "Goals"   -> navController.navigate(Screen.Goals.route)
                        }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(color.copy(0.18f), color.copy(0.06f))))
                            .border(1.dp, color.copy(0.3f), CircleShape)) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, fontFamily = DmSansFontFamily, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                }
            }
        }
    }
}

@Composable
private fun AiNudgeCard(onClick: () -> Unit) {
    val gold    = MaterialTheme.akibaColors.gold
    val primary = MaterialTheme.colorScheme.primary
    val pulse by rememberInfiniteTransition(label = "aiPulse").animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "p")
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "aiS")

    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clip(RoundedCornerShape(18.dp))
        .background(Brush.horizontalGradient(listOf(gold.copy(0.10f), primary.copy(0.07f))))
        .border(1.dp, Brush.horizontalGradient(listOf(gold.copy(0.35f), primary.copy(0.18f))), RoundedCornerShape(18.dp))
        .clickable { pressed = true; onClick() }
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(38.dp).graphicsLayer(scaleX = pulse, scaleY = pulse)
                    .clip(CircleShape).background(Brush.radialGradient(listOf(gold, gold.copy(0.5f))))) {
                Text("✦", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Akiba AI", fontFamily = SoraFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = gold)
                Text("Ask me anything about your finances →", fontFamily = DmSansFontFamily, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}

@Composable
private fun BudgetSection(overview: BudgetOverviewDto?, isLoading: Boolean, onSeeAll: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val error   = MaterialTheme.colorScheme.error

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Budgets", primary, "See all", onSeeAll)

        if (isLoading) {
            repeat(2) { ShimmerCard(height = 56.dp) }
        } else {
            val budgets = overview?.budgets?.take(3) ?: emptyList()
            if (budgets.isEmpty()) {
                EmptyStateRow("No budgets set", "Tap See all to add one")
            } else {
                budgets.forEach { budget ->
                    val isOver = budget.percentage >= 90.0
                    val progressColor by animateColorAsState(if (isOver) error else primary, tween(500), label = "pc")
                    val progress by animateFloatAsState((budget.percentage / 100.0).toFloat().coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "pp")

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(progressColor))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(budget.category, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${budget.percentage.toInt()}%", fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp, color = progressColor, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(5.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(0.07f))) {
                                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                                    .background(Brush.horizontalGradient(listOf(progressColor, progressColor.copy(0.55f)))))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsSection(goals: List<SavingsGoalDto>, isLoading: Boolean, onSeeAll: () -> Unit) {
    val accent = MaterialTheme.akibaColors.accentGreen
    val gold   = MaterialTheme.akibaColors.gold

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Goals", accent, "See all", onSeeAll)

        if (isLoading) {
            repeat(2) { ShimmerCard(height = 70.dp) }
        } else if (goals.isEmpty()) {
            EmptyStateRow("No goals yet", "Tap See all to create one")
        } else {
            goals.take(2).forEach { goal ->
                val progress by animateFloatAsState((goal.percentage / 100.0).toFloat().coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "gp${goal.id}")

                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(0.6f))
                    .border(1.dp, MaterialTheme.akibaColors.glassBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(goal.emoji, fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(goal.name, fontFamily = SoraFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Ksh ${"%,.0f".format(goal.savedAmount)} of ${"%,.0f".format(goal.targetAmount)}",
                                        fontFamily = DmSansFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                }
                            }
                            Box(contentAlignment = Alignment.Center,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(gold.copy(0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("${goal.percentage.toInt()}%", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = gold)
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(0.07f))) {
                            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(listOf(gold, accent))))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, accentColor: androidx.compose.ui.graphics.Color, actionLabel: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(4.dp, 18.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
            Spacer(Modifier.width(8.dp))
            Text(title, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        TextButton(onClick = onAction) {
            Text(actionLabel, color = accentColor, fontFamily = DmSansFontFamily, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyStateRow(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(0.04f)).padding(16.dp)) {
        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.2f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = DmSansFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Text(subtitle, fontFamily = DmSansFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    }
}
