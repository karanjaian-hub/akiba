package com.akiba.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.ui.graphics.vector.ImageVector

// ── Sealed class — every screen in the app has a route here ──────────────────
// Adding a screen? Add it here first. This is the single source of truth.
sealed class Screen(val route: String, val title: String) {

    // ── Auth flow ─────────────────────────────────────────────────────────
    object Splash          : Screen("splash",           "Splash")
    object Onboarding      : Screen("onboarding",       "Onboarding")
    object Login           : Screen("login",            "Login")
    object Register        : Screen("register",         "Register")
    object ForgotPassword  : Screen("forgot_password",  "Forgot Password")

    // OTP and Reset carry arguments in the route
    object OtpVerification : Screen("otp/{email}/{type}", "Verify Email") {
        fun createRoute(email: String, type: String) = "otp/$email/$type"
    }
    object ResetPassword   : Screen("reset/{email}/{otp}", "Reset Password") {
        fun createRoute(email: String, otp: String) = "reset/$email/$otp"
    }

    // ── Main tabs ─────────────────────────────────────────────────────────
    object Dashboard : Screen("dashboard", "Home")
    object Payments  : Screen("payments",  "Payments")
    object Goals     : Screen("goals",     "Goals")
    object History   : Screen("history",   "History")
    object Profile   : Screen("profile",   "Profile")

    // ── Nested / modal screens ────────────────────────────────────────────
    object Budgets       : Screen("budgets",       "Budgets")
    object Savings       : Screen("savings",       "Savings")
    object AiChat        : Screen("ai_chat",       "AI Assistant")
    object Notifications : Screen("notifications", "Notifications")
}

// ── Bottom nav items — only the 5 tabs shown in the NavigationBar ─────────────
data class BottomNavItem(
    val screen      : Screen,
    val icon        : ImageVector,
    val activeColor : androidx.compose.ui.graphics.Color? = null, // null = use primary
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Rounded.GridView),
    BottomNavItem(Screen.Payments,  Icons.AutoMirrored.Rounded.Send),
    BottomNavItem(Screen.Goals,     Icons.Rounded.TrackChanges),
    BottomNavItem(Screen.History,   Icons.Rounded.History),
    BottomNavItem(Screen.Profile,   Icons.Rounded.Person),
)
