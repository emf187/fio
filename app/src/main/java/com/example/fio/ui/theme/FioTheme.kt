package com.example.fio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Dark neutral (Notion-like dark: warm charcoal, hairline borders)
val ObsidianBg = Color(0xFF191919)
val ObsidianSurface = Color(0xFF202020)
val ObsidianSurfaceElevated = Color(0xFF252525)
val ObsidianBorder = Color(0xFF2E2E31)
val ObsidianPurple = Color(0xFF9D7CFF)
val ObsidianPurpleDeep = Color(0xFF7C3AED)
val ObsidianPurpleContainer = Color(0xFF26262B)
val ObsidianOnPurple = Color(0xFFE2D9FC)
val ObsidianTextPrimary = Color(0xFFE6E6E3)
val ObsidianTextSecondary = Color(0xFFA3A29F)
val ObsidianTextMuted = Color(0xFF716F6C)

val ObsidianGreen = Color(0xFF4ADE80)
val ObsidianYellow = Color(0xFFFBBF24)
val ObsidianRed = Color(0xFFF87171)
val ObsidianBlue = Color(0xFF60A5FA)

// OLED Black
val OledBg = Color(0xFF000000)
val OledSurface = Color(0xFF0C0C0C)
val OledSurfaceElevated = Color(0xFF161616)
val OledBorder = Color(0xFF242426)

// High Contrast
val HighContrastBg = Color(0xFF000000)
val HighContrastSurface = Color(0xFF0A0A0E)
val HighContrastBorder = Color(0xFF4B4B5A)
val HighContrastText = Color(0xFFFFFFFF)

// Light neutral (Notion-like light: white page, soft gray blocks)
val LightBg = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF7F7F5)
val LightSurfaceElevated = Color(0xFFEDEDEB)
val LightBorder = Color(0xFFE7E7E4)
val LightPrimary = Color(0xFF6D28D9)
val LightTextPrimary = Color(0xFF34322E)
val LightTextSecondary = Color(0xFF6F6E6A)

data class FioCustomColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primary: Color,
    val primaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color
)

val LocalFioColors = staticCompositionLocalOf {
    FioCustomColors(
        background = ObsidianBg,
        surface = ObsidianSurface,
        surfaceElevated = ObsidianSurfaceElevated,
        border = ObsidianBorder,
        primary = ObsidianPurple,
        primaryContainer = ObsidianPurpleContainer,
        textPrimary = ObsidianTextPrimary,
        textSecondary = ObsidianTextSecondary,
        textMuted = ObsidianTextMuted,
        success = ObsidianGreen,
        warning = ObsidianYellow,
        error = ObsidianRed,
        info = ObsidianBlue
    )
}

val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun FioTheme(
    themeName: String = "OBSIDIAN_PURPLE",
    accentColor: String = "PURPLE",
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val customColors = when (themeName) {
        "OLED_BLACK" -> FioCustomColors(
            background = OledBg,
            surface = OledSurface,
            surfaceElevated = OledSurfaceElevated,
            border = OledBorder,
            primary = ObsidianPurple,
            primaryContainer = ObsidianPurpleContainer,
            textPrimary = ObsidianTextPrimary,
            textSecondary = ObsidianTextSecondary,
            textMuted = ObsidianTextMuted,
            success = ObsidianGreen,
            warning = ObsidianYellow,
            error = ObsidianRed,
            info = ObsidianBlue
        )
        "HIGH_CONTRAST" -> FioCustomColors(
            background = HighContrastBg,
            surface = HighContrastSurface,
            surfaceElevated = Color(0xFF181820),
            border = HighContrastBorder,
            primary = Color(0xFFC084FC),
            primaryContainer = Color(0xFF3B1E63),
            textPrimary = HighContrastText,
            textSecondary = Color(0xFFD4D4D8),
            textMuted = Color(0xFFA1A1AA),
            success = Color(0xFF86EFAC),
            warning = Color(0xFFFDE047),
            error = Color(0xFFFCA5A5),
            info = Color(0xFF93C5FD)
        )
        "OBSIDIAN_LIGHT" -> FioCustomColors(
            background = LightBg,
            surface = LightSurface,
            surfaceElevated = LightSurfaceElevated,
            border = LightBorder,
            primary = LightPrimary,
            primaryContainer = Color(0xFFEDE9FE),
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textMuted = Color(0xFF71717A),
            success = Color(0xFF16A34A),
            warning = Color(0xFFD97706),
            error = Color(0xFFDC2626),
            info = Color(0xFF2563EB)
        )
        else -> FioCustomColors(
            background = ObsidianBg,
            surface = ObsidianSurface,
            surfaceElevated = ObsidianSurfaceElevated,
            border = ObsidianBorder,
            primary = ObsidianPurple,
            primaryContainer = ObsidianPurpleContainer,
            textPrimary = ObsidianTextPrimary,
            textSecondary = ObsidianTextSecondary,
            textMuted = ObsidianTextMuted,
            success = ObsidianGreen,
            warning = ObsidianYellow,
            error = ObsidianRed,
            info = ObsidianBlue
        )
    }

    val isLight = themeName == "OBSIDIAN_LIGHT"
    val (accentPrimary, accentContainer) = accentPalette(accentColor, isLight)
    val themedColors = customColors.copy(
        primary = accentPrimary,
        primaryContainer = accentContainer
    )
    val colorScheme = if (isLight) {
        lightColorScheme(
            background = themedColors.background,
            surface = themedColors.surface,
            primary = themedColors.primary,
            onPrimary = Color.White,
            onBackground = themedColors.textPrimary,
            onSurface = themedColors.textPrimary
        )
    } else {
        darkColorScheme(
            background = themedColors.background,
            surface = themedColors.surface,
            primary = themedColors.primary,
            onPrimary = Color(0xFF191919),
            onBackground = themedColors.textPrimary,
            onSurface = themedColors.textPrimary
        )
    }

    CompositionLocalProvider(
        LocalFioColors provides themedColors,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = FioShapes,
            content = content
        )
    }
}

/** Notion-style geometry: crisp but never boxy — soft corners, layered by whitespace. */
val FioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** Accent choices: returns (primary, primaryContainer) for the current theme. */
fun accentPalette(name: String, light: Boolean): Pair<Color, Color> = when (name) {
    "BLUE" -> if (light) Color(0xFF2563EB) to Color(0xFFE3EFFC) else Color(0xFF71A8F5) to Color(0xFF1B2530)
    "TEAL" -> if (light) Color(0xFF0D9488) to Color(0xFFDFF5F2) else Color(0xFF3FC2B6) to Color(0xFF142826)
    "GREEN" -> if (light) Color(0xFF16A34A) to Color(0xFFE4F6EA) else Color(0xFF54C980) to Color(0xFF17251B)
    "AMBER" -> if (light) Color(0xFFD97706) to Color(0xFFFAF1DE) else Color(0xFFE7BC4E) to Color(0xFF282316)
    "ROSE" -> if (light) Color(0xFFE11D48) to Color(0xFFFAE9ED) else Color(0xFFEA7F95) to Color(0xFF2A1A1E)
    "ORANGE" -> if (light) Color(0xFFEA580C) to Color(0xFFFAEDE2) else Color(0xFFEF9755) to Color(0xFF28201A)
    else -> if (light) Color(0xFF6D28D9) to Color(0xFFEEE9F8) else Color(0xFF9D7CFF) to Color(0xFF25232D)
}

/** Preview colours for the accent picker in Settings. */
fun accentSwatch(name: String): Color = when (name) {
    "BLUE" -> Color(0xFF60A5FA)
    "TEAL" -> Color(0xFF2DD4BF)
    "GREEN" -> Color(0xFF4ADE80)
    "AMBER" -> Color(0xFFFBBF24)
    "ROSE" -> Color(0xFFFB7185)
    "ORANGE" -> Color(0xFFFB923C)
    else -> Color(0xFF9D7CFF)
}
