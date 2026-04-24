package com.akiba.app.ui.screens.auth


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*

@Composable
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
fun RegisterScreen(
    navController: NavHostController,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val registerState by viewModel.registerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentStep by remember { mutableIntStateOf(0) }

    // ── React to register state ───────────────────────────────────────────
    LaunchedEffect(registerState) {
        when (val state = registerState) {
            is AuthUiState.Success -> {
                navController.navigate(
                    Screen.OtpVerification.createRoute(viewModel.step1.email, "verify")
                ) { popUpTo(Screen.Register.route) { inclusive = true } }
            }
            is AuthUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetRegisterState()
            }
            else -> Unit
        }
    }

    val isLoading = registerState is AuthUiState.Loading

    Scaffold(
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AuroraBackground(
                primary   = MaterialTheme.colorScheme.primary,
                secondary = MaterialTheme.colorScheme.secondary,
                accent    = MaterialTheme.akibaColors.accentGreen,
                intensity = 0.5f,
            )

            Column(modifier = Modifier.fillMaxSize()) {

                // ── Step progress header ──────────────────────────────────
                StepProgressHeader(currentStep = currentStep)

                // ── Step content with slide transition ────────────────────
                AnimatedContent(
                    targetState   = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn(tween(250)) togetherWith
                            slideOutHorizontally { -it } + fadeOut(tween(200))
                        } else {
                            slideInHorizontally { -it } + fadeIn(tween(250)) togetherWith
                            slideOutHorizontally { it } + fadeOut(tween(200))
                        }
                    },
                    label = "stepContent",
                    modifier = Modifier.weight(1f),
                ) { step ->
                    when (step) {
                        0 -> Step1BasicInfo(viewModel)
                        1 -> Step2FinancialProfile(viewModel)
                        2 -> Step3BudgetSetup(viewModel)
                        3 -> Step4BankAccount(viewModel)
                    }
                }

                // ── Navigation buttons ────────────────────────────────────
                StepNavButtons(
                    currentStep = currentStep,
                    isLoading   = isLoading,
                    onBack      = {
                        if (currentStep > 0) currentStep--
                        else navController.popBackStack()
                    },
                    onNext = {
                        if (currentStep < 3) currentStep++
                        else viewModel.register()
                    },
                )
            }
        }
    }
}

// ── Step progress header ──────────────────────────────────────────────────────
@Composable
private fun StepProgressHeader(currentStep: Int) {
    val steps  = listOf("Basic Info", "Financial", "Budgets", "Bank")
    val primary = MaterialTheme.colorScheme.primary
    val accent  = MaterialTheme.akibaColors.accentGreen

    Row(
        modifier              = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        steps.forEachIndexed { index, label ->
            // Step circle
            val isComplete = index < currentStep
            val isActive   = index == currentStep
            val circleColor by animateColorAsState(
                targetValue   = when {
                    isComplete -> accent
                    isActive   -> primary
                    else       -> MaterialTheme.colorScheme.surface
                },
                label = "circleColor$index",
            )
            val scale by animateFloatAsState(
                targetValue   = if (isActive) 1.1f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label         = "circleScale$index",
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(32.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(CircleShape)
                        .background(circleColor)
                        .border(
                            width = 1.dp,
                            color = if (isActive || isComplete) Color.Transparent
                                    else MaterialTheme.akibaColors.glassBorder,
                            shape = CircleShape,
                        ),
                ) {
                    if (isComplete) {
                        Icon(Icons.Rounded.Check, null,
                            tint     = Color.White,
                            modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text       = "${index + 1}",
                            color      = if (isActive) Color.White
                                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize   = 12.sp,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = label,
                    fontSize   = 9.sp,
                    fontFamily = DmSansFontFamily,
                    color      = if (isActive) primary
                                 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }

            // Connecting line between circles
            if (index < steps.size - 1) {
                val lineColor by animateColorAsState(
                    targetValue   = if (index < currentStep) accent
                                    else MaterialTheme.akibaColors.glassBorder,
                    label         = "lineColor$index",
                )
                Divider(
                    modifier  = Modifier.weight(1f).padding(horizontal = 4.dp),
                    color     = lineColor,
                    thickness = 1.dp,
                )
            }
        }
    }
}

// ── Step 1 — Basic Info ───────────────────────────────────────────────────────
@Composable
private fun Step1BasicInfo(viewModel: AuthViewModel) {
    val step = viewModel.step1
    Column(
        modifier              = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(elevation = GlassElevation.Raised) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AkibaTextField(
                    value         = step.fullName,
                    onValueChange = { viewModel.updateStep1 { copy(fullName = it) } },
                    label         = "Full Name",
                    leadingIcon   = Icons.Rounded.Person,
                )
                AkibaTextField(
                    value         = step.email,
                    onValueChange = { viewModel.updateStep1 { copy(email = it) } },
                    label         = "Email",
                    leadingIcon   = Icons.Rounded.Email,
                    keyboardType  = KeyboardType.Email,
                )
                AkibaTextField(
                    value         = step.phone,
                    onValueChange = { viewModel.updateStep1 { copy(phone = it) } },
                    label         = "Phone (07XX XXX XXX)",
                    leadingIcon   = Icons.Rounded.Phone,
                    keyboardType  = KeyboardType.Phone,
                )
                AkibaTextField(
                    value         = step.password,
                    onValueChange = { viewModel.updateStep1 { copy(password = it) } },
                    label         = "Password",
                    leadingIcon   = Icons.Rounded.Lock,
                    isPassword    = true,
                )
                AkibaTextField(
                    value         = step.confirmPassword,
                    onValueChange = { viewModel.updateStep1 { copy(confirmPassword = it) } },
                    label         = "Confirm Password",
                    leadingIcon   = Icons.Rounded.Lock,
                    isPassword    = true,
                )
            }
        }
    }
}

// ── Step 2 — Financial Profile ────────────────────────────────────────────────
@Composable
private fun Step2FinancialProfile(viewModel: AuthViewModel) {
    val step = viewModel.step2
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassCard(elevation = GlassElevation.Raised) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ChipSelector(
                    label    = "Monthly Income",
                    options  = listOf("Under 20k", "20k–50k", "50k–100k", "100k+"),
                    selected = step.incomeRange,
                    onSelect = { viewModel.updateStep2 { copy(incomeRange = it) } },
                )
                ChipSelector(
                    label    = "Employment Type",
                    options  = listOf("Employed", "Self-employed", "Student", "Other"),
                    selected = step.employmentType,
                    onSelect = { viewModel.updateStep2 { copy(employmentType = it) } },
                )
                ChipSelector(
                    label    = "Primary Goal",
                    options  = listOf("Save more", "Budget better", "Track spending", "Invest"),
                    selected = step.primaryGoal,
                    onSelect = { viewModel.updateStep2 { copy(primaryGoal = it) } },
                )
            }
        }
    }
}

// ── Step 3 — Budget Setup ─────────────────────────────────────────────────────
@Composable
private fun Step3BudgetSetup(viewModel: AuthViewModel) {
    val categories = listOf(
        "Food 🍽"         to 8000f,
        "Transport 🚗"    to 5000f,
        "Rent 🏠"         to 15000f,
        "Bills ⚡"        to 3000f,
        "Entertainment 🎬" to 2000f,
        "Health 💊"       to 2000f,
    )

    val budgets = remember {
        mutableStateMapOf<String, Float>().apply {
            categories.forEach { (name, default) -> put(name, default) }
        }
    }

    // Sync into viewModel whenever values change
    LaunchedEffect(budgets.toMap()) {
        viewModel.updateStep3 {
            copy(budgets = budgets.map { BudgetSetup(it.key, it.value) })
        }
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(elevation = GlassElevation.Raised) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                categories.forEach { (name, _) ->
                    val value = budgets[name] ?: 0f
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(name, fontFamily = DmSansFontFamily, fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Ksh ${value.toInt().formatAmount()}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize   = 13.sp,
                                color      = MaterialTheme.akibaColors.gold,
                            )
                        }
                        Slider(
                            value         = value,
                            onValueChange = { budgets[name] = it },
                            valueRange    = 0f..50000f,
                            steps         = 49,
                            colors        = SliderDefaults.colors(
                                thumbColor        = MaterialTheme.colorScheme.primary,
                                activeTrackColor  = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor= MaterialTheme.akibaColors.glassBorder,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ── Step 4 — Bank Account ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step4BankAccount(viewModel: AuthViewModel) {
    val step = viewModel.step4
    val banks = listOf("KCB", "Equity", "Co-op", "NCBA", "Absa", "Standard Chartered", "DTB", "I&M")
    var showBankSheet by remember { mutableStateOf(false) }

    if (showBankSheet) {
        ModalBottomSheet(onDismissRequest = { showBankSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Bank", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                banks.forEach { bank ->
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateStep4 { copy(bankName = bank) }
                                showBankSheet = false
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Initial avatar
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier         = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            ) {
                                Text(bank.first().toString(),
                                    color      = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 14.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(bank, fontFamily = DmSansFontFamily,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (step.bankName == bank) {
                            Icon(Icons.Rounded.Check, null,
                                tint = MaterialTheme.akibaColors.accentGreen)
                        }
                    }
                    Divider(color = MaterialTheme.akibaColors.glassBorder)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(elevation = GlassElevation.Raised) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Bank selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AkibaShapes.input)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.input)
                        .clickable { showBankSheet = true }
                        .padding(16.dp),
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = step.bankName.ifBlank { "Select Bank" },
                            fontFamily = DmSansFontFamily,
                            color      = if (step.bankName.isBlank())
                                             MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                         else MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                AkibaTextField(
                    value         = step.accountNickname,
                    onValueChange = { viewModel.updateStep4 { copy(accountNickname = it) } },
                    label         = "Account Nickname (e.g. Salary account)",
                    leadingIcon   = Icons.Rounded.AccountBalance,
                )

                // Account type segmented control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AkibaShapes.button)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.akibaColors.glassBorder, AkibaShapes.button),
                ) {
                    listOf("Savings", "Current").forEach { type ->
                        val isSelected = step.accountType == type
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                          else Color.Transparent,
                            label       = "segmentBg$type",
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .weight(1f)
                                .clip(AkibaShapes.button)
                                .background(bgColor)
                                .clickable { viewModel.updateStep4 { copy(accountType = type) } }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text       = type,
                                color      = if (isSelected) Color.White
                                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontFamily = DmSansFontFamily,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Navigation buttons ────────────────────────────────────────────────────────
@Composable
private fun StepNavButtons(
    currentStep: Int,
    isLoading  : Boolean,
    onBack     : () -> Unit,
    onNext     : () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (currentStep > 0) {
            AkibaButton(
                text     = "Back",
                onClick  = onBack,
                variant  = AkibaButtonVariant.Ghost,
                modifier = Modifier.weight(0.4f),
            )
        }
        AkibaButton(
            text     = if (currentStep == 3) "Create Account" else "Next",
            onClick  = onNext,
            loading  = isLoading,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Chip selector ─────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipSelector(
    label   : String,
    options : List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontFamily = DmSansFontFamily, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = selected == option
                val scale by animateFloatAsState(
                    targetValue   = if (isSelected) 1.05f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label         = "chipScale$option",
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) primary else Color.Transparent,
                    label       = "chipBg$option",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(AkibaShapes.chip)
                        .background(bgColor)
                        .border(
                            1.dp,
                            if (isSelected) Color.Transparent
                            else MaterialTheme.akibaColors.glassBorder,
                            AkibaShapes.chip,
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text       = option,
                        color      = if (isSelected) Color.White
                                     else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize   = 13.sp,
                        fontFamily = DmSansFontFamily,
                    )
                }
            }
        }
    }
}

// ── Utility ───────────────────────────────────────────────────────────────────
private fun Int.formatAmount(): String {
    return "%,d".format(this)
}
