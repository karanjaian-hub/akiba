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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.akiba.app.data.remote.api.NotificationApiService
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val api: NotificationApiService,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { loadNotifications() }

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { api.getNotifications() }
                .getOrNull()?.body()?.let { list ->
                    _notifications.value = list.map { n ->
                        AppNotification(
                            id      = n.id,
                            type    = when {
                                n.type.contains("payment", true) -> NotificationType.PAYMENT
                                n.type.contains("budget",  true) -> NotificationType.BUDGET_EXCEEDED
                                n.type.contains("goal",    true) -> NotificationType.GOAL_ACHIEVED
                                n.type.contains("saving",  true) -> NotificationType.SAVINGS_NUDGE
                                n.type.contains("report",  true) -> NotificationType.REPORT_READY
                                else                              -> NotificationType.SYSTEM_ERROR
                            },
                            title   = n.title,
                            message = n.message,
                            timeAgo = n.createdAt.take(10),
                            isRead  = n.read,
                        )
                    }
                }
            _isLoading.value = false
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            runCatching { api.markRead(id) }
            _notifications.update { list ->
                list.map { if (it.id == id) it.copy(isRead = true) else it }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching { api.markAllRead() }
            _notifications.update { list -> list.map { it.copy(isRead = true) } }
        }
    }

    fun dismiss(id: String) {
        _notifications.update { list -> list.filter { it.id != id } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavHostController,
    viewModel    : NotificationViewModel = hiltViewModel(),
) {
    val primary       = MaterialTheme.colorScheme.primary
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isLoading     by viewModel.isLoading.collectAsStateWithLifecycle()
    val unread        = notifications.filter { !it.isRead }
    val read          = notifications.filter { it.isRead }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Notifications", fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (unread.isNotEmpty()) {
                        TextButton(onClick = { viewModel.markAllRead() }) {
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
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primary)
                }
            }
            notifications.isEmpty() -> EmptyNotificationsState()
            else -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    if (unread.isNotEmpty()) {
                        itemsIndexed(unread, key = { _, n -> n.id }) { index, notification ->
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { delay(index * 50L); visible = true }
                            AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)) + slideInVertically { 20 }) {
                                NotificationItem(
                                    notification = notification,
                                    onDismiss    = { viewModel.dismiss(notification.id) },
                                    onTap        = { viewModel.markRead(notification.id) },
                                )
                            }
                        }
                    }

                    if (unread.isNotEmpty() && read.isNotEmpty()) {
                        item {
                            Row(
                                modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Divider(Modifier.weight(1f), color = MaterialTheme.akibaColors.glassBorder)
                                Text("  Earlier  ", fontFamily = DmSansFontFamily, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                Divider(Modifier.weight(1f), color = MaterialTheme.akibaColors.glassBorder)
                            }
                        }
                    }

                    itemsIndexed(read, key = { _, n -> n.id }) { index, notification ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(index * 50L); visible = true }
                        AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)) + slideInVertically { 20 }) {
                            NotificationItem(
                                notification = notification,
                                onDismiss    = { viewModel.dismiss(notification.id) },
                                onTap        = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

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
        targetValue = if (!notification.isRead) primary.copy(alpha = 0.06f) else Color.Transparent,
        label       = "notifBg",
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDismiss(); true } else false
        }
    )

    SwipeToDismissBox(
        state             = dismissState,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, error)))
                    .padding(end = 20.dp),
            ) {
                Icon(Icons.Rounded.Delete, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(iconColor, iconColor.copy(alpha = 0.5f)))),
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
                Text(notification.timeAgo, fontFamily = DmSansFontFamily, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
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
    Divider(color = MaterialTheme.akibaColors.glassBorder, thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun EmptyNotificationsState() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Notifications, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                modifier = Modifier.size(72.dp))
            Text("All caught up", fontFamily = SoraFontFamily, fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text("No new notifications", fontFamily = DmSansFontFamily, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}
