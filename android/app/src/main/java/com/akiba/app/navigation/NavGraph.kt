package com.akiba.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.akiba.app.ui.components.common.AuroraBackground
import com.akiba.app.ui.components.common.AkibaLogo
import com.akiba.app.ui.components.common.AkibaLogoSize
import com.akiba.app.ui.components.common.AkibaLogoVariant
import com.akiba.app.ui.screens.auth.*
import com.akiba.app.ui.screens.dashboard.DashboardScreen
import com.akiba.app.ui.screens.dashboard.ImportScreen
import com.akiba.app.ui.screens.dashboard.MpesaImportScreen
import com.akiba.app.ui.screens.dashboard.BankImportScreen
import com.akiba.app.ui.screens.payments.PaymentScreen
import com.akiba.app.ui.screens.payments.PaymentPendingScreen
import com.akiba.app.ui.screens.payments.PaymentSuccessScreen
import com.akiba.app.ui.screens.payments.PaymentFailedScreen
import com.akiba.app.ui.screens.history.TransactionListScreen
import com.akiba.app.ui.screens.history.TransactionDetailScreen
import com.akiba.app.ui.screens.budgets.BudgetScreen
import com.akiba.app.ui.screens.savings.GoalsScreen
import com.akiba.app.ui.screens.ai.AiChatScreen
import com.akiba.app.ui.screens.profile.NotificationsScreen
import com.akiba.app.ui.screens.profile.ProfileScreen
import com.akiba.app.ui.screens.profile.AppearanceScreen
import com.akiba.app.ui.screens.profile.EditProfileScreen
import com.akiba.app.ui.theme.DmSansFontFamily
import com.akiba.app.ui.theme.akibaColors

@Composable
fun RootNavGraph(isLoggedIn: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = if (isLoggedIn) "main" else "auth",
        enterTransition  = { fadeIn(tween(300)) },
        exitTransition   = { fadeOut(tween(200)) },
    ) {
        authGraph(navController, isLoggedIn)
        mainGraph(navController)
    }
}

// ── Auth graph ────────────────────────────────────────────────────────────────
fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    isLoggedIn   : Boolean,
) {
    navigation(
        route            = "auth",
        startDestination = Screen.Splash.route,
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController, isLoggedIn)
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(navController)
        }
        composable(Screen.Login.route) {
            LoginScreen(navController)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController)
        }
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(navController)
        }
        composable(
            route     = Screen.OtpVerification.route,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("type")  { type = NavType.StringType },
            ),
        ) { backStack ->
            OtpVerificationScreen(
                navController = navController,
                email         = backStack.arguments?.getString("email") ?: "",
                type          = backStack.arguments?.getString("type")  ?: "verify",
            )
        }
        composable(
            route     = Screen.ResetPassword.route,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("otp")   { type = NavType.StringType },
            ),
        ) { backStack ->
            ResetPasswordScreen(
                navController = navController,
                email         = backStack.arguments?.getString("email") ?: "",
                otp           = backStack.arguments?.getString("otp")   ?: "",
            )
        }
    }
}

// ── Main graph ────────────────────────────────────────────────────────────────
fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation(
        route            = "main",
        startDestination = Screen.Dashboard.route,
    ) {
        composable(Screen.Dashboard.route) { DashboardScreen(navController) }
        composable(Screen.Payments.route) { PaymentScreen(navController) }
        composable("payment_pending/{paymentId}",
            arguments = listOf(navArgument("paymentId") { type = NavType.StringType })
        ) { PaymentPendingScreen(navController, it.arguments?.getString("paymentId") ?: "") }
        composable("payment_success/{paymentId}",
            arguments = listOf(navArgument("paymentId") { type = NavType.StringType })
        ) { PaymentSuccessScreen(navController, it.arguments?.getString("paymentId") ?: "") }
        composable("payment_failed/{reason}",
            arguments = listOf(navArgument("reason") { type = NavType.StringType })
        ) { PaymentFailedScreen(navController, it.arguments?.getString("reason") ?: "Unknown error") }
        composable(Screen.Goals.route)         { ScreenPlaceholder("Goals")     }
        composable(Screen.History.route)       { ScreenPlaceholder("History")   }
        composable(Screen.Profile.route)       { ScreenPlaceholder("Profile")   }
        composable(Screen.Budgets.route) { BudgetScreen(navController) }
        composable(Screen.Savings.route)       { ScreenPlaceholder("Savings")   }
        composable(Screen.AiChat.route) { AiChatScreen(navController) }
        composable(Screen.Notifications.route) { NotificationsScreen(navController) }
        composable("import")        { ImportScreen(navController) }
        composable("mpesa_import")  { MpesaImportScreen(navController) }
        composable("bank_import")   { BankImportScreen(navController) }
        composable("appearance")     { AppearanceScreen(navController) }
        composable("edit_profile")   { EditProfileScreen(navController) }
    }
}

// ── Bottom navigation bar ─────────────────────────────────────────────────────
@Composable
fun AkibaBottomBar(navController: NavHostController) {
    val backStack    by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val primary      = MaterialTheme.colorScheme.primary
    val secondary    = MaterialTheme.colorScheme.secondary
    val accent       = MaterialTheme.akibaColors.accentGreen
    val surface      = MaterialTheme.colorScheme.surface

    val activeColors = listOf(primary, secondary, accent, primary, secondary)

    NavigationBar(
        containerColor = surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        modifier       = Modifier.height(68.dp),
    ) {
        bottomNavItems.forEachIndexed { index, item ->
            val isActive = currentRoute == item.screen.route

            val iconScale by animateFloatAsState(
                targetValue   = if (isActive) 1.18f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label         = "iconScale$index",
            )
            val iconColor by animateColorAsState(
                targetValue   = if (isActive) activeColors[index]
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                animationSpec = tween(200),
                label         = "iconColor$index",
            )

            NavigationBarItem(
                selected = isActive,
                onClick  = {
                    if (!isActive) {
                        navController.navigate(item.screen.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon  = {
                    Icon(
                        imageVector        = item.icon,
                        contentDescription = item.screen.title,
                        tint               = iconColor,
                        modifier           = Modifier.size((24 * iconScale).dp),
                    )
                },
                label = {
                    Text(
                        text       = item.screen.title,
                        fontSize   = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color      = iconColor,
                        fontFamily = DmSansFontFamily,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = activeColors[index].copy(alpha = 0.12f),
                ),
            )
        }
    }
}

// ── Generic screen placeholder ────────────────────────────────────────────────
@Composable
private fun ScreenPlaceholder(name: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxSize(),
    ) {
        Text(
            text  = "$name — coming in Phase 4+",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}
