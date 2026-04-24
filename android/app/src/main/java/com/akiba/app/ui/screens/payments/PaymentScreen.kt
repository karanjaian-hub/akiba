package com.akiba.app.ui.screens.payments

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.data.remote.dto.PaymentRequest
import com.akiba.app.data.remote.dto.RecipientDto
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    navController: NavHostController,
    viewModel    : PaymentViewModel = hiltViewModel(),
) {
    val recipients  by viewModel.recipients.collectAsStateWithLifecycle()
    val budgetCheck by viewModel.budgetCheck.collectAsStateWithLifecycle()
    val paymentState by viewModel.paymentState.collectAsStateWithLifecycle()

    val primary    = MaterialTheme.colorScheme.primary
    val secondary  = MaterialTheme.colorScheme.secondary
    val accent     = MaterialTheme.akibaColors.accentGreen
    val haptic     = LocalHapticFeedback.current

    // ── Form state ────────────────────────────────────────────────────────
    var activeTab    by remember { mutableIntStateOf(0) }  // 0=Phone 1=Till 2=Paybill
    var phone        by remember { mutableStateOf("") }
    var till         by remember { mutableStateOf("") }
    var paybill      by remember { mutableStateOf("") }
    var accountNo    by remember { mutableStateOf("") }
    var amount       by remember { mutableStateOf("") }
    var category     by remember { mutableStateOf("") }
    var showPinSheet by remember { mutableStateOf(false) }
    var pin          by remember { mutableStateOf("") }

    val categories = listOf("Food","Transport","Rent","Bills","Entertainment","Health","Other")

    // Debounced budget check
    LaunchedEffect(amount, category) {
        if (amount.isNotBlank() && category.isNotBlank()) {
            delay(500)
            viewModel.checkBudget(category, amount.toDoubleOrNull() ?: 0.0)
        }
    }

    // Navigate on payment initiated
    LaunchedEffect(paymentState) {
        when (val s = paymentState) {
            is PaymentUiState.Pending   ->
                navController.navigate("payment_pending/${s.paymentId}")
            is PaymentUiState.Completed ->
                navController.navigate("payment_success/${s.status.paymentId}")
            is PaymentUiState.Failed    ->
                navController.navigate("payment_failed/${s.reason}")
            else -> Unit
        }
    }

    val recipient = when (activeTab) {
        0 -> phone; 1 -> till; else -> paybill
    }
    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    val canProceed   = recipient.isNotBlank() && amountDouble > 0 && category.isNotBlank()

    // ── PIN bottom sheet ──────────────────────────────────────────────────
    if (showPinSheet) {
        PinBottomSheet(
            recipient = recipient,
            amount    = amountDouble,
            pin       = pin,
            onPinChange = { newPin ->
                pin = newPin
                if (newPin.length == 4) {
                    showPinSheet = false
                    val request = PaymentRequest(
                        recipientType = when (activeTab) { 0 -> "phone"; 1 -> "till"; else -> "paybill" },
                        phone         = if (activeTab == 0) phone else null,
                        till          = if (activeTab == 1) till  else null,
                        paybill       = if (activeTab == 2) paybill else null,
                        accountNumber = if (activeTab == 2) accountNo else null,
                        amount        = amountDouble,
                        category      = category,
                        pin           = newPin,
                    )
                    viewModel.initiatePayment(request)
                }
            },
            onDismiss = { showPinSheet = false; pin = "" },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Send Money", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.Close, "Close",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AuroraBackground(primary, secondary, accent, 0.3f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Tab selector ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AkibaShapes.button)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.button)
                        .padding(4.dp),
                ) {
                    listOf("Phone", "Till", "Paybill").forEachIndexed { index, label ->
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
                            Text(label,
                                color      = if (isActive) Color.White
                                             else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                fontFamily = DmSansFontFamily,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 14.sp,
                            )
                        }
                    }
                }

                // ── Recipient input ───────────────────────────────────────
                GlassCard(elevation = GlassElevation.Raised) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnimatedContent(targetState = activeTab, label = "recipientInput") { tab ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                when (tab) {
                                    0 -> AkibaTextField(
                                        value         = phone,
                                        onValueChange = { phone = it },
                                        label         = "Phone number",
                                        leadingIcon   = Icons.Rounded.Phone,
                                        keyboardType  = KeyboardType.Phone,
                                    )
                                    1 -> AkibaTextField(
                                        value         = till,
                                        onValueChange = { till = it },
                                        label         = "Till number",
                                        leadingIcon   = Icons.Rounded.Store,
                                        keyboardType  = KeyboardType.Number,
                                    )
                                    2 -> {
                                        AkibaTextField(
                                            value         = paybill,
                                            onValueChange = { paybill = it },
                                            label         = "Paybill number",
                                            leadingIcon   = Icons.Rounded.Business,
                                            keyboardType  = KeyboardType.Number,
                                        )
                                        AkibaTextField(
                                            value         = accountNo,
                                            onValueChange = { accountNo = it },
                                            label         = "Account number",
                                            leadingIcon   = Icons.Rounded.Tag,
                                            keyboardType  = KeyboardType.Number,
                                        )
                                    }
                                }
                            }
                        }

                        // Recent recipients
                        if (recipients.isNotEmpty()) {
                            Text("Recent", fontFamily = DmSansFontFamily, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recipients) { r -> RecipientChip(r, primary) {
                                    when {
                                        r.phone   != null -> { activeTab = 0; phone   = r.phone }
                                        r.till    != null -> { activeTab = 1; till    = r.till }
                                        r.paybill != null -> { activeTab = 2; paybill = r.paybill }
                                    }
                                }}
                            }
                        }
                    }
                }

                // ── Amount section ────────────────────────────────────────
                GlassCard(elevation = GlassElevation.Raised) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Amount", fontFamily = DmSansFontFamily, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Ksh", fontFamily = DmSansFontFamily, fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value         = amount,
                                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amount = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle     = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 42.sp,
                                    color      = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush   = SolidColor(primary),
                                singleLine    = true,
                                decorationBox = { inner ->
                                    Box {
                                        if (amount.isEmpty()) {
                                            Text("0", fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold, fontSize = 42.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                                        }
                                        inner()
                                    }
                                },
                            )
                        }

                        if (amountDouble > 0) {
                            Text(
                                "Ksh ${"%,.2f".format(amountDouble)}",
                                fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }

                        // Category chips
                        Text("Category", fontFamily = DmSansFontFamily, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { cat ->
                                val isSelected = category == cat
                                val bgColor by animateColorAsState(
                                    targetValue = if (isSelected) primary else Color.Transparent,
                                    label       = "catBg$cat",
                                )
                                val scale by animateFloatAsState(
                                    targetValue   = if (isSelected) 1.05f else 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                    label         = "catScale$cat",
                                )
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer(scaleX = scale, scaleY = scale)
                                        .clip(AkibaShapes.chip)
                                        .background(bgColor)
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.Transparent
                                            else MaterialTheme.akibaColors.glassBorder,
                                            AkibaShapes.chip,
                                        )
                                        .clickable { category = cat }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(cat, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                                        color = if (isSelected) Color.White
                                                else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // ── Budget warning ────────────────────────────────────────
                AnimatedVisibility(
                    visible = budgetCheck is BudgetCheckState.Result,
                    enter   = expandVertically() + fadeIn(),
                    exit    = shrinkVertically() + fadeOut(),
                ) {
                    val result = (budgetCheck as? BudgetCheckState.Result)?.warning
                    result?.let {
                        val borderColor = if (it.canAfford) accent else MaterialTheme.akibaColors.gold
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AkibaShapes.card)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.card)
                                .drawBehind {
                                    drawRect(
                                        color   = borderColor,
                                        size    = androidx.compose.ui.geometry.Size(4.dp.toPx(), this.size.height),
                                    )
                                }
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (it.canAfford) "✓" else "⚠", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (it.canAfford)
                                        "Ksh ${"%,.0f".format(it.remaining)} remaining in ${it.category} after this."
                                    else
                                        "This will take your ${it.category} budget to ${it.projectedPercent.toInt()}%",
                                    fontFamily = DmSansFontFamily, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // ── Send button ───────────────────────────────────────────
                val buttonLabel by remember(recipient, amountDouble) {
                    derivedStateOf {
                        if (recipient.isNotBlank() && amountDouble > 0)
                            "Send Ksh ${"%,.0f".format(amountDouble)} to $recipient"
                        else "Send Money"
                    }
                }

                AkibaButton(
                    text     = buttonLabel,
                    enabled  = canProceed,
                    modifier = Modifier.fillMaxWidth(),
                    onClick  = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        pin = ""
                        showPinSheet = true
                    },
                )
            }
        }
    }
}

// ── Recipient chip ────────────────────────────────────────────────────────────
@Composable
private fun RecipientChip(
    recipient: RecipientDto,
    color    : Color,
    onClick  : () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label         = "chipScale",
    )
    GlassCard(
        elevation = GlassElevation.Flat,
        onClick   = onClick,
        modifier  = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
            ) {
                Text(recipient.nickname.take(1).uppercase(),
                    fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = color)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(recipient.nickname, fontFamily = DmSansFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(recipient.category, fontFamily = DmSansFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }
    }
}

// ── PIN bottom sheet ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinBottomSheet(
    recipient  : String,
    amount     : Double,
    pin        : String,
    onPinChange: (String) -> Unit,
    onDismiss  : () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val haptic  = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = AkibaShapes.bottomSheet,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(24.dp),
        ) {
            Text("Enter your 4-digit PIN", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface)

            // Recipient + amount summary
            GlassCard(elevation = GlassElevation.Flat) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(recipient, fontFamily = DmSansFontFamily, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Ksh ${"%,.2f".format(amount)}", fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = primary)
                }
            }

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    val isFilled = index < pin.length
                    val dotScale by animateFloatAsState(
                        targetValue   = if (isFilled) 1f else 0.6f,
                        animationSpec = spring(stiffness = 600f),
                        label         = "dot$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer(scaleX = dotScale, scaleY = dotScale)
                            .clip(CircleShape)
                            .background(
                                if (isFilled)
                                    Brush.radialGradient(listOf(primary, primary.copy(alpha = 0.6f)))
                                else
                                    Brush.radialGradient(listOf(
                                        MaterialTheme.akibaColors.glassBorder,
                                        MaterialTheme.akibaColors.glassBorder,
                                    ))
                            ),
                    )
                }
            }

            // Numpad
            val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns          = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier         = Modifier.height(240.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(keys.size) { index ->
                    val key = keys[index]
                    if (key.isEmpty()) {
                        Box(modifier = Modifier.size(72.dp))
                    } else {
                        var pressed by remember { mutableStateOf(false) }
                        val bgColor by animateColorAsState(
                            targetValue = if (pressed) primary.copy(alpha = 0.2f)
                                          else MaterialTheme.colorScheme.surface,
                            label       = "keyBg$index",
                        )
                        val scale by animateFloatAsState(
                            targetValue   = if (pressed) 0.85f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessHigh),
                            label         = "keyScale$index",
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(72.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .border(1.dp, MaterialTheme.akibaColors.glassBorder,
                                    androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pressed = true
                                    when (key) {
                                        "⌫" -> if (pin.isNotEmpty()) onPinChange(pin.dropLast(1))
                                        else -> if (pin.length < 4) onPinChange(pin + key)
                                    }
                                },
                        ) {
                            Text(key, fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
