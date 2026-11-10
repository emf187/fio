package com.example.fio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Fio design system, v2 ("Midnight & Paper").
 *
 * Reference language: near-black canvas, white type, one mint signal, soft
 * tonal surfaces instead of borders, generous spacing, large quiet headings.
 * The light ("Paper") side keeps the same bones on warm paper white.
 *
 * Colour roles (better-colors): one neutral ramp, one accent ramp, status ramps.
 *  - primary  = solid fill for the one primary action (deep teal; white-safe)
 *  - accent   = the mint signal: progress, selection, links, active markers
 *  - textPrimary / textSecondary / textMuted  = the three text jobs only
 *  - surface / surfaceElevated / border       = raised areas and hairlines
 * Components must never invent colours outside these roles.
 */

/** Bundled Inter (SIL OFL). Numerals stay unambiguous across device skins. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Book/reading voice. Android's serif (Noto Serif) — no extra APK weight. */
val FioSerif = FontFamily.Serif

private fun fioStyle(
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    tracking: Double = 0.0,
    family: FontFamily = InterFamily
) = TextStyle(
    fontFamily = family,
    fontSize = size,
    fontWeight = weight,
    lineHeight = lineHeight,
    letterSpacing = tracking.sp
)

/** Inter across the Material type scale; tighter tracking, calmer weights. */
private val FioTypography = Typography(
    displayLarge = fioStyle(48.sp, FontWeight.SemiBold, 54.sp, -1.2),
    displayMedium = fioStyle(40.sp, FontWeight.SemiBold, 46.sp, -1.0),
    displaySmall = fioStyle(34.sp, FontWeight.SemiBold, 40.sp, -0.8),
    headlineLarge = fioStyle(30.sp, FontWeight.SemiBold, 36.sp, -0.6),
    headlineMedium = fioStyle(26.sp, FontWeight.SemiBold, 32.sp, -0.5),
    headlineSmall = fioStyle(22.sp, FontWeight.SemiBold, 28.sp, -0.4),
    titleLarge = fioStyle(20.sp, FontWeight.SemiBold, 26.sp, -0.3),
    titleMedium = fioStyle(16.sp, FontWeight.SemiBold, 22.sp, -0.1),
    titleSmall = fioStyle(14.sp, FontWeight.Medium, 20.sp, 0.0),
    bodyLarge = fioStyle(16.sp, FontWeight.Normal, 24.sp, 0.0),
    bodyMedium = fioStyle(14.5.sp, FontWeight.Normal, 21.sp, 0.0),
    bodySmall = fioStyle(12.5.sp, FontWeight.Normal, 18.sp, 0.0),
    labelLarge = fioStyle(14.sp, FontWeight.Medium, 20.sp, 0.0),
    labelMedium = fioStyle(12.sp, FontWeight.Medium, 16.sp, 0.0),
    labelSmall = fioStyle(11.sp, FontWeight.Medium, 15.sp, 0.0)
)

/**
 * Fio's own type ramp — used by the redesigned screens and shared components.
 * Roles, not sizes picked by eye: display (greetings), title (screen heads),
 * section (group heads), body, small, caption, and a serif reading voice.
 */
object FioType {
    val display = fioStyle(30.sp, FontWeight.SemiBold, 36.sp, -0.7)
    val displaySmall = fioStyle(25.sp, FontWeight.SemiBold, 31.sp, -0.5)
    val title = fioStyle(21.sp, FontWeight.SemiBold, 27.sp, -0.35)
    val section = fioStyle(17.sp, FontWeight.SemiBold, 23.sp, -0.2)
    val body = fioStyle(15.sp, FontWeight.Normal, 22.sp, 0.0)
    val bodyMedium = fioStyle(14.sp, FontWeight.Normal, 20.sp, 0.0)
    val small = fioStyle(12.5.sp, FontWeight.Normal, 18.sp, 0.0)
    val label = fioStyle(13.sp, FontWeight.Medium, 18.sp, 0.0)
    val caption = fioStyle(11.sp, FontWeight.Normal, 15.sp, 0.0)
    val reading = fioStyle(17.sp, FontWeight.Normal, 28.sp, 0.15, FioSerif)
    val readingLarge = fioStyle(19.sp, FontWeight.Normal, 31.sp, 0.2, FioSerif)
}

// ---------------------------------------------------------------- Midnight
// Near-black canvas (never pure #000), quiet charcoal surfaces, mint signal.
val MidnightBg = Color(0xFF0B0B0C)
val MidnightSurface = Color(0xFF141416)
val MidnightSurfaceElevated = Color(0xFF1C1C1F)
val MidnightBorder = Color(0xFF27272B)
val MidnightPrimary = Color(0xFF0F766E)      // solid action fill (white-safe)
val MidnightAccent = Color(0xFF5EEAD4)       // mint signal on dark
val MidnightContainer = Color(0xFF123A35)
val MidnightOnPrimary = Color(0xFFFFFFFF)
val MidnightTextPrimary = Color(0xFFF3F3F1)
val MidnightTextSecondary = Color(0xFFA7A7AC)
val MidnightTextMuted = Color(0xFF74747A)

val MidnightSuccess = Color(0xFF4ADE80)
val MidnightWarning = Color(0xFFFBBF24)
val MidnightError = Color(0xFFF87171)
val MidnightInfo = Color(0xFF7DD3FC)

// ---------------------------------------------------------------- True black
val OledBg = Color(0xFF000000)
val OledSurface = Color(0xFF0B0B0C)
val OledSurfaceElevated = Color(0xFF151517)
val OledBorder = Color(0xFF232326)

// ---------------------------------------------------------------- Paper (light)
val PaperBg = Color(0xFFFAFAF7)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceElevated = Color(0xFFF2F2EC)
val PaperBorder = Color(0xFFE6E6DE)
val PaperPrimary = Color(0xFF0F766E)         // deep teal; white-safe on light
val PaperAccent = Color(0xFF0F766E)
val PaperContainer = Color(0xFFDCF3EE)
val PaperTextPrimary = Color(0xFF1B1B19)
val PaperTextSecondary = Color(0xFF5D5D57)
val PaperTextMuted = Color(0xFF8A8A82)

val PaperSuccess = Color(0xFF15803D)
val PaperWarning = Color(0xFFB45309)
val PaperError = Color(0xFFB91C1C)
val PaperInfo = Color(0xFF0369A1)

// ---------------------------------------------------------------- High contrast
val HighContrastBg = Color(0xFF000000)
val HighContrastSurface = Color(0xFF0A0A0C)
val HighContrastBorder = Color(0xFF4B4B55)
val HighContrastText = Color(0xFFFFFFFF)

/**
 * Semantic colour tokens consumed by every screen. `primary` is a fill, `accent`
 * is the signal colour used for text/icons/progress — they are deliberately
 * different values so both stay legible.
 */
data class FioCustomColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primary: Color,
    val primaryContainer: Color,
    val accent: Color,
    val onPrimary: Color,
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
        background = MidnightBg,
        surface = MidnightSurface,
        surfaceElevated = MidnightSurfaceElevated,
        border = MidnightBorder,
        primary = MidnightPrimary,
        primaryContainer = MidnightContainer,
        accent = MidnightAccent,
        onPrimary = MidnightOnPrimary,
        textPrimary = MidnightTextPrimary,
        textSecondary = MidnightTextSecondary,
        textMuted = MidnightTextMuted,
        success = MidnightSuccess,
        warning = MidnightWarning,
        error = MidnightError,
        info = MidnightInfo
    )
}

val LocalReduceMotion = staticCompositionLocalOf { false }

// ---------------------------------------------------------------- Slate (dark)
val SlateBg = Color(0xFF0C0F12)
val SlateSurface = Color(0xFF141A1F)
val SlateSurfaceElevated = Color(0xFF1B232B)
val SlateBorder = Color(0xFF27323C)
val SlatePrimary = Color(0xFF0E7490)
val SlateAccent = Color(0xFF67E8F9)
val SlateContainer = Color(0xFF10303B)
val SlateTextPrimary = Color(0xFFEDF2F5)
val SlateTextSecondary = Color(0xFF9EACB6)
val SlateTextMuted = Color(0xFF6F7C86)

// ---------------------------------------------------------------- Plum (dark)
val PlumBg = Color(0xFF0E0A10)
val PlumSurface = Color(0xFF17121B)
val PlumSurfaceElevated = Color(0xFF201826)
val PlumBorder = Color(0xFF2D2434)
val PlumPrimary = Color(0xFF86198F)
val PlumAccent = Color(0xFFF0ABFC)
val PlumContainer = Color(0xFF3A1440)
val PlumTextPrimary = Color(0xFFF5EFF7)
val PlumTextSecondary = Color(0xFFB6A9BF)
val PlumTextMuted = Color(0xFF80728A)

// ---------------------------------------------------------------- Forest (dark)
val ForestBg = Color(0xFF0A0F0B)
val ForestSurface = Color(0xFF121813)
val ForestSurfaceElevated = Color(0xFF19211A)
val ForestBorder = Color(0xFF253026)
val ForestPrimary = Color(0xFF166534)
val ForestAccent = Color(0xFF86EFAC)
val ForestContainer = Color(0xFF14351D)
val ForestTextPrimary = Color(0xFFEFF4EE)
val ForestTextSecondary = Color(0xFFA7B3A6)
val ForestTextMuted = Color(0xFF728072)

// ---------------------------------------------------------------- Sand (light)
val SandBg = Color(0xFFF7F3EA)
val SandSurface = Color(0xFFFFFDF8)
val SandSurfaceElevated = Color(0xFFEFE9DC)
val SandBorder = Color(0xFFE2DAC9)
val SandPrimary = Color(0xFF4F5D2C)
val SandAccent = Color(0xFF556B2F)
val SandContainer = Color(0xFFE9ECD7)
val SandTextPrimary = Color(0xFF2A241B)
val SandTextSecondary = Color(0xFF6B6152)
val SandTextMuted = Color(0xFF948874)

// ---------------------------------------------------------------- Mist (light)
val MistBg = Color(0xFFF5F7FA)
val MistSurface = Color(0xFFFFFFFF)
val MistSurfaceElevated = Color(0xFFECF0F5)
val MistBorder = Color(0xFFDCE2EA)
val MistPrimary = Color(0xFF1E3A8A)
val MistAccent = Color(0xFF1D4ED8)
val MistContainer = Color(0xFFE1E8F7)
val MistTextPrimary = Color(0xFF171A1F)
val MistTextSecondary = Color(0xFF5B6470)
val MistTextMuted = Color(0xFF8A929D)

/** Base canvases; each theme is a complete, contrast-checked neutral ramp. */
private fun basePalette(themeName: String): FioCustomColors = when (themeName) {
    "OLED_BLACK" -> FioCustomColors(
        background = OledBg,
        surface = OledSurface,
        surfaceElevated = OledSurfaceElevated,
        border = OledBorder,
        primary = MidnightPrimary,
        primaryContainer = MidnightContainer,
        accent = MidnightAccent,
        onPrimary = MidnightOnPrimary,
        textPrimary = MidnightTextPrimary,
        textSecondary = MidnightTextSecondary,
        textMuted = MidnightTextMuted,
        success = MidnightSuccess,
        warning = MidnightWarning,
        error = MidnightError,
        info = MidnightInfo
    )
    "HIGH_CONTRAST" -> FioCustomColors(
        background = HighContrastBg,
        surface = HighContrastSurface,
        surfaceElevated = Color(0xFF191920),
        border = HighContrastBorder,
        primary = Color(0xFF0F766E),
        primaryContainer = Color(0xFF103F39),
        accent = Color(0xFF5EEAD4),
        onPrimary = Color.White,
        textPrimary = HighContrastText,
        textSecondary = Color(0xFFD4D4D8),
        textMuted = Color(0xFFA1A1AA),
        success = Color(0xFF86EFAC),
        warning = Color(0xFFFDE047),
        error = Color(0xFFFCA5A5),
        info = Color(0xFF93C5FD)
    )
    "SLATE" -> FioCustomColors(
        background = SlateBg, surface = SlateSurface, surfaceElevated = SlateSurfaceElevated,
        border = SlateBorder, primary = SlatePrimary, primaryContainer = SlateContainer,
        accent = SlateAccent, onPrimary = Color.White,
        textPrimary = SlateTextPrimary, textSecondary = SlateTextSecondary, textMuted = SlateTextMuted,
        success = MidnightSuccess, warning = MidnightWarning, error = MidnightError, info = MidnightInfo
    )
    "PLUM" -> FioCustomColors(
        background = PlumBg, surface = PlumSurface, surfaceElevated = PlumSurfaceElevated,
        border = PlumBorder, primary = PlumPrimary, primaryContainer = PlumContainer,
        accent = PlumAccent, onPrimary = Color.White,
        textPrimary = PlumTextPrimary, textSecondary = PlumTextSecondary, textMuted = PlumTextMuted,
        success = MidnightSuccess, warning = MidnightWarning, error = MidnightError, info = MidnightInfo
    )
    "FOREST" -> FioCustomColors(
        background = ForestBg, surface = ForestSurface, surfaceElevated = ForestSurfaceElevated,
        border = ForestBorder, primary = ForestPrimary, primaryContainer = ForestContainer,
        accent = ForestAccent, onPrimary = Color.White,
        textPrimary = ForestTextPrimary, textSecondary = ForestTextSecondary, textMuted = ForestTextMuted,
        success = MidnightSuccess, warning = MidnightWarning, error = MidnightError, info = MidnightInfo
    )
    "OBSIDIAN_LIGHT", "PAPER" -> FioCustomColors(
        background = PaperBg, surface = PaperSurface, surfaceElevated = PaperSurfaceElevated,
        border = PaperBorder, primary = PaperPrimary, primaryContainer = PaperContainer,
        accent = PaperAccent, onPrimary = Color.White,
        textPrimary = PaperTextPrimary, textSecondary = PaperTextSecondary, textMuted = PaperTextMuted,
        success = PaperSuccess, warning = PaperWarning, error = PaperError, info = PaperInfo
    )
    "SAND" -> FioCustomColors(
        background = SandBg, surface = SandSurface, surfaceElevated = SandSurfaceElevated,
        border = SandBorder, primary = SandPrimary, primaryContainer = SandContainer,
        accent = SandAccent, onPrimary = Color.White,
        textPrimary = SandTextPrimary, textSecondary = SandTextSecondary, textMuted = SandTextMuted,
        success = PaperSuccess, warning = PaperWarning, error = PaperError, info = PaperInfo
    )
    "MIST" -> FioCustomColors(
        background = MistBg, surface = MistSurface, surfaceElevated = MistSurfaceElevated,
        border = MistBorder, primary = MistPrimary, primaryContainer = MistContainer,
        accent = MistAccent, onPrimary = Color.White,
        textPrimary = MistTextPrimary, textSecondary = MistTextSecondary, textMuted = MistTextMuted,
        success = PaperSuccess, warning = PaperWarning, error = PaperError, info = PaperInfo
    )
    else -> FioCustomColors(
        background = MidnightBg,
        surface = MidnightSurface,
        surfaceElevated = MidnightSurfaceElevated,
        border = MidnightBorder,
        primary = MidnightPrimary,
        primaryContainer = MidnightContainer,
        accent = MidnightAccent,
        onPrimary = MidnightOnPrimary,
        textPrimary = MidnightTextPrimary,
        textSecondary = MidnightTextSecondary,
        textMuted = MidnightTextMuted,
        success = MidnightSuccess,
        warning = MidnightWarning,
        error = MidnightError,
        info = MidnightInfo
    )
}

@Composable
fun FioTheme(
    themeName: String = "OBSIDIAN_PURPLE",
    accentColor: String = "MINT",
    reduceMotion: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val base = basePalette(themeName)

    val isLight = themeName == "OBSIDIAN_LIGHT" || themeName == "PAPER" ||
        themeName == "SAND" || themeName == "MIST"
    // "MINT"/"PURPLE" mean "use this theme's own signature accent", so choosing a
    // theme actually changes the signal colour; an explicit accent overrides it.
    val useThemeAccent = accentColor == "MINT" || accentColor == "PURPLE"
    val accentPair = accentPalette(accentColor, isLight)
    val themedColors = if (useThemeAccent) base else base.copy(
        primary = accentPair.first,
        primaryContainer = accentPair.second,
        accent = accentPair.third
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
            onPrimary = Color.White,
            onBackground = themedColors.textPrimary,
            onSurface = themedColors.textPrimary
        )
    }

    // Fixed type ramp: the host's own font-scale no longer breaks Fio's
    // buttons/headings — but the in-app Text size setting multiplies on top of
    // the device default so it actually changes every screen.
    val platformDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalFioColors provides themedColors,
        LocalReduceMotion provides reduceMotion,
        LocalDensity provides Density(
            density = platformDensity.density,
            fontScale = (platformDensity.fontScale * fontScale.coerceIn(0.8f, 1.4f))
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = FioShapes,
            typography = FioTypography,
            content = content
        )
    }
}

/** Quiet geometry: generous radii on cards, pills for chips and actions. */
val FioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Accent families. Returns (fill, container, signal):
 *  - fill: white-text-safe solid (buttons)
 *  - container: quiet tinted surface
 *  - signal: accent used for text/icons/progress on the canvas
 */
fun accentPalette(name: String, light: Boolean): Triple<Color, Color, Color> = when (name) {
    "BLUE" -> if (light) Triple(Color(0xFF1D4ED8), Color(0xFFE0EBFD), Color(0xFF1D4ED8))
    else Triple(Color(0xFF1E5AA8), Color(0xFF152A42), Color(0xFF7DD3FC))
    "TEAL", "MINT", "PURPLE" -> if (light) Triple(Color(0xFF0F766E), Color(0xFFDCF3EE), Color(0xFF0F766E))
    else Triple(MidnightPrimary, MidnightContainer, MidnightAccent)
    "GREEN" -> if (light) Triple(Color(0xFF15803D), Color(0xFFE3F5E8), Color(0xFF15803D))
    else Triple(Color(0xFF166534), Color(0xFF14281B), Color(0xFF4ADE80))
    "AMBER" -> if (light) Triple(Color(0xFFB45309), Color(0xFFFAEFDD), Color(0xFFB45309))
    else Triple(Color(0xFF9A5B11), Color(0xFF2A2113), Color(0xFFFBBF24))
    "ROSE" -> if (light) Triple(Color(0xFFBE123C), Color(0xFFFCE8ED), Color(0xFFBE123C))
    else Triple(Color(0xFF9F1239), Color(0xFF2A151C), Color(0xFFFB7185))
    "ORANGE" -> if (light) Triple(Color(0xFFC2410C), Color(0xFFFBEDE3), Color(0xFFC2410C))
    else Triple(Color(0xFF9A3D0C), Color(0xFF2A1C13), Color(0xFFFB923C))
    else -> if (light) Triple(Color(0xFF0F766E), Color(0xFFDCF3EE), Color(0xFF0F766E))
    else Triple(MidnightPrimary, MidnightContainer, MidnightAccent)
}

/** Preview colours for the theme picker: (canvas, accent, text). */
fun themePreview(themeId: String): Triple<Color, Color, Color> {
    val p = basePalette(themeId)
    return Triple(p.background, p.accent, p.textPrimary)
}

/** Preview colours for the accent picker in Settings. */
fun accentSwatch(name: String): Color = when (name) {
    "BLUE" -> Color(0xFF60A5FA)
    "TEAL", "MINT", "PURPLE" -> Color(0xFF5EEAD4)
    "GREEN" -> Color(0xFF4ADE80)
    "AMBER" -> Color(0xFFFBBF24)
    "ROSE" -> Color(0xFFFB7185)
    "ORANGE" -> Color(0xFFFB923C)
    else -> Color(0xFF5EEAD4)
}
