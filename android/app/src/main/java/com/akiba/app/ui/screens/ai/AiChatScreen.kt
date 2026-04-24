package com.akiba.app.ui.screens.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.akiba.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Message model ─────────────────────────────────────────────────────────────
data class ChatMessage(
    val id     : String = java.util.UUID.randomUUID().toString(),
    val role   : String, // "user" or "assistant"
    val content: String,
    val timestamp: String = java.text.SimpleDateFormat(
        "HH:mm", java.util.Locale.getDefault()
    ).format(java.util.Date()),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class AiChatViewModel @Inject constructor() : ViewModel() {

    private val _messages  = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping  = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    init {
        // Welcome message after short delay
        viewModelScope.launch {
            delay(500)
            _isTyping.value = true
            delay(1200)
            _isTyping.value = false
            _messages.update { it + ChatMessage(
                role    = "assistant",
                content = "Hey! I'm Akiba AI, your personal financial assistant. " +
                          "Ask me anything about your spending, budgets, or savings goals. " +
                          "Every shilling has a story — let's read yours. 💜",
            )}
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        val userMsg = ChatMessage(role = "user", content = content.trim())
        _messages.update { it + userMsg }

        viewModelScope.launch {
            _isTyping.value = true
            delay(800) // Simulate network latency

            // Build message history for the API
            val history = _messages.value.map {
                mapOf("role" to it.role, "content" to it.content)
            }

            try {
                val response = callClaudeApi(history)
                _isTyping.value = false
                _messages.update { it + ChatMessage(role = "assistant", content = response) }
            } catch (e: Exception) {
                _isTyping.value = false
                _messages.update { it + ChatMessage(
                    role    = "assistant",
                    content = "I'm having trouble connecting right now. Please try again.",
                )}
            }
        }
    }

    // Calls the Gemini API
    // In production this should go through your backend ai-service
    private suspend fun callClaudeApi(history: List<Map<String, String>>): String {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Build Gemini contents array from chat history
        val contents = history.map { msg ->
            mapOf(
                "role"  to if (msg["role"] == "assistant") "model" else "user",
                "parts" to listOf(mapOf("text" to msg["content"])),
            )
        }

        // System instruction prepended as first user turn
        val systemInstruction = mapOf(
            "parts" to listOf(mapOf("text" to
                """You are Akiba AI, a friendly and knowledgeable personal finance assistant 
                for Kenyan users. You help users understand their spending, budgets, savings 
                goals, and M-Pesa transactions. Always respond in a helpful, concise, and 
                encouraging tone. When mentioning amounts always use Ksh prefix."""
            ))
        )

        val body = com.google.gson.Gson().toJson(mapOf(
            "system_instruction" to systemInstruction,
            "contents"           to contents,
            "generationConfig"   to mapOf(
                "maxOutputTokens" to 1024,
                "temperature"     to 0.7,
            ),
        ))

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                  "gemini-2.0-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val responseStr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute().body?.string() ?: "" ?: ""
        }

        val json = com.google.gson.JsonParser.parseString(responseStr).asJsonObject
        return json.getAsJsonArray("candidates")
            .get(0).asJsonObject
            .getAsJsonObject("content")
            .getAsJsonArray("parts")
            .get(0).asJsonObject
            .get("text").asString
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    navController: NavHostController,
    viewModel    : AiChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isTyping by viewModel.isTyping.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    val haptic    = LocalHapticFeedback.current
    val gold      = MaterialTheme.akibaColors.gold
    val primary   = MaterialTheme.colorScheme.primary
    val surface   = MaterialTheme.colorScheme.surface

    var input by remember { mutableStateOf("") }

    val suggestions = listOf(
        "Where am I overspending this month?",
        "Can I afford Ksh 5,000 for entertainment this week?",
        "How are my savings goals tracking?",
        "Give me a spending summary for this month.",
        "What is my single biggest expense category?",
    )

    // Scroll to bottom on new message
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty() || isTyping) {
            listState.animateScrollToItem(
                index  = maxOf(0, listState.layoutInfo.totalItemsCount - 1)
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Custom top bar
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(surface.copy(alpha = 0.95f))
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.akibaColors.glassBorder,
                        shape = RoundedCornerShape(0.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back",
                        tint = MaterialTheme.colorScheme.onSurface)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.weight(1f),
                ) {
                    // Gold AI avatar
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(gold, gold.copy(alpha = 0.6f)))),
                    ) {
                        Text("AI", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Akiba AI", fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Your financial assistant", fontFamily = DmSansFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                IconButton(onClick = { viewModel.clearHistory() }) {
                    Icon(Icons.Rounded.MoreVert, "More",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        },
        bottomBar = {
            // Input row
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.akibaColors.glassBorder,
                        shape = RoundedCornerShape(0.dp),
                    )
                    .background(surface.copy(alpha = 0.95f))
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Input field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.akibaColors.glassBorder,
                            RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value           = input,
                        onValueChange   = { input = it },
                        textStyle       = TextStyle(
                            fontFamily = DmSansFontFamily,
                            fontSize   = 15.sp,
                            color      = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush     = SolidColor(primary),
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.sendMessage(input)
                            input = ""
                        }),
                        decorationBox   = { inner ->
                            Box {
                                if (input.isEmpty()) {
                                    Text("Ask me anything...",
                                        fontFamily = DmSansFontFamily, fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                                }
                                inner()
                            }
                        },
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Send button
                val sendAlpha by animateFloatAsState(
                    targetValue   = if (input.isNotEmpty()) 1f else 0.3f,
                    label         = "sendAlpha",
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(48.dp)
                        .graphicsLayer(alpha = sendAlpha)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(primary, primary.copy(0.7f))))
                        .clickable(enabled = input.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.sendMessage(input)
                            input = ""
                        },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state           = listState,
            modifier        = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding  = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Suggestion cards when empty
            if (messages.isEmpty() && !isTyping) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(MaterialTheme.akibaColors.gold,
                                            MaterialTheme.akibaColors.gold.copy(alpha = 0.5f))
                                    )
                                ),
                        ) {
                            Text("AI", fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = Color.White)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Ask me anything", fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                itemsIndexed(suggestions) { index, suggestion ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 80L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter   = fadeIn(tween(300)) + slideInVertically { 20 },
                    ) {
                        GlassCard(
                            elevation = GlassElevation.Flat,
                            onClick   = {
                                input = suggestion
                                viewModel.sendMessage(suggestion)
                                input = ""
                            },
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier              = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(8.dp).clip(CircleShape)
                                            .background(primary),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(suggestion, fontFamily = DmSansFontFamily,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f))
                                }
                                Icon(Icons.Rounded.ChevronRight, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Messages
            items(messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }

            // Typing indicator
            if (isTyping) {
                item { TypingIndicator() }
            }
        }
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────────
@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser  = message.role == "user"
    val primary = MaterialTheme.colorScheme.primary
    val gold    = MaterialTheme.akibaColors.gold
    val surface = MaterialTheme.colorScheme.surface

    val bubbleScale = remember { Animatable(0.85f) }
    LaunchedEffect(message.id) {
        bubbleScale.animateTo(1f, spring(stiffness = 300f, dampingRatio = 0.7f))
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = bubbleScale.value, scaleY = bubbleScale.value),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom,
    ) {
        if (!isUser) {
            // AI avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(gold, gold.copy(alpha = 0.5f)))),
            ) {
                Text("AI", fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier            = Modifier.widthIn(max = 280.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart    = 18.dp,
                            topEnd      = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd   = if (isUser) 4.dp  else 18.dp,
                        )
                    )
                    .background(
                        if (isUser)
                            Brush.horizontalGradient(listOf(primary, primary.copy(alpha = 0.8f)))
                        else
                            Brush.horizontalGradient(listOf(surface, surface))
                    )
                    .border(
                        width = if (isUser) 0.dp else 1.dp,
                        color = MaterialTheme.akibaColors.glassBorder,
                        shape = RoundedCornerShape(
                            topStart    = 18.dp, topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd   = if (isUser) 4.dp  else 18.dp,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text       = message.content,
                    fontFamily = DmSansFontFamily,
                    fontSize   = 15.sp,
                    lineHeight  = 22.sp,
                    color      = if (isUser) Color.White
                                 else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text       = message.timestamp,
                fontFamily = DmSansFontFamily,
                fontSize   = 10.sp,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

// ── Typing indicator ──────────────────────────────────────────────────────────
@Composable
private fun TypingIndicator() {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent    = MaterialTheme.akibaColors.accentGreen
    val dotColors = listOf(primary, secondary, accent)

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier          = Modifier.padding(start = 40.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = 4.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.akibaColors.glassBorder,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = 4.dp, bottomEnd = 18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dotColors.forEachIndexed { index, color ->
                    val infinite = rememberInfiniteTransition(label = "dot$index")
                    val offsetY by infinite.animateFloat(
                        initialValue  = 0f, targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            tween(600, delayMillis = index * 150),
                            RepeatMode.Reverse,
                        ),
                        label = "dotY$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .offset(y = offsetY.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
    }
}
