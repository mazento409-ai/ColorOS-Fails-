package com.filemanager.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { AUTO, LIGHT, DARK }

data class FmColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val sub: Color,
    val navBg: Color,
    val navSel: Color,
    val divider: Color
)

val DarkFm = FmColors(
    true, Color(0xFF000000), Color(0xFF212121), Color.White, Color(0xFF9A9A9A),
    Color(0xFF1B1B1B), Color(0xFF383838), Color(0xFF2E2E2E)
)
val LightFm = FmColors(
    false, Color(0xFFF2F2F2), Color.White, Color(0xFF111111), Color(0xFF7A7A7A),
    Color.White, Color(0xFFE4E4E4), Color(0xFFE6E6E6)
)

val LocalFm = staticCompositionLocalOf { DarkFm }

// Icon colors: identical in light and dark mode
object IcColor {
    val docs = Color(0xFFFFB300)
    val images = Color(0xFF7B61FF)
    val videos = Color(0xFF6A4CFF)
    val music = Color(0xFFFF4B4B)
    val archives = Color(0xFF2F80FF)
    val apks = Color(0xFF22C55E)
    val camera = Color(0xFF8FA3B8)
    val screens = Color(0xFF5B5BF0)
    val recorder = Color(0xFF2B2B2B)
    val dual = Color(0xFF2DBE60)
    val telegram = Color(0xFF3A3A3C)
    val folder = Color(0xFFFFB300)
}

@Composable
fun FmTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val fm = if (dark) DarkFm else LightFm
    val scheme = if (dark)
        darkColorScheme(
            background = fm.bg, surface = fm.card, onSurface = fm.text,
            onBackground = fm.text, primary = Color(0xFF7C6CFF)
        )
    else
        lightColorScheme(
            background = fm.bg, surface = fm.card, onSurface = fm.text,
            onBackground = fm.text, primary = Color(0xFF5B4CFF)
        )
    CompositionLocalProvider(LocalFm provides fm) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
