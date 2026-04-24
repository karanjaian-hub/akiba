package com.akiba.app.ui.screens.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.akiba.app.data.remote.api.TransactionApiService
import com.akiba.app.data.remote.dto.TransactionDto
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.components.skeleton.ShimmerCard
import com.akiba.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val api: TransactionApiService,
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<TransactionDto>>(emptyList())
    val transactions: StateFlow<List<TransactionDto>> = _transactions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow("All")
    val activeFilter: StateFlow<String> = _activeFilter.asStateFlow()

    init { loadTransactions() }

    fun loadTransactions(category: String? = null, type: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                api.getTransactions(
                    category = category,
                    type     = type,
                )
            }.getOrNull()?.body()?.let {
                _transactions.value = it
            }
            _isLoading.value = false
        }
    }

    fun setSearch(query: String) { _searchQuery.value = query }
    fun setFilter(filter: String) {
        _activeFilter.value = filter
        when (filter) {
            "Income"   -> loadTransactions(type = "credit")
            "Expenses" -> loadTransactions(type = "debit")
            "All"      -> loadTransactions()
            else       -> loadTransactions(category = filter)
        }
    }

    fun updateCategory(id: String, category: String) {
        viewModelScope.launch {
            runCatching { api.updateCategory(id, category) }
                .getOrNull()?.body()?.let { updated ->
                    _transactions.update { list ->
                        list.map { if (it.id == updated.id) updated else it }
                    }
                }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionListScreen(
    navController: NavHostController,
    viewModel    : TransactionViewModel = hiltViewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery  by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()

    val primary  = MaterialTheme.colorScheme.primary
    val filters  = listOf("All","Income","Expenses","Food","Transport","Bills","Entertainment","Health")

    // Filter transactions by search query client-side
    val filtered = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            it.merchant.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    // Group by date label
    val grouped = remember(filtered) {
        filtered.groupBy { it.date.take(10) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("History", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: filter sheet */ }) {
                        Icon(Icons.Rounded.FilterList, "Filter",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Search bar ────────────────────────────────────────────────
            var searchFocused by remember { mutableStateOf(false) }
            val searchHeight by animateDpAsState(
                targetValue   = if (searchFocused) 56.dp else 48.dp,
                label         = "searchHeight",
            )
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(searchHeight)) {
                AkibaTextField(
                    value         = searchQuery,
                    onValueChange = { viewModel.setSearch(it) },
                    placeholder   = "Search transactions...",
                    leadingIcon   = Icons.Rounded.Search,
                    modifier      = Modifier.fillMaxSize(),
                )
            }

            // ── Filter chips ──────────────────────────────────────────────
            LazyRow(
                modifier            = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters) { filter ->
                    val isActive = activeFilter == filter
                    val bgColor by animateColorAsState(
                        targetValue = if (isActive) primary else Color.Transparent,
                        label       = "filterBg$filter",
                    )
                    val scale by animateFloatAsState(
                        targetValue   = if (isActive) 1.05f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label         = "filterScale$filter",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .clip(AkibaShapes.chip)
                            .background(bgColor)
                            .border(
                                1.dp,
                                if (isActive) Color.Transparent
                                else MaterialTheme.akibaColors.glassBorder,
                                AkibaShapes.chip,
                            )
                            .clickable { viewModel.setFilter(filter) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(filter, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                            color = if (isActive) Color.White
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Transaction list ──────────────────────────────────────────
            if (isLoading) {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(6) { ShimmerCard(height = 72.dp) }
                }
            } else if (filtered.isEmpty()) {
                EmptyHistoryState()
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(bottom = 80.dp),
                ) {
                    grouped.forEach { (date, txns) ->
                        stickyHeader {
                            DateHeader(date)
                        }
                        itemsIndexed(txns) { index, tx ->
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(index * 40L)
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter   = fadeIn(tween(300)) + slideInVertically { 20 },
                            ) {
                                TransactionItem(
                                    transaction = tx,
                                    onSwipe     = { /* TODO: category edit sheet */ },
                                    onClick     = {
                                        navController.navigate("transaction_detail/${tx.id}")
                                    },
                                )
                            }
                            if (index < txns.size - 1) {
                                Divider(
                                    modifier  = Modifier.padding(horizontal = 16.dp),
                                    color     = MaterialTheme.akibaColors.glassBorder,
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Date header ───────────────────────────────────────────────────────────────
@Composable
private fun DateHeader(date: String) {
    val label = when (date) {
        java.time.LocalDate.now().toString()                -> "TODAY"
        java.time.LocalDate.now().minusDays(1).toString()  -> "YESTERDAY"
        else -> date.uppercase()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text          = label,
            fontFamily    = DmSansFontFamily,
            fontSize      = 11.sp,
            letterSpacing = 0.08.em,
            color         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight    = FontWeight.SemiBold,
        )
    }
}

// ── Transaction item ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionItem(
    transaction: TransactionDto,
    onSwipe    : () -> Unit,
    onClick    : () -> Unit,
) {
    val primary  = MaterialTheme.colorScheme.primary
    val accent   = MaterialTheme.akibaColors.accentGreen
    val error    = MaterialTheme.colorScheme.error
    val isCredit = transaction.type == "credit"

    // Category color and emoji
    val (categoryColor, emoji) = categoryStyle(transaction.category, primary, accent)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onSwipe(); true }
            else false
        }
    )

    SwipeToDismissBox(
        state            = dismissState,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier         = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.secondary)
                        )
                    )
                    .padding(end = 20.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Edit, null, tint = Color.White,
                        modifier = Modifier.size(20.dp))
                    Text("Change\nCategory", fontFamily = DmSansFontFamily,
                        fontSize = 10.sp, color = Color.White)
                }
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Category circle
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
            ) {
                Text(emoji, fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.merchant, fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Row(
                    verticalAlignment  = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AkibaBadge(transaction.category,
                        variant = BadgeVariant.Info, size = BadgeSize.Sm)
                    Text(transaction.date, fontFamily = DmSansFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "${if (isCredit) "+" else "-"}Ksh ${"%,.0f".format(transaction.amount)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = if (isCredit) accent else error,
                )
                if (transaction.isAnomalous) {
                    Icon(Icons.Rounded.Warning, null,
                        tint     = MaterialTheme.akibaColors.gold,
                        modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyHistoryState() {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Receipt, null,
                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp))
            Text("No transactions found", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text("Import your M-Pesa or bank statement to get started.",
                fontFamily = DmSansFontFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

// ── Category style helper ─────────────────────────────────────────────────────
private fun categoryStyle(category: String, primary: Color, accent: Color): Pair<Color, String> =
    when (category.lowercase()) {
        "food"          -> accent    to "🍽"
        "transport"     -> primary   to "🚗"
        "rent"          -> primary   to "🏠"
        "bills"         -> Color(0xFFF59E0B) to "⚡"
        "entertainment" -> Color(0xFF8B5CF6) to "🎬"
        "health"        -> Color(0xFF10B981) to "💊"
        "savings"       -> Color(0xFFF59E0B) to "💰"
        "income"        -> accent    to "💵"
        else            -> primary   to "💳"
    }
