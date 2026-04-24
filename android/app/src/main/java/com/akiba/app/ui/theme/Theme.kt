package com.akiba.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class AkibaThemeMode { LIGHT, DARK, AUTO }

@Composable
fun AkibaTheme(
    themeMode: AkibaThemeMode = AkibaThemeMode.AUTO,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AkibaThemeMode.DARK  -> true
        AkibaThemeMode.LIGHT -> false
        AkibaThemeMode.AUTO  -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) AkibaDarkColorScheme else AkibaLightColorScheme
    val extraColors = if (isDark) DarkExtraColors      else LightExtraColors

    // 'provides' is the correct infix for CompositionLocalProvider, not 'to'
    CompositionLocalProvider(
        LocalAkibaColors   provides extraColors,
        LocalAkibaSpacing  provides AkibaSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AkibaTypography,
            content     = content,
        )
    }
}

val MaterialTheme.akibaColors: AkibaExtraColors
    @Composable get() = LocalAkibaColors.current
