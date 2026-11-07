package com.example.fio.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.fio.ui.theme.FioCustomColors
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
        val drawable = JLatexMathDrawable.builder(normalizeLatexCommands(latex))
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
        // Blank-render guard: some unsupported macro forms make the engine
        // produce an (almost) empty bitmap. Treat that as a parse failure so
        // callers fall back to the text renderer (visible glyphs) instead of
        // rendering blank space where a symbol should be.
        val step = 7
        var ink = 0
        var sampled = 0
        var x = 0
        while (x < bw) {
            var y = 0
            while (y < bh) {
                if ((bmp.getPixel(x, y) ushr 24) > 8) ink++
                sampled++
                y += step
            }
            x += step
        }
        if (sampled == 0 || ink * 200 < sampled) return null
        val result = bmp.asImageBitmap()
        latexBitmapCache.put(key, result)
        result
    } catch (e: Throwable) {
        null // ParseException or OOM — caller falls back to the text renderer
    }
}

/** Maps `\Ω`-style escapes to real TeX commands (`\Omega`). Uppercase Greek
 * letters that have no TeX command use their Latin lookalike instead. */
private fun normalizeLatexCommands(src: String): String {
    val escapedGreek = Regex("""\\([\u0370-\u03FF])""")
    val replaced = escapedGreek.replace(src) { m ->
        when (val c = m.groupValues[1][0]) {
            'α' -> "\\alpha"; 'β' -> "\\beta"; 'γ' -> "\\gamma"; 'δ' -> "\\delta"
            'ε' -> "\\varepsilon"; 'ζ' -> "\\zeta"; 'η' -> "\\eta"; 'θ' -> "\\theta"
            'ι' -> "\\iota"; 'κ' -> "\\kappa"; 'λ' -> "\\lambda"; 'μ' -> "\\mu"
            'ν' -> "\\nu"; 'ξ' -> "\\xi"; 'ο' -> "\\omicron"; 'π' -> "\\pi"
            'ρ' -> "\\rho"; 'ς' -> "\\varsigma"; 'σ' -> "\\sigma"; 'τ' -> "\\tau"
            'υ' -> "\\upsilon"; 'φ' -> "\\varphi"; 'χ' -> "\\chi"; 'ψ' -> "\\psi"; 'ω' -> "\\omega"
            'Γ' -> "\\Gamma"; 'Δ' -> "\\Delta"; 'Θ' -> "\\Theta"; 'Λ' -> "\\Lambda"
            'Ξ' -> "\\Xi"; 'Π' -> "\\Pi"; 'Σ' -> "\\Sigma"; 'Υ' -> "\\Upsilon"
            'Φ' -> "\\Phi"; 'Ψ' -> "\\Psi"; 'Ω' -> "\\Omega"
            'Α' -> "A"; 'Β' -> "B"; 'Ε' -> "E"; 'Ζ' -> "Z"; 'Η' -> "H"; 'Ι' -> "I"
            'Κ' -> "K"; 'Μ' -> "M"; 'Ν' -> "N"; 'Ο' -> "O"; 'Ρ' -> "P"; 'Τ' -> "T"; 'Χ' -> "X"
            else -> c.toString()
        }
    }
    // Models sometimes drop English words straight into math mode (e.g.
    // "(for asymmetric matrix A)"); TeX ignores spaces there, producing
    // "forasymmetricmatrixA". Preserve their intended word gaps explicitly.
    return LETTER_GAP.replace(replaced) { "\\ " }
}

/** A space with letters on both sides — an intended word boundary in prose
 * that the model embedded in math mode. */
private val LETTER_GAP = Regex("""(?<=[A-Za-z])\s+(?=[A-Za-z])""")

/**
 * True when a line is a standalone math expression worth the big-typography
 * path ("x = \frac{3}{4}" style). Prose with one embedded symbol stays on the
 * fast text path.
 */
fun isPureMathParagraph(source: String): Boolean {
    val t = source.trim()
    if (t.isEmpty() || t.length > 400) return false
    // Prose that merely CONTAINS math must never be typeset wholesale: in TeX
    // math mode words lose their spacing ("gtheoutputinto..."). List items,
    // bold spans, inline code and long \text{…} prose stay on the text path.
    if (LIST_ITEM_START.containsMatchIn(t)) return false
    if (t.contains("**") || t.contains('`')) return false
    if (LONG_TEXT_CMD.containsMatchIn(t)) return false
    // Our custom student fraction syntaxes are not valid LaTeX for the engine.
    if (t.contains("frac((") || t.contains("frac (") || t.contains("frac(")) return false
    if (LATEX_DISPLAY_CMD.containsMatchIn(t)) return true
    // Short bare equations like `2 + 2 = 4` or `x = 3x^2 - 7` render nicely too.
    return t.contains(Regex("[=<>]")) && t.split(Regex("\\s+")).size <= 14
}

private val LIST_ITEM_START = Regex("""^\s*(?:[-*+]|\d{1,2}[.)])\s""")
private val LONG_TEXT_CMD = Regex("""\\text\{[^{}]{25,}\}""")

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

/**
 * One inline math run (e.g. `e^{z_j}`, `x_{i+1}`) typeset by the TeX engine at
 * text size inside a prose paragraph. Falls back to the glyph renderer when
 * the formula cannot be parsed, so nothing ever leaks raw.
 */
@Composable
internal fun InlineMathView(
    latex: String,
    colors: FioCustomColors,
    base: TextUnit,
    modifier: Modifier = Modifier
) {
    val px = with(LocalDensity.current) { (base.value * 1.08f).sp.toPx() }
    val argb = colors.textPrimary.toArgb()
    val bmp = remember(latex, px, argb) { latexBitmap(latex, px, argb) }
    if (bmp != null) {
        val (wDp, hDp) = with(LocalDensity.current) { bmp.width.toDp() to bmp.height.toDp() }
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier.width(wDp).height(hDp)
        )
    } else {
        Text(
            text = remember(latex, colors) { buildFullLinear(latex, colors) },
            color = colors.textPrimary,
            fontSize = base,
            modifier = modifier
        )
    }
}
