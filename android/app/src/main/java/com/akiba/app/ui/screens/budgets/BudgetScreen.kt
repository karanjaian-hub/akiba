package com.akiba.app.ui.screens.budgets

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.akiba.app.data.remote.api.BudgetApiService
import com.akiba.app.data.remote.dto.BudgetDto
import com.akiba.app.data.remote.dto.BudgetOverviewDto
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val api: BudgetApiService,
) : ViewModel() {

    private val _overview  = MutableStateFlow<BudgetOverviewDto?>(null)
    val overview: StateFlow<BudgetOverviewDto?> = _overview.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { loadBudgets() }

    fun loadBudgets() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { api.getBudgetOverview() }
                .getOrNull()?.body()?.let { _overview.value = it }
            _isLoading.value = false
        }
    }

    fun createBudget(category: String, limit: Double) {
        viewModelScope.launch {
            runCatching {
                api.createBudget(mapOf("category" to category, "limit" to limit))
            }.getOrNull()?.body()?.let { loadBudgets() }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    navController: NavHostController,
    viewModel    : BudgetViewModel = hiltViewModel(),
) {
    val overview  by viewModel.overview.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val primary   = MaterialTheme.colorScheme.primary
    val gold      = MaterialTheme.akibaColors.gold
    val accent    = MaterialTheme.akibaColors.accentGreen

    var showAddSheet    by remember { mutableStateOf(false) }
    var expandedBudget  by remember { mutableStateOf<String?>(null) }

    // Add budget bottom sheet
    if (showAddSheet) {
        AddBudgetSheet(
            onDismiss = { showAddSheet = false },
            onSave    = { category, limit ->
                viewModel.createBudget(category, limit)
                showAddSheet = false
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Budgets", fontFamily = SoraFontFamily,
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
                    .background(Brush.radialGradient(listOf(primary, primary.copy(alpha = 0.7f))))
                    .clickable { showAddSheet = true },
            ) {
                Icon(Icons.Rounded.Add, "Add budget",
                    tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Donut chart ───────────────────────────────────────────────
            item {
                DonutChartCard(
                    overview  = overview,
                    isLoading = isLoading,
                )
            }

            // ── Budget cards ──────────────────────────────────────────────
            if (isLoading) {
                items(4) {
                    com.akiba.app.ui.components.skeleton.ShimmerCard(height = 80.dp)
                }
            } else {
                val budgets = overview?.budgets ?: emptyList()
                if (budgets.isEmpty()) {
                    item { EmptyBudgetState(onAdd = { showAddSheet = true }) }
                } else {
                    itemsIndexed(budgets) { index, budget ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(index * 100L)
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter   = fadeIn(tween(400)) + slideInVertically { 20 },
                        ) {
                            BudgetCard(
                                budget    = budget,
                                isExpanded= expandedBudget == budget.id,
                                onToggle  = {
                                    expandedBudget =
                                        if (expandedBudget == budget.id) null else budget.id
                                },
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

// ── Donut chart card ──────────────────────────────────────────────────────────
@Composable
private fun DonutChartCard(
    overview : BudgetOverviewDto?,
    isLoading: Boolean,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val gold      = MaterialTheme.akibaColors.gold

    val segmentColors = listOf(primary, secondary, accent, gold,
        primary.copy(alpha = 0.6f), secondary.copy(alpha = 0.6f))

    GlassCard(elevation = GlassElevation.Hero) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Animated donut
            val animatedAngles = overview?.budgets?.mapIndexed { index, budget ->
                val target = if ((overview.totalLimit) > 0)
                    (budget.limit / overview.totalLimit * 360f).toFloat() else 0f
                animateFloatAsState(
                    targetValue   = target,
                    animationSpec = tween(1000 + index * 100, easing = FastOutSlowInEasing),
                    label         = "donut$index",
                ).value
            } ?: emptyList()

            val totalSpent by animateIntAsState(
                targetValue   = overview?.totalSpent?.toInt() ?: 0,
                animationSpec = tween(1200, easing = FastOutSlowInEasing),
                label         = "totalSpent",
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(180.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke     = 28.dp.toPx()
                    val inset      = stroke / 2
                    val arcSize    = Size(size.width - stroke, size.height - stroke)
                    val topLeft    = Offset(inset, inset)
                    var startAngle = -90f

                    if (animatedAngles.isEmpty()) {
                        // Empty ring
                        drawArc(
                            color      = Color.White.copy(alpha = 0.1f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter  = false,
                            topLeft    = topLeft,
                            size       = arcSize,
                            style      = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    } else {
                        animatedAngles.forEachIndexed { index, sweep ->
                            val color = segmentColors.getOrElse(index) { primary }
                            drawArc(
                                color      = color,
                                startAngle = startAngle,
                                sweepAngle = sweep - 4f, // 4dp gap between segments
                                useCenter  = false,
                                topLeft    = topLeft,
                                size       = arcSize,
                                style      = Stroke(stroke, cap = StrokeCap.Round),
                            )
                            startAngle += sweep
                        }
                    }
                }

                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Spent", fontFamily = DmSansFontFamily, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("Ksh ${"%,d".format(totalSpent)}",
                        fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Legend chips
            overview?.budgets?.take(6)?.forEachIndexed { index, budget ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(segmentColors.getOrElse(index) { primary }),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(budget.category, fontFamily = DmSansFontFamily,
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("${budget.percentage.toInt()}%",
                        fontFamily = JetBrainsMonoFamily, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ── Budget card ───────────────────────────────────────────────────────────────
@Composable
private fun BudgetCard(
    budget    : BudgetDto,
    isExpanded: Boolean,
    onToggle  : () -> Unit,
) {
    val primary    = MaterialTheme.colorScheme.primary
    val error      = MaterialTheme.colorScheme.error
    val isOverLimit = budget.percentage >= 90.0

    val progressColor by animateColorAsState(
        targetValue   = if (isOverLimit) error else primary,
        animationSpec = tween(500),
        label         = "progressColor",
    )
    val progress by animateFloatAsState(
        targetValue   = (budget.percentage / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "progress",
    )

    // Pulse glow on over-limit budgets
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue  = 0.1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label         = "pulseAlpha",
    )

    GlassCard(
        elevation = GlassElevation.Raised,
        onClick   = onToggle,
        modifier  = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(progressColor)
                            .then(
                                if (isOverLimit) Modifier.drawBehind {
                                    drawCircle(color = error.copy(alpha = pulseAlpha),
                                        radius = size.minDimension)
                                } else Modifier
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(budget.category, fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ksh ${"%,.0f".format(budget.spent)} / ${"%,.0f".format(budget.limit)}",
                        fontFamily = JetBrainsMonoFamily, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(8.dp))
                    com.akiba.app.ui.components.common.AkibaBadge(
                        text    = "${budget.percentage.toInt()}%",
                        variant = when {
                            budget.percentage >= 90 -> BadgeVariant.Danger
                            budget.percentage >= 70 -> BadgeVariant.Warning
                            else                    -> BadgeVariant.Success
                        },
                    )
                }
            }

            // Progress bar with gradient fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.akibaColors.glassBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(progressColor, progressColor.copy(alpha = 0.6f))
                            )
                        ),
                )
            }

            // Expanded content — chart placeholder
            if (isExpanded) {
                Divider(color = MaterialTheme.akibaColors.glassBorder)
                Text("Weekly breakdown coming soon",
                    fontFamily = DmSansFontFamily, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { }) {
                        Text("Edit Limit", color = primary,
                            fontFamily = DmSansFontFamily, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Add budget sheet ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddBudgetSheet(
    onDismiss: () -> Unit,
    onSave   : (String, Double) -> Unit,
) {
    val categories = listOf("Food","Transport","Rent","Bills","Entertainment","Health","Other")
    var selected   by remember { mutableStateOf("") }
    var limitText  by remember { mutableStateOf("") }
    val primary    = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = AkibaShapes.bottomSheet,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add Budget", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface)

            // Category grid
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    val isSelected = selected == cat
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) primary else Color.Transparent,
                        label       = "catBg$cat",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .clip(AkibaShapes.chip)
                            .background(bgColor)
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent
                                else MaterialTheme.akibaColors.glassBorder,
                                AkibaShapes.chip,
                            )
                            .clickable { selected = cat }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(cat, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                            color = if (isSelected) Color.White
                                    else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            AkibaTextField(
                value         = limitText,
                onValueChange = { limitText = it },
                label         = "Monthly limit (Ksh)",
                leadingIcon   = Icons.Rounded.AttachMoney,
                keyboardType  = androidx.compose.ui.text.input.KeyboardType.Decimal,
            )

            AkibaButton(
                text     = "Save Budget",
                enabled  = selected.isNotBlank() && limitText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick  = { onSave(selected, limitText.toDoubleOrNull() ?: 0.0) },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyBudgetState(onAdd: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.PieChart, null,
                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp))
            Text("No budgets yet", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            AkibaButton(
                text    = "Add your first budget",
                onClick = onAdd,
                variant = AkibaButtonVariant.Outline,
            )
        }
    }
}
