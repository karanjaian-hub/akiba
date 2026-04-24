package com.akiba.app.ui.screens.savings

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.akiba.app.data.remote.api.SavingsApiService
import com.akiba.app.data.remote.dto.SavingsGoalDto
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val api: SavingsApiService,
) : ViewModel() {

    private val _goals     = MutableStateFlow<List<SavingsGoalDto>>(emptyList())
    val goals: StateFlow<List<SavingsGoalDto>> = _goals.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { loadGoals() }

    fun loadGoals() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { api.getGoals() }
                .getOrNull()?.body()?.let { _goals.value = it }
            _isLoading.value = false
        }
    }

    fun createGoal(name: String, emoji: String, target: Double, deadline: String?) {
        viewModelScope.launch {
            runCatching {
                api.createGoal(buildMap {
                    put("name",   name)
                    put("emoji",  emoji)
                    put("target_amount", target)
                    if (deadline != null) put("deadline", deadline)
                })
            }.getOrNull()?.body()?.let { loadGoals() }
        }
    }

    fun contribute(goalId: String, amount: Double) {
        viewModelScope.launch {
            runCatching {
                api.contribute(goalId, mapOf("amount" to amount))
            }.getOrNull()?.body()?.let { loadGoals() }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteGoal(id) }
            loadGoals()
        }
    }
}

// ── Goals screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    navController: NavHostController,
    viewModel    : SavingsViewModel = hiltViewModel(),
) {
    val goals     by viewModel.goals.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val accent    = MaterialTheme.akibaColors.accentGreen
    val gold      = MaterialTheme.akibaColors.gold

    var showAddSheet    by remember { mutableStateOf(false) }
    var achievedGoal    by remember { mutableStateOf<SavingsGoalDto?>(null) }

    // Detect newly completed goals
    LaunchedEffect(goals) {
        goals.firstOrNull { it.status == "completed" && it.percentage >= 100.0 }
            ?.let { achievedGoal = it }
    }

    // Goal achieved overlay
    achievedGoal?.let { goal ->
        GoalAchievedOverlay(goal = goal, onDismiss = { achievedGoal = null })
    }

    if (showAddSheet) {
        AddGoalSheet(
            onDismiss = { showAddSheet = false },
            onSave    = { name, emoji, target, deadline ->
                viewModel.createGoal(name, emoji, target, deadline)
                showAddSheet = false
            },
        )
    }

    val totalSaved by animateIntAsState(
        targetValue   = goals.sumOf { it.savedAmount }.toInt(),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label         = "totalSaved",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Goals", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent, accent.copy(alpha = 0.6f))))
                    .clickable { showAddSheet = true },
            ) {
                Icon(Icons.Rounded.Add, "Add goal",
                    tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Summary card ──────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AkibaShapes.card)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.card)
                        .drawBehind {
                            drawRoundRect(
                                color        = accent,
                                size         = Size(6.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                        }
                        .padding(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total Saved", fontFamily = DmSansFontFamily, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("Ksh ${"%,d".format(totalSaved)}",
                            fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold,
                            fontSize = 36.sp, color = accent)
                        Text("across ${goals.size} goal${if (goals.size != 1) "s" else ""}",
                            fontFamily = DmSansFontFamily, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

                        // Decorative gold coins
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        ) {
                            listOf(size.width * 0.7f, size.width * 0.8f, size.width * 0.9f)
                                .forEachIndexed { i, x ->
                                    drawCircle(
                                        color  = gold.copy(alpha = 0.4f - i * 0.1f),
                                        radius = 8.dp.toPx(),
                                        center = Offset(x, size.height / 2),
                                    )
                                }
                        }
                    }
                }
            }

            // ── Goal cards ────────────────────────────────────────────────
            if (isLoading) {
                items(3) { com.akiba.app.ui.components.skeleton.ShimmerCard(height = 140.dp) }
            } else if (goals.isEmpty()) {
                item { EmptyGoalsState(onAdd = { showAddSheet = true }) }
            } else {
                itemsIndexed(goals) { index, goal ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 80L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter   = fadeIn(tween(400)) + slideInVertically { 20 },
                    ) {
                        GoalCard(
                            goal     = goal,
                            onContribute = { navController.navigate("goal_detail/${goal.id}") },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

// ── Goal card ─────────────────────────────────────────────────────────────────
@Composable
private fun GoalCard(
    goal        : SavingsGoalDto,
    onContribute: () -> Unit,
) {
    val gold   = MaterialTheme.akibaColors.gold
    val accent = MaterialTheme.akibaColors.accentGreen

    val progress by animateFloatAsState(
        targetValue   = (goal.percentage / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label         = "goalProgress",
    )

    // Sparkle pulse at progress tip
    val sparkle by rememberInfiniteTransition(label = "sparkle").animateFloat(
        initialValue  = 1f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label         = "sparkleScale",
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
                    size         = Size(4.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .clickable(onClick = onContribute)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(goal.name, fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                AkibaBadge(
                    text    = goal.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                    variant = when (goal.status) {
                        "completed" -> BadgeVariant.Success
                        "behind"    -> BadgeVariant.Warning
                        else        -> BadgeVariant.Info
                    },
                )
            }

            // Amounts
            Text(
                "Ksh ${"%,.0f".format(goal.savedAmount)} / Ksh ${"%,.0f".format(goal.targetAmount)}",
                fontFamily = DmSansFontFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            // Gold progress bar with sparkle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                // Track
                Box(modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.akibaColors.glassBorder))
                // Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(listOf(gold, gold.copy(alpha = 0.7f)))
                        ),
                )
                // Sparkle dot at tip
                if (progress > 0.05f && progress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .wrapContentWidth(Alignment.End),
                    ) {
                        Box(
                            modifier = Modifier
                                .size((4 * sparkle).dp)
                                .clip(CircleShape)
                                .background(gold),
                        )
                    }
                }
            }

            // Bottom row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("${goal.percentage.toInt()}%",
                    fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = gold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goal.daysRemaining?.let { days ->
                        Box(
                            modifier = Modifier
                                .clip(AkibaShapes.chip)
                                .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.chip)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("$days days left", fontFamily = DmSansFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                    goal.weeklyTarget?.let { weekly ->
                        Text("Ksh ${"%,.0f".format(weekly)}/wk",
                            fontFamily = DmSansFontFamily, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

// ── Add goal sheet ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddGoalSheet(
    onDismiss: () -> Unit,
    onSave   : (String, String, Double, String?) -> Unit,
) {
    val primary  = MaterialTheme.colorScheme.primary
    var name     by remember { mutableStateOf("") }
    var target   by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf<String?>(null) }
    var emoji    by remember { mutableStateOf("💰") }

    val emojis = listOf("💻","🚗","🏠","✈️","💊","🎓","💍","👶","📱","🌍",
                         "🏋️","💰","🎁","🛒","🐕","🎵","🏦","🎒","💎","🛡")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = AkibaShapes.bottomSheet,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New Goal", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface)

            // Emoji picker
            Text("Pick an emoji", fontFamily = DmSansFontFamily, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns          = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                modifier         = Modifier.height(160.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(emojis.size) { index ->
                    val e = emojis[index]
                    val isSelected = emoji == e
                    val scale by animateFloatAsState(
                        targetValue   = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label         = "emojiScale$index",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(48.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { emoji = e },
                    ) {
                        Text(e, fontSize = 22.sp)
                    }
                }
            }

            AkibaTextField(
                value         = name,
                onValueChange = { name = it },
                label         = "Goal name",
                leadingIcon   = Icons.Rounded.Flag,
            )

            AkibaTextField(
                value         = target,
                onValueChange = { target = it },
                label         = "Target amount (Ksh)",
                leadingIcon   = Icons.Rounded.AttachMoney,
                keyboardType  = androidx.compose.ui.text.input.KeyboardType.Decimal,
            )

            AkibaButton(
                text     = "Create Goal",
                variant  = AkibaButtonVariant.Success,
                enabled  = name.isNotBlank() && target.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick  = { onSave(name, emoji, target.toDoubleOrNull() ?: 0.0, deadline) },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Goal achieved overlay ─────────────────────────────────────────────────────
@Composable
private fun GoalAchievedOverlay(
    goal     : SavingsGoalDto,
    onDismiss: () -> Unit,
) {
    val accent  = MaterialTheme.akibaColors.accentGreen
    val gold    = MaterialTheme.akibaColors.gold
    val primary = MaterialTheme.colorScheme.primary

    val checkScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        checkScale.animateTo(1f, spring(stiffness = 120f, dampingRatio = 0.55f))
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(onClick = onDismiss),
            )

            // Confetti canvas
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val colors = listOf(gold, accent, primary)
                repeat(30) { i ->
                    drawCircle(
                        color  = colors[i % colors.size].copy(alpha = 0.6f),
                        radius = (4 + i % 6).dp.toPx(),
                        center = Offset(
                            x = size.width * ((i * 37) % 100) / 100f,
                            y = size.height * ((i * 53) % 100) / 100f,
                        ),
                    )
                }
            }

            // Card
            GlassCard(elevation = GlassElevation.Hero) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier            = Modifier.padding(8.dp),
                ) {
                    Text(goal.emoji, fontSize = 64.sp)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(56.dp)
                            .graphicsLayer(scaleX = checkScale.value, scaleY = checkScale.value)
                            .clip(CircleShape)
                            .background(accent),
                    ) {
                        Icon(Icons.Rounded.Check, null,
                            tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    Text("Goal Achieved!", fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center)

                    Text(goal.name, fontFamily = DmSansFontFamily, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AkibaButton(
                            text    = "Share",
                            variant = AkibaButtonVariant.Outline,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        AkibaButton(
                            text    = "Continue",
                            variant = AkibaButtonVariant.Success,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyGoalsState(onAdd: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🎯", fontSize = 64.sp)
            Text("No goals yet", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text("Set a savings goal and watch your money grow.",
                fontFamily = DmSansFontFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                textAlign = TextAlign.Center)
            AkibaButton(
                text    = "Create your first goal",
                variant = AkibaButtonVariant.Success,
                onClick = onAdd,
            )
        }
    }
}
