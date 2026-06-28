package com.omnisearch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class FluentColors(
    val pageBg: Color,
    val panelBg: Color,
    val surfaceBg: Color,
    val accent: Color,
    val onAccent: Color,
    val textColor: Color,
    val textMuted: Color,
    val panelBorder: Color,
    val connectedText: Color,
    val dangerText: Color,
    val isDark: Boolean
)

@Stable
class FluentDims(
    val surfaceRadius: Dp = 12.dp,
    val controlRadius: Dp = 8.dp,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 12.dp,
    val paddingLarge: Dp = 16.dp,
    val paddingExtraLarge: Dp = 20.dp
)

val LightFluentColors = FluentColors(
    pageBg = Color(0xFFF3F3F3),
    panelBg = Color(0xF2F3F3F3), // 95% opacity
    surfaceBg = Color(0xD9FFFFFF), // 85% opacity
    accent = Color(0xFF0078D4),
    onAccent = Color.White,
    textColor = Color(0xFF1A1A1A),
    textMuted = Color(0xFF737373),
    panelBorder = Color(0x14000000), // 8% opacity
    connectedText = Color(0xFF0F6C0F),
    dangerText = Color(0xFFC42B1C),
    isDark = false
)

val DarkFluentColors = FluentColors(
    pageBg = Color(0xFF010101),
    panelBg = Color(0xFF171717),
    surfaceBg = Color(0xFF171717),
    accent = Color(0xFF60CDFF),
    onAccent = Color(0xFF010101),
    textColor = Color(0xFFF3F3F3),
    textMuted = Color(0x80FFFFFF), // 50% opacity
    panelBorder = Color(0x14FFFFFF), // 8% opacity
    connectedText = Color(0xFF6AC46A),
    dangerText = Color(0xFFFF5A46),
    isDark = true
)

val LightOceanColors = FluentColors(
    pageBg = Color(0xFFF0F4F8),
    panelBg = Color(0xFFE1EBF5),
    surfaceBg = Color(0xFFFFFFFF),
    accent = Color(0xFF1D4ED8),
    onAccent = Color.White,
    textColor = Color(0xFF0F172A),
    textMuted = Color(0xFF64748B),
    panelBorder = Color(0x141D4ED8),
    connectedText = Color(0xFF0F6C0F),
    dangerText = Color(0xFFC42B1C),
    isDark = false
)

val DarkOceanColors = FluentColors(
    pageBg = Color(0xFF05080E),
    panelBg = Color(0xFF0E1626),
    surfaceBg = Color(0xFF0E1626),
    accent = Color(0xFF3B82F6),
    onAccent = Color.White,
    textColor = Color(0xFFE2E8F0),
    textMuted = Color(0x80E2E8F0),
    panelBorder = Color(0x143B82F6),
    connectedText = Color(0xFF6AC46A),
    dangerText = Color(0xFFFF5A46),
    isDark = true
)

val LightForestColors = FluentColors(
    pageBg = Color(0xFFF2F7F4),
    panelBg = Color(0xFFE3EDE7),
    surfaceBg = Color(0xFFFFFFFF),
    accent = Color(0xFF047857),
    onAccent = Color.White,
    textColor = Color(0xFF062F22),
    textMuted = Color(0xFF507A6D),
    panelBorder = Color(0x14047857),
    connectedText = Color(0xFF0F6C0F),
    dangerText = Color(0xFFC42B1C),
    isDark = false
)

val DarkForestColors = FluentColors(
    pageBg = Color(0xFF030705),
    panelBg = Color(0xFF0C1812),
    surfaceBg = Color(0xFF0C1812),
    accent = Color(0xFF10B981),
    onAccent = Color(0xFF030705),
    textColor = Color(0xFFECFDF5),
    textMuted = Color(0x80ECFDF5),
    panelBorder = Color(0x1410B981),
    connectedText = Color(0xFF6AC46A),
    dangerText = Color(0xFFFF5A46),
    isDark = true
)

val LightSunsetColors = FluentColors(
    pageBg = Color(0xFFFAF7F2),
    panelBg = Color(0xFFF4EDE2),
    surfaceBg = Color(0xFFFFFFFF),
    accent = Color(0xFFD97706),
    onAccent = Color.White,
    textColor = Color(0xFF2D1E05),
    textMuted = Color(0xFF7A6548),
    panelBorder = Color(0x14D97706),
    connectedText = Color(0xFF0F6C0F),
    dangerText = Color(0xFFC42B1C),
    isDark = false
)

val DarkSunsetColors = FluentColors(
    pageBg = Color(0xFF0C0702),
    panelBg = Color(0xFF1F1307),
    surfaceBg = Color(0xFF1F1307),
    accent = Color(0xFFF59E0B),
    onAccent = Color(0xFF0C0702),
    textColor = Color(0xFFFFF7ED),
    textMuted = Color(0x80FFF7ED),
    panelBorder = Color(0x14F59E0B),
    connectedText = Color(0xFF6AC46A),
    dangerText = Color(0xFFFF5A46),
    isDark = true
)

val LightLavenderColors = FluentColors(
    pageBg = Color(0xFFF8F7FC),
    panelBg = Color(0xFFECEAF4),
    surfaceBg = Color(0xFFFFFFFF),
    accent = Color(0xFF7C3AED),
    onAccent = Color.White,
    textColor = Color(0xFF1F0D3D),
    textMuted = Color(0xFF63567D),
    panelBorder = Color(0x147C3AED),
    connectedText = Color(0xFF0F6C0F),
    dangerText = Color(0xFFC42B1C),
    isDark = false
)

val DarkLavenderColors = FluentColors(
    pageBg = Color(0xFF07030F),
    panelBg = Color(0xFF130B24),
    surfaceBg = Color(0xFF130B24),
    accent = Color(0xFFA78BFA),
    onAccent = Color(0xFF07030F),
    textColor = Color(0xFFF5F3FF),
    textMuted = Color(0x80F5F3FF),
    panelBorder = Color(0x14A78BFA),
    connectedText = Color(0xFF6AC46A),
    dangerText = Color(0xFFFF5A46),
    isDark = true
)

val LightRoseColors = FluentColors(
    pageBg = Color(0xFFFDF6F7),
    panelBg = Color(0xFFF6E4E7),
    surfaceBg = Color(0xFFFFFFFF),
    accent = Color(0xFFE11D48),
    onAccent = Color.White,
    textColor = Color(0xFF3F0712),
    textMuted = Color(0xFF88535E),
    panelBorder = Color(0x14E11D48),
    connectedText = Color(0xFF0F6C0F),
    dangerText = Color(0xFFC42B1C),
    isDark = false
)

val DarkRoseColors = FluentColors(
    pageBg = Color(0xFF0F0205),
    panelBg = Color(0xFF240C12),
    surfaceBg = Color(0xFF240C12),
    accent = Color(0xFFFB7185),
    onAccent = Color(0xFF0F0205),
    textColor = Color(0xFFFFF1F2),
    textMuted = Color(0x80FFF1F2),
    panelBorder = Color(0x14FB7185),
    connectedText = Color(0xFF6AC46A),
    dangerText = Color(0xFFFF5A46),
    isDark = true
)

val LocalFluentColors = staticCompositionLocalOf { LightFluentColors }
val LocalFluentDims = staticCompositionLocalOf { FluentDims() }

@Composable
fun OmniSearchTheme(
    themeMode: String = "SYSTEM",
    themeColor: String = "SLATE",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    val colors = when (themeColor) {
        "OCEAN" -> if (isDark) DarkOceanColors else LightOceanColors
        "FOREST" -> if (isDark) DarkForestColors else LightForestColors
        "SUNSET" -> if (isDark) DarkSunsetColors else LightSunsetColors
        "LAVENDER" -> if (isDark) DarkLavenderColors else LightLavenderColors
        "ROSE" -> if (isDark) DarkRoseColors else LightRoseColors
        else -> if (isDark) DarkFluentColors else LightFluentColors
    }
    val dims = FluentDims()

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                windowInsetsController.isAppearanceLightStatusBars = !isDark
                windowInsetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val colorScheme = if (isDark) {
        androidx.compose.material3.darkColorScheme(
            background = colors.pageBg,
            surface = colors.surfaceBg,
            primary = colors.accent,
            onBackground = colors.textColor,
            onSurface = colors.textColor
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            background = colors.pageBg,
            surface = colors.surfaceBg,
            primary = colors.accent,
            onBackground = colors.textColor,
            onSurface = colors.textColor
        )
    }

    CompositionLocalProvider(
        LocalFluentColors provides colors,
        LocalFluentDims provides dims
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

object FluentTheme {
    val colors: FluentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFluentColors.current

    val dims: FluentDims
        @Composable
        @ReadOnlyComposable
        get() = LocalFluentDims.current
}
