package com.akiba.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import com.akiba.app.navigation.Screen
import com.akiba.app.ui.components.common.*
import com.akiba.app.ui.theme.*
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title   : String,
    val subtitle: String,
    val pageNum : String,
)

private val pages = listOf(
    OnboardingPage(
        title    = "All your money, one place",
        subtitle = "Connect M-Pesa, bank accounts and cards. See every shilling in one beautiful dashboard.",
        pageNum  = "01 / 03",
    ),
    OnboardingPage(
        title    = "AI that knows your money",
        subtitle = "Akiba learns your spending patterns and gives you smart nudges before you overspend.",
        pageNum  = "02 / 03",
    ),
    OnboardingPage(
        title    = "Watch your wealth grow",
        subtitle = "Set savings goals, track budgets, and celebrate every milestone on your journey.",
        pageNum  = "03 / 03",
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(navController: NavHostController) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope      = rememberCoroutineScope()
    val primary    = MaterialTheme.colorScheme.primary
    val secondary  = MaterialTheme.colorScheme.secondary
    val background = MaterialTheme.colorScheme.background
    val accent     = MaterialTheme.akibaColors.accentGreen
    val gold       = MaterialTheme.akibaColors.gold

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        AuroraBackground(primary = primary, secondary = secondary, accent = accent, intensity = 0.7f)

        Column(modifier = Modifier.fillMaxSize()) {

            // Skip button
            Box(
                modifier         = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(onClick = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }) {
                        Text("Skip", color = gold, fontFamily = DmSansFontFamily)
                    }
                }
            }

            // Illustration pager
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxWidth().weight(0.5f),
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) +
                        pagerState.currentPageOffsetFraction

                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = pageOffset * -size.width * 0.4f },
                ) {
                    when (page) {
                        0 -> WalletIllustration(primary, secondary, gold)
                        1 -> AiIllustration(primary, secondary, gold)
                        2 -> GrowthIllustration(primary, accent, gold)
                    }
                }
            }

            // Content card
            GlassCard(
                elevation = GlassElevation.Hero,
                modifier  = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(
                    modifier              = Modifier.fillMaxSize(),
                    horizontalAlignment   = Alignment.CenterHorizontally,
                    verticalArrangement   = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text       = pages[pagerState.currentPage].pageNum,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize   = 11.sp,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier   = Modifier.align(Alignment.End),
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = pages[pagerState.currentPage].title,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 24.sp,
                            textAlign  = TextAlign.Center,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text       = pages[pagerState.currentPage].subtitle,
                            fontFamily = DmSansFontFamily,
                            fontSize   = 15.sp,
                            textAlign  = TextAlign.Center,
                            lineHeight  = 24.sp,
                            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Animated pill dots
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(pages.size) { index ->
                                val isActive = pagerState.currentPage == index
                                val width by animateDpAsState(
                                    targetValue   = if (isActive) 32.dp else 8.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                    label         = "dotWidth$index",
                                )
                                val color by animateColorAsState(
                                    targetValue   = if (isActive) primary else primary.copy(alpha = 0.3f),
                                    animationSpec = tween(200),
                                    label         = "dotColor$index",
                                )
                                Box(
                                    modifier = Modifier
                                        .height(8.dp)
                                        .width(width)
                                        .clip(CircleShape)
                                        .background(color),
                                )
                            }
                        }

                        val isLastPage = pagerState.currentPage == pages.size - 1
                        AkibaButton(
                            text     = if (isLastPage) "Get Started" else "Next",
                            variant  = if (isLastPage) AkibaButtonVariant.Success
                                       else AkibaButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick  = {
                                if (isLastPage) {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                    }
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Illustrations ─────────────────────────────────────────────────────────────

@Composable
private fun WalletIllustration(primary: Color, secondary: Color, gold: Color) {
    Canvas(modifier = Modifier.size(240.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(
            color        = primary.copy(alpha = 0.15f),
            topLeft      = Offset(w * 0.2f, h * 0.05f),
            size         = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.75f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f),
        )
        drawRoundRect(
            color        = primary.copy(alpha = 0.5f),
            topLeft      = Offset(w * 0.2f, h * 0.05f),
            size         = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.75f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f),
            style        = Stroke(width = 2f),
        )
        drawCircle(color = secondary.copy(alpha = 0.8f), radius = w * 0.08f,
            center = Offset(w * 0.38f, h * 0.25f))
        drawRoundRect(
            color        = gold.copy(alpha = 0.6f),
            topLeft      = Offset(w * 0.28f, h * 0.38f),
            size         = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
        )
        listOf(0.62f, 0.68f, 0.74f).forEach { yFrac ->
            drawRoundRect(color = primary.copy(alpha = 0.3f),
                topLeft = Offset(w * 0.28f, h * yFrac),
                size    = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.03f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            drawRoundRect(color = gold.copy(alpha = 0.4f),
                topLeft = Offset(w * 0.62f, h * yFrac),
                size    = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.03f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        }
        listOf(Offset(w * 0.1f, h * 0.2f), Offset(w * 0.85f, h * 0.35f)).forEach {
            drawCircle(color = gold.copy(alpha = 0.7f), radius = w * 0.055f, center = it)
        }
    }
}

@Composable
private fun AiIllustration(primary: Color, secondary: Color, gold: Color) {
    val pulse = rememberInfiniteTransition(label = "aiPulse")
    val ring by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "aiRing",
    )
    Canvas(modifier = Modifier.size(240.dp)) {
        val w = size.width; val h = size.height
        val center = Offset(w * 0.5f, h * 0.45f)
        val step = w * 0.15f
        for (i in 0..6) {
            drawLine(color = primary.copy(alpha = 0.1f),
                start = Offset(i * step, 0f), end = Offset(i * step, h), strokeWidth = 1f)
        }
        drawCircle(color = primary.copy(alpha = 0.15f), radius = w * 0.28f * ring, center = center)
        drawCircle(
            brush  = Brush.radialGradient(listOf(primary, secondary), center, w * 0.22f),
            radius = w * 0.22f, center = center,
        )
        listOf(Offset(w*0.15f,h*0.2f), Offset(w*0.85f,h*0.2f),
               Offset(w*0.1f,h*0.7f),  Offset(w*0.9f,h*0.7f)).forEach { node ->
            drawLine(color = primary.copy(alpha = 0.3f), start = center, end = node, strokeWidth = 1.5f)
            drawCircle(color = secondary.copy(alpha = 0.6f), radius = w * 0.045f, center = node)
        }
        drawCircle(color = gold, radius = w * 0.04f, center = Offset(w * 0.5f, h * 0.18f))
    }
}

@Composable
private fun GrowthIllustration(primary: Color, accent: Color, gold: Color) {
    val coinAnim = rememberInfiniteTransition(label = "coins")
    val coinY by coinAnim.animateFloat(
        initialValue = 0f, targetValue = -30f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "coinY",
    )
    Canvas(modifier = Modifier.size(240.dp)) {
        val w = size.width; val h = size.height
        val arrowPath = Path().apply {
            moveTo(w*0.5f, h*0.08f); lineTo(w*0.3f, h*0.32f)
            lineTo(w*0.42f, h*0.32f); lineTo(w*0.42f, h*0.78f)
            lineTo(w*0.58f, h*0.78f); lineTo(w*0.58f, h*0.32f)
            lineTo(w*0.7f, h*0.32f); close()
        }
        drawPath(arrowPath, Brush.verticalGradient(listOf(primary, accent), 0f, h*0.78f))
        drawRoundRect(color = accent.copy(alpha = 0.2f),
            topLeft = Offset(w*0.1f, h*0.82f),
            size    = androidx.compose.ui.geometry.Size(w*0.8f, h*0.06f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
        drawRoundRect(color = accent,
            topLeft = Offset(w*0.1f, h*0.82f),
            size    = androidx.compose.ui.geometry.Size(w*0.64f, h*0.06f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
        listOf(w*0.2f, w*0.5f, w*0.78f).forEachIndexed { i, x ->
            drawCircle(color = gold.copy(alpha = 0.75f), radius = w*0.045f,
                center = Offset(x, h*0.55f + coinY*(1f + i*0.3f)))
        }
    }
}
