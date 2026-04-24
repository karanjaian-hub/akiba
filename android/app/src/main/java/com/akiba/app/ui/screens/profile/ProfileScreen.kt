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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.akiba.app.data.repository.AuthRepository
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _userName  = MutableStateFlow("User")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            runCatching { authRepository.getProfile() }
                .getOrNull()?.getOrNull()?.let { user ->
                    _userName.value  = user.fullName
                    _userEmail.value = user.email
                }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onComplete()
        }
    }
}

// ── Profile screen ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    viewModel    : ProfileViewModel = hiltViewModel(),
) {
    val userName  by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val primary   = MaterialTheme.colorScheme.primary
    val accent    = MaterialTheme.akibaColors.accentGreen

    var showLogoutDialog  by remember { mutableStateOf(false) }
    var headerVisible     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { headerVisible = true }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor   = MaterialTheme.colorScheme.surface,
            title = {
                Text("Sign out of Akiba?", fontFamily = SoraFontFamily,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text("You'll need to sign in again to access your account.",
                    fontFamily = DmSansFontFamily, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            },
            confirmButton = {
                AkibaButton(
                    text    = "Sign Out",
                    variant = AkibaButtonVariant.Danger,
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                )
            },
            dismissButton = {
                AkibaButton(
                    text    = "Cancel",
                    variant = AkibaButtonVariant.Ghost,
                    onClick = { showLogoutDialog = false },
                )
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // ── Profile header ────────────────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = headerVisible,
                    enter   = fadeIn(tween(500)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                androidx.compose.foundation.shape.RoundedCornerShape(
                                    bottomStart = 28.dp, bottomEnd = 28.dp
                                )
                            )
                            .background(
                                Brush.linearGradient(
                                    listOf(primary, primary.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f))
                                )
                            )
                            .padding(28.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.fillMaxWidth(),
                        ) {
                            // Avatar
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier         = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .border(3.dp, Color.White, CircleShape),
                            ) {
                                Text(
                                    text       = userName.take(1).uppercase(),
                                    fontFamily = SoraFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 36.sp,
                                    color      = Color.White,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            TextButton(onClick = { /* TODO: photo picker */ }) {
                                Text("Change Photo", color = Color.White.copy(alpha = 0.7f),
                                    fontFamily = DmSansFontFamily, fontSize = 12.sp)
                            }

                            Text(userName, fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold, fontSize = 22.sp,
                                color = Color.White)

                            Text(userEmail, fontFamily = DmSansFontFamily,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f))

                            Spacer(Modifier.height(8.dp))

                            AkibaBadge("Member", BadgeVariant.Purple)

                            Spacer(Modifier.height(4.dp))

                            Text(
                                "Member since ${java.text.SimpleDateFormat("MMMM yyyy",
                                    java.util.Locale.getDefault()).format(java.util.Date())}",
                                fontFamily = DmSansFontFamily, fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // ── Account section ───────────────────────────────────────────
            item {
                MenuSection(
                    title = "ACCOUNT",
                    items = listOf(
                        MenuItemData(
                            icon    = Icons.Rounded.Person,
                            color   = primary,
                            label   = "Edit Profile",
                            onClick = { navController.navigate("edit_profile") },
                        ),
                        MenuItemData(
                            icon    = Icons.Rounded.Lock,
                            color   = primary,
                            label   = "Change Password",
                            onClick = { navController.navigate(Screen.ForgotPassword.route) },
                        ),
                        MenuItemData(
                            icon    = Icons.Rounded.Notifications,
                            color   = primary,
                            label   = "Notifications",
                            onClick = { navController.navigate(Screen.Notifications.route) },
                        ),
                    ),
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── Preferences section ───────────────────────────────────────
            item {
                MenuSection(
                    title = "PREFERENCES",
                    items = listOf(
                        MenuItemData(
                            icon    = Icons.Rounded.DarkMode,
                            color   = MaterialTheme.colorScheme.secondary,
                            label   = "Appearance",
                            onClick = { navController.navigate("appearance") },
                        ),
                        MenuItemData(
                            icon    = Icons.Rounded.Fingerprint,
                            color   = MaterialTheme.colorScheme.secondary,
                            label   = "Biometric Lock",
                            onClick = { },
                            trailing = {
                                var enabled by remember { mutableStateOf(false) }
                                val thumbColor by animateColorAsState(
                                    targetValue = if (enabled) MaterialTheme.akibaColors.gold
                                                  else MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                    label       = "thumbColor",
                                )
                                Switch(
                                    checked         = enabled,
                                    onCheckedChange = { enabled = it },
                                    colors          = SwitchDefaults.colors(
                                        checkedThumbColor   = thumbColor,
                                        checkedTrackColor   = MaterialTheme.akibaColors.gold.copy(0.3f),
                                    ),
                                )
                            },
                        ),
                        MenuItemData(
                            icon    = Icons.Rounded.Language,
                            color   = MaterialTheme.colorScheme.secondary,
                            label   = "Language",
                            value   = "English (Kenya)",
                            onClick = { },
                        ),
                    ),
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── About section ─────────────────────────────────────────────
            item {
                MenuSection(
                    title = "ABOUT",
                    items = listOf(
                        MenuItemData(
                            icon    = Icons.Rounded.PrivacyTip,
                            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            label   = "Privacy Policy",
                            onClick = { },
                        ),
                        MenuItemData(
                            icon    = Icons.Rounded.Description,
                            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            label   = "Terms of Service",
                            onClick = { },
                        ),
                        MenuItemData(
                            icon    = Icons.Rounded.Info,
                            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            label   = "App Version",
                            value   = "1.0.0",
                            onClick = { },
                        ),
                    ),
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            // ── Sign out ──────────────────────────────────────────────────
            item {
                GlassCard(
                    elevation = GlassElevation.Raised,
                    onClick   = { showLogoutDialog = true },
                    modifier  = Modifier.padding(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(MaterialTheme.colorScheme.error,
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                    )
                                ),
                        ) {
                            Icon(Icons.Rounded.ExitToApp, null,
                                tint     = Color.White,
                                modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Sign Out", fontFamily = DmSansFontFamily,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ── Menu section ──────────────────────────────────────────────────────────────
data class MenuItemData(
    val icon    : ImageVector,
    val color   : Color,
    val label   : String,
    val value   : String?              = null,
    val onClick : () -> Unit,
    val trailing: (@Composable () -> Unit)? = null,
)

@Composable
private fun MenuSection(title: String, items: List<MenuItemData>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text          = title,
            fontFamily    = DmSansFontFamily,
            fontSize      = 11.sp,
            letterSpacing = 0.1.em,
            color         = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight    = FontWeight.SemiBold,
            modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        GlassCard(elevation = GlassElevation.Raised) {
            Column {
                items.forEachIndexed { index, item ->
                    MenuItem(item)
                    if (index < items.size - 1) {
                        Divider(color = MaterialTheme.akibaColors.glassBorder,
                            thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(item: MenuItemData) {
    var pressed by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue   = if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color.Transparent,
        animationSpec = tween(100),
        label         = "menuBg",
    )

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable {
                pressed = true
                item.onClick()
            }
            .padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.12f)),
            ) {
                Icon(item.icon, null, tint = item.color,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(item.label, fontFamily = DmSansFontFamily, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            item.value?.let {
                Text(it, fontFamily = DmSansFontFamily, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Spacer(Modifier.width(4.dp))
            }
            if (item.trailing != null) {
                item.trailing.invoke()
            } else {
                Icon(Icons.Rounded.ChevronRight, null,
                    tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}
