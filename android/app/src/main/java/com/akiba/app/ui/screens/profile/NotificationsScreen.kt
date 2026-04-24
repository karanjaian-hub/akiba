package com.akiba.app.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import kotlinx.coroutines.delay

// ── Notification model ────────────────────────────────────────────────────────
data class AppNotification(
    val id      : String = java.util.UUID.randomUUID().toString(),
    val type    : NotificationType,
    val title   : String,
    val message : String,
    val timeAgo : String,
    val isRead  : Boolean = false,
)

enum class NotificationType {
    PAYMENT, BUDGET_EXCEEDED, GOAL_ACHIEVED, SAVINGS_NUDGE, REPORT_READY, SYSTEM_ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavHostController) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val gold      = MaterialTheme.akibaColors.gold
    val error     = MaterialTheme.colorScheme.error

    // Sample notifications — replaced by real data from notification-service in production
    var notifications by remember {
        mutableStateOf(listOf(
            AppNotification(type = NotificationType.PAYMENT,
                title   = "Payment Successful",
                message = "Ksh 1,200 sent to Jane Doe via M-Pesa",
                timeAgo = "2 min ago", isRead = false),
            AppNotification(type = NotificationType.BUDGET_EXCEEDED,
                title   = "Budget Alert",
                message = "You've used 92% of your Food budget this month",
                timeAgo = "1 hr ago", isRead = false),
            AppNotification(type = NotificationType.GOAL_ACHIEVED,
                title   = "Goal Achieved! 🎉",
                message = "You've reached your Emergency Fund goal of Ksh 50,000",
                timeAgo = "3 hrs ago", isRead = false),
            AppNotification(type = NotificationType.SAVINGS_NUDGE,
                title   = "Savings Reminder",
                message = "You're Ksh 2,000 behind on your Laptop savings goal",
                timeAgo = "Yesterday", isRead = true),
            AppNotification(type = NotificationType.REPORT_READY,
                title   = "Monthly Report Ready",
                message = "Your March 2026 financial report is ready to view",
                timeAgo = "2 days ago", isRead = true),
            AppNotification(type = NotificationType.SYSTEM_ERROR,
                title   = "Sync Failed",
                message = "Could not sync your M-Pesa transactions. Tap to retry.",
                timeAgo = "3 days ago", isRead = true),
        ))
    }

    val unread = notifications.filter { !it.isRead }
    val read   = notifications.filter { it.isRead }

    fun markAllRead() {
        notifications = notifications.map { it.copy(isRead = true) }
    }

    fun dismiss(id: String) {
        notifications = notifications.filter { it.id != id }
    }

    fun markRead(id: String) {
        notifications = notifications.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (unread.isNotEmpty()) {
                        TextButton(onClick = { markAllRead() }) {
                            Text("Mark all read", color = primary,
                                fontFamily = DmSansFontFamily, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (notifications.isEmpty()) {
            EmptyNotificationsState()
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(bottom = 80.dp),
            ) {
                // Unread section
                if (unread.isNotEmpty()) {
                    itemsIndexed(unread, key = { _, n -> n.id }) { index, notification ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 50L)
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter   = fadeIn(tween(300)) + slideInVertically { 20 },
                        ) {
                            NotificationItem(
                                notification = notification,
                                onDismiss    = { dismiss(notification.id) },
                                onTap        = { markRead(notification.id) },
                            )
                        }
                    }
                }

                // Divider between unread and read
                if (unread.isNotEmpty() && read.isNotEmpty()) {
                    item {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Divider(modifier = Modifier.weight(1f),
                                color = MaterialTheme.akibaColors.glassBorder)
                            Text("  Earlier  ", fontFamily = DmSansFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Divider(modifier = Modifier.weight(1f),
                                color = MaterialTheme.akibaColors.glassBorder)
                        }
                    }
                }

                // Read section
                itemsIndexed(read, key = { _, n -> n.id }) { index, notification ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 50L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter   = fadeIn(tween(300)) + slideInVertically { 20 },
                    ) {
                        NotificationItem(
                            notification = notification,
                            onDismiss    = { dismiss(notification.id) },
                            onTap        = { },
                        )
                    }
                }
            }
        }
    }
}

// ── Notification item ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationItem(
    notification: AppNotification,
    onDismiss   : () -> Unit,
    onTap       : () -> Unit,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val gold      = MaterialTheme.akibaColors.gold
    val error     = MaterialTheme.colorScheme.error

    val (iconColor, icon) = when (notification.type) {
        NotificationType.PAYMENT         -> secondary to Icons.Rounded.Send
        NotificationType.BUDGET_EXCEEDED -> gold      to Icons.Rounded.Warning
        NotificationType.GOAL_ACHIEVED   -> accent    to Icons.Rounded.EmojiEvents
        NotificationType.SAVINGS_NUDGE   -> primary   to Icons.Rounded.TrackChanges
        NotificationType.REPORT_READY    -> secondary to Icons.Rounded.Description
        NotificationType.SYSTEM_ERROR    -> error     to Icons.Rounded.ErrorOutline
    }

    val bgColor by animateColorAsState(
        targetValue = if (!notification.isRead)
            primary.copy(alpha = 0.06f)
        else Color.Transparent,
        label = "notifBg",
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDismiss(); true }
            else false
        }
    )

    SwipeToDismissBox(
        state             = dismissState,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier         = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, error))
                    )
                    .padding(end = 20.dp),
            ) {
                Icon(Icons.Rounded.Delete, null,
                    tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.Top,
        ) {
            // Icon circle
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(iconColor, iconColor.copy(alpha = 0.5f)))
                    ),
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = notification.title,
                    fontFamily = SoraFontFamily,
                    fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text       = notification.message,
                    fontFamily = DmSansFontFamily,
                    fontSize   = 13.sp,
                    lineHeight  = 18.sp,
                    maxLines   = 2,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(4.dp))
                Text(notification.timeAgo, fontFamily = DmSansFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }

            // Unread dot
            if (!notification.isRead) {
                val dotScale = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    dotScale.animateTo(1f, spring(stiffness = Spring.StiffnessHigh))
                }
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(8.dp)
                        .graphicsLayer(scaleX = dotScale.value, scaleY = dotScale.value)
                        .clip(CircleShape)
                        .background(primary),
                )
            }
        }
    }

    Divider(
        color     = MaterialTheme.akibaColors.glassBorder,
        thickness = 0.5.dp,
        modifier  = Modifier.padding(horizontal = 16.dp),
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyNotificationsState() {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Notifications, null,
                tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                modifier = Modifier.size(72.dp))
            Text("All caught up ✓", fontFamily = SoraFontFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text("No new notifications", fontFamily = DmSansFontFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}
