package com.example.fio.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.example.fio.ui.theme.LocalFioColors
import ru.noties.jlatexmath.JLatexMathDrawable

/*
 * Real LaTeX typesetting bridge (JLaTeXMath, offline).
 *
 * Everything containing \frac, \sum, matrices, Greek etc. is rendered with a
 * true TeX engine to a bitmap — no more character truncation or half-rendered
 * glyphs. Simple prose keeps the fast text renderer; anything the TeX engine
 * cannot parse (e.g. mid-stream) falls back to the legacy renderer.
 */

/** Global result cache — one bitmap per (formula, size, color). Bounded. */
private val latexBitmapCache = object : LruCache<String, ImageBitmap>(64) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = 1
}

/**
 * Renders [latex] with the real TeX engine and returns the ImageBitmap, or
 * null when it cannot be parsed (fallback path takes over). Thread-safe.
 */
fun latexBitmap(latex: String, textSizePx: Float, colorArgb: Int): ImageBitmap? {
    if (latex.isBlank()) return null
    val key = "$latex\u0001${textSizePx.toInt()}\u0001$colorArgb"
    latexBitmapCache.get(key)?.let { return it }
    return try {
        val drawable = JLatexMathDrawable.builder(latex)
            .textSize(textSizePx)
            .color(colorArgb)
            .align(JLatexMathDrawable.ALIGN_LEFT)
            .build()
        val w = maxOf(1, drawable.intrinsicWidth)
        val h = maxOf(1, drawable.intrinsicHeight)
        // Cap absurd sizes (malformed input can ask for huge icons).
        val scale = minOf(1f, 2200f / w, 3200f / h)
        val bw = maxOf(1, (w * scale + 0.5f).toInt())
        val bh = maxOf(1, (h * scale + 0.5f).toInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, bw, bh)
        drawable.draw(canvas)
        val result = bmp.asImageBitmap()
        latexBitmapCache.put(key, result)
        result
    } catch (e: Throwable) {
        null // ParseException or OOM — caller falls back to the text renderer
    }
}

/**
 * True when a line is a standalone math expression worth the big-typography
 * path ("x = \frac{3}{4}" style). Prose with one embedded symbol stays on the
 * fast text path.
 */
fun isPureMathParagraph(source: String): Boolean {
    val t = source.trim()
    if (t.isEmpty() || t.length > 400) return false
    // Our custom student fraction syntaxes are not valid LaTeX for the engine.
    if (t.contains("frac((") || t.contains("frac (") || t.contains("frac(")) return false
    if (LATEX_DISPLAY_CMD.containsMatchIn(t)) return true
    // Short bare equations like `2 + 2 = 4` or `x = 3x^2 - 7` render nicely too.
    return t.contains(Regex("[=<>]")) && t.split(Regex("\\s+")).size <= 14
}

private val LATEX_DISPLAY_CMD = Regex(
    """\\(frac|dfrac|tfrac|cfrac|sqrt|sum|int|prod|lim|binom|begin|vec|overline|underline|widehat|hat|overrightarrow|left|right)"""
)

/**
 * Composable image of a TeX-rendered formula. Scales down to fit the available
 * width; [fallback] renders when the formula cannot be parsed.
 */
@Composable
internal fun LatexMathImage(
    latex: String,
    textSizePx: Float,
    colorArgb: Int,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    fallback: @Composable () -> Unit = {}
) {
    val bmp = remember(latex, textSizePx, colorArgb) { latexBitmap(latex, textSizePx, colorArgb) }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
            modifier = modifier
        )
    } else {
        fallback()
    }
}

/** Convenience overload: size in sp + themed text color. */
@Composable
internal fun LatexMathImage(
    latex: String,
    textSizeSp: Float,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    fallback: @Composable () -> Unit = {}
) {
    val colors = LocalFioColors.current
    val px = with(LocalDensity.current) { textSizeSp.sp.toPx() }
    LatexMathImage(
        latex = latex,
        textSizePx = px,
        colorArgb = colors.textPrimary.toArgb(),
        modifier = modifier,
        fillWidth = fillWidth,
        fallback = fallback
    )
}
