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

// Obsidian-style palette
val ObsidianBg = Color(0xFF111113)
val ObsidianSurface = Color(0xFF18181C)
val ObsidianSurfaceElevated = Color(0xFF222228)
val ObsidianBorder = Color(0xFF2C2C36)
val ObsidianPurple = Color(0xFF9D7CFF)
val ObsidianPurpleDeep = Color(0xFF7C3AED)
val ObsidianPurpleContainer = Color(0xFF291E3E)
val ObsidianOnPurple = Color(0xFFE2D9FC)
val ObsidianTextPrimary = Color(0xFFEDEDED)
val ObsidianTextSecondary = Color(0xFF9E9EAE)
val ObsidianTextMuted = Color(0xFF666675)

val ObsidianGreen = Color(0xFF4ADE80)
val ObsidianYellow = Color(0xFFFBBF24)
val ObsidianRed = Color(0xFFF87171)
val ObsidianBlue = Color(0xFF60A5FA)

// OLED Black
val OledBg = Color(0xFF000000)
val OledSurface = Color(0xFF08080A)
val OledSurfaceElevated = Color(0xFF121216)
val OledBorder = Color(0xFF202026)

// High Contrast
val HighContrastBg = Color(0xFF000000)
val HighContrastSurface = Color(0xFF0A0A0E)
val HighContrastBorder = Color(0xFF4B4B5A)
val HighContrastText = Color(0xFFFFFFFF)

// Obsidian Light
val LightBg = Color(0xFFF7F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFEFEFF4)
val LightBorder = Color(0xFFDCDCE5)
val LightPrimary = Color(0xFF6D28D9)
val LightTextPrimary = Color(0xFF18181B)
val LightTextSecondary = Color(0xFF52525B)

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
            onPrimary = Color(0xFF120C24),
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

/** Soft, generously rounded geometry so the app feels hand-crafted, not boxy. */
val FioShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Accent choices: returns (primary, primaryContainer) for the current theme. */
fun accentPalette(name: String, light: Boolean): Pair<Color, Color> = when (name) {
    "BLUE" -> if (light) Color(0xFF2563EB) to Color(0xFFDBEAFE) else Color(0xFF60A5FA) to Color(0xFF16263D)
    "TEAL" -> if (light) Color(0xFF0D9488) to Color(0xFFCCFBF1) else Color(0xFF2DD4BF) to Color(0xFF0E2B28)
    "GREEN" -> if (light) Color(0xFF16A34A) to Color(0xFFDCFCE7) else Color(0xFF4ADE80) to Color(0xFF122E1D)
    "AMBER" -> if (light) Color(0xFFD97706) to Color(0xFFFEF3C7) else Color(0xFFFBBF24) to Color(0xFF322609)
    "ROSE" -> if (light) Color(0xFFE11D48) to Color(0xFFFFE4E6) else Color(0xFFFB7185) to Color(0xFF341219)
    "ORANGE" -> if (light) Color(0xFFEA580C) to Color(0xFFFFEDD5) else Color(0xFFFB923C) to Color(0xFF331B0B)
    else -> if (light) Color(0xFF6D28D9) to Color(0xFFEDE9FE) else Color(0xFF9D7CFF) to Color(0xFF291E3E)
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
