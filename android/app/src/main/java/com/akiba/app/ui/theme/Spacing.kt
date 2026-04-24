package com.akiba.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AkibaSpacing(
    val xxs: Dp = 2.dp,
    val xs : Dp = 4.dp,
    val sm : Dp = 8.dp,
    val md : Dp = 16.dp,
    val lg : Dp = 24.dp,
    val xl : Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl:Dp = 64.dp,
)

val LocalAkibaSpacing = compositionLocalOf { AkibaSpacing() }

// Access spacing anywhere via MaterialTheme.spacing.md
val MaterialTheme.spacing: AkibaSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalAkibaSpacing.current
