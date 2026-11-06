package com.example.fio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.ui.theme.FioCustomColors

/**
 * Math + rich-inline renderer for AI replies.
 *
 * Converts LaTeX-ish markup into real typography:
 *  - `\frac{a}{b}`, `frac((a),(b))`, `frac(a)(b)`, `frac(a,b)` → real stacked fraction
 *  - `\sqrt[3]{x}`                                              → ∛(x)
 *  - `^{…}` / `_{…}`                                              → upper/lowered spans
 *  - `\alpha`, `\times`, `\int`, `\to` …                         → Unicode glyphs (α × ∫ →)
 *  - `\text{In 3 days}`                                          → plain text
 *
 * Math does NOT need `$…$` wrappers: bare `frac((3x+2),(x-5))` fragments in
 * prose are detected and stacked too. Malformed/incomplete input is kept as
 * literal text, so mid-stream replies never flicker or lose characters.
 */

/**
 * One inline segment of a rich paragraph: annotated text (markdown styling +
 * linear math) or a stacked-fraction slot rendered as its own composable.
 */
sealed class MathSegment {
    class Math(val text: AnnotatedString) : MathSegment()
    class Frac(val num: String, val den: String) : MathSegment()
}

// ----------------------------------------------------------------- token tree

private sealed class Tok {
    class Text(val s: String) : Tok()
    /** `\frac` variants; content kept raw so parent renders it with the same helpers. */
    class Frac(val num: String, val den: String) : Tok()
    class Sqrt(val body: String, val index: String?) : Tok()
    /** `\text{…}`, `\mathrm{…}` and friends: keep content, drop the command. */
    class TextCmd(val s: String) : Tok()
    /** `\overline{x}` / `\hat{x}`: body plus a combining over-mark. */
    class Decorated(val body: String, val combining: Char) : Tok()
}

private val FRACTION_CMDS = listOf("\\dfrac", "\\tfrac", "\\cfrac", "\\frac")
private val TEXT_CMDS = listOf(
    "\\text", "\\mathrm", "\\mathbf", "\\mathbb", "\\mathcal", "\\mathsf",
    "\\mathtt", "\\operatorname", "\\boldsymbol", "\\textbf", "\\mathit"
)
private val DECORATE_CMDS = listOf(
    "\\overline" to '\u0304', "\\bar" to '\u0304', "\\hat" to '\u0302',
    "\\dot" to '\u0307', "\\tilde" to '\u0303'
)

/** Splits [src] into Text/Frac/Sqrt/TextCmd tokens. Never throws; failures degrade to plain text. */
private fun tokenize(src: String): List<Tok> {
    val tokens = mutableListOf<Tok>()
    val sb = StringBuilder()
    fun flush() {
        if (sb.isNotEmpty()) { tokens.add(Tok.Text(sb.toString())); sb.clear() }
    }
    var i = 0
    while (i < src.length) {
        val rest = src.substring(i)
        if (src[i] == '\\' && !src.startsWith("\\\\", i)) {
            val fracCmd = FRACTION_CMDS.firstOrNull { rest.startsWith(it) }
            if (fracCmd != null) {
                val cmdEnd = i + fracCmd.length
                val parsed = parseFracArgs(src, cmdEnd)
                if (parsed != null) {
                    flush(); tokens.add(Tok.Frac(parsed.first, parsed.second)); i = parsed.third; continue
                }
            }
            if (rest.startsWith("\\sqrt")) {
                val parsed = parseSqrtArgs(src, i + "\\sqrt".length)
                if (parsed != null) {
                    flush(); tokens.add(Tok.Sqrt(parsed.first, parsed.second)); i = parsed.third; continue
                }
            }

            val textCmd = TEXT_CMDS.firstOrNull { rest.startsWith(it) }
            if (textCmd != null) {
                val parsed = parseBraced(src, i + textCmd.length)
                if (parsed != null) {
                    flush(); tokens.add(Tok.TextCmd(parsed.first)); i = parsed.second; continue
                }
            }
            val decorate = DECORATE_CMDS.firstOrNull { rest.startsWith(it.first) }
            if (decorate != null) {
                val parsed = parseBraced(src, i + decorate.first.length)
                if (parsed != null) {
                    flush(); tokens.add(Tok.Decorated(parsed.first, decorate.second)); i = parsed.second; continue
                }
            }
            sb.append(src[i]); i++
            continue
        }
        // Student-style bare `frac((a),(b))` / `frac(a)(b)` / `frac(a,b)` — not glued to a letter.
        val boundary = i == 0 || (src[i - 1] != '\\' && !src[i - 1].isLetter())
        if (boundary && src.startsWith("frac", i)) {
            val parsed = parseFracArgs(src, i + "frac".length)
            if (parsed != null) {
                flush(); tokens.add(Tok.Frac(parsed.first, parsed.second)); i = parsed.third; continue
            }
        }
        sb.append(src[i]); i++
    }
    flush()
    return tokens
}

/**
 * Parses fraction arguments at [start] (index just after the `frac` command).
 * Handles: `{a}{b}` with spacing, `((a),(b))` plainmath style, `(a)(b)`,
 * `(a,b)` and `12` (`\frac12`) forms. Returns (num, den, endPosInclusive) or null.
 */
private fun parseFracArgs(src: String, start: Int): Triple<String, String, Int>? {
    var pos = skipSpaces(src, start)
    if (pos >= src.length) return null
    if (src[pos] == '(') {
        val first = parseBalanced(src, pos, '(', ')') ?: return null
        val firstInner = src.substring(first.first + 1, first.second - 1)
        val commaInsideFirst = topLevelComma(firstInner)
        if (commaInsideFirst >= 0) {
            // ((a),(b)) or (a,b): single paren group holding both args.
            val num = stripOuterParens(firstInner.substring(0, commaInsideFirst).trim())
            val den = stripOuterParens(firstInner.substring(commaInsideFirst + 1).trim())
            return Triple(num, den, first.second)
        }
        // (a)(b): first group done, second group follows directly.
        val mid = skipSpaces(src, first.second)
        if (mid < src.length && src[mid] == '(') {
            val second = parseBalanced(src, mid, '(', ')')
            if (second != null) {
                return Triple(
                    stripOuterParens(firstInner.trim()),
                    stripOuterParens(src.substring(second.first + 1, second.second - 1).trim()),
                    second.second
                )
            }
        }
        return null
    }
    if (src[pos] == '{') {
        val first = parseBalanced(src, pos, '{', '}') ?: return null
        val mid = skipSpaces(src, first.second)
        if (mid < src.length && src[mid] == '{') {
            val second = parseBalanced(src, mid, '{', '}')
            if (second != null) {
                return Triple(
                    src.substring(first.first + 1, first.second - 1).trim(),
                    src.substring(second.first + 1, second.second - 1).trim(),
                    second.second
                )
            }
        }
        return null
    }
    // \frac12 style: two single argument characters.
    if (pos + 1 < src.length && src[pos].isLetterOrDigit() && src[pos + 1].isLetterOrDigit()) {
        return Triple(src[pos].toString(), src[pos + 1].toString(), pos + 2)
    }
    return null
}

private fun parseSqrtArgs(src: String, start: Int): Triple<String, String?, Int>? {
    var pos = skipSpaces(src, start)
    var index: String? = null
    if (pos < src.length && src[pos] == '[') {
        val idx = parseBalanced(src, pos, '[', ']')
        if (idx != null) {
            index = src.substring(idx.first + 1, idx.second - 1).trim()
            pos = skipSpaces(src, idx.second)
        }
    }
    if (pos < src.length) {
        for ((open, close) in listOf('{' to '}', '(' to ')')) {
            if (src[pos] == open) {
                val group = parseBalanced(src, pos, open, close)
                if (group != null) {
                    return Triple(src.substring(group.first + 1, group.second - 1).trim(), index, group.second)
                }
            }
        }
        if (src[pos].isLetterOrDigit()) return Triple(src[pos].toString(), index, pos + 1)
    }
    return null
}

/** Returns (contentStartInclusive, afterCloseExclusive) for a balanced group, or null. */
private fun parseBalanced(src: String, start: Int, open: Char, close: Char): Pair<Int, Int>? {
    if (start >= src.length || src[start] != open) return null
    var depth = 0
    var i = start
    while (i < src.length) {
        val c = src[i]
        if (c == '\\') { i += 2; continue } // skip escaped char
        if (c == open) depth++
        else if (c == close) {
            depth--
            if (depth == 0) return start to (i + 1)
        }
        i++
    }
    return null
}

/** `{…}` group content (braces dropped). */
private fun parseBraced(src: String, start: Int): Pair<String, Int>? {
    val group = parseBalanced(src, start, '{', '}') ?: return null
    return src.substring(group.first + 1, group.second - 1) to group.second
}

private fun skipSpaces(src: String, from: Int): Int {
    var p = from
    while (p < src.length && src[p] == ' ') p++
    return p
}

/** Index of the first ',' occurring at brace/paren depth 0 inside [s], or -1. */
private fun topLevelComma(s: String): Int {
    var depth = 0
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\') { i += 2; continue }
        when (c) {
            '(', '{', '[' -> depth++
            ')', '}', ']' -> depth--
            ',' -> if (depth == 0) return i
        }
        i++
    }
    return -1
}

private fun stripOuterParens(s: String): String {
    var out = s.trim()
    while (out.length >= 2 && out.first() == '(' && out.last() == ')') {
        if (parseBalanced(out, 0, '(', ')')?.second == out.length) out = out.substring(1, out.length - 1).trim() else break
    }
    return out
}

/** Parses only fully-formed `{…}` group content at [start]; returns (content, afterClose). */

// ----------------------------------------------------------- symbol conversion

/** Longest-first substitution so \rightarrow wins over \right, \sigma wins over \s, etc. */
private val LATEX_SYMBOLS: List<Pair<String, String>> = listOf(
    // --- text-size / environment noise: silenced ---
    "\\displaystyle" to "", "\\textstyle" to "", "\\scriptstyle" to "",
    "\\operatorname" to "", "\\limits" to "", "\\nolimits" to "", "\\nonumber" to "",
    "\\left" to "", "\\right" to "",
    "\\big" to "", "\\Big" to "", "\\bigg" to "", "\\Bigg" to "",
    "\\large" to "", "\\small" to "", "\\huge" to "", "\\tiny" to "",
    "\\scriptsize" to "", "\\footnotesize" to "", "\\normalsize" to "",
    "\\begin" to "", "\\end" to "", "\\hline" to "", "\\boxed" to "",
    // --- long arrows/relations before shorter ones ---
    "\\longleftrightarrow" to "⟷", "\\leftrightarrow" to "↔",
    "\\longrightarrow" to "⟶", "\\rightarrow" to "→", "\\Rightarrow" to "⇒",
    "\\leftarrow" to "←", "\\downarrow" to "↓", "\\uparrow" to "↑",
    "\\mapsto" to "↦", "\\to" to "→", "\\gets" to "←", "\\iff" to "⟺", "\\implies" to "⇒",
    "\\subseteq" to "⊆", "\\supseteq" to "⊇", "\\emptyset" to "∅",
    "\\varnothing" to "∅", "\\varnothing" to "∅",
    "\\leqslant" to "≤", "\\geqslant" to "≥", "\\varepsilon" to "ε",
    "\\greek text" to "", "\\gammacdot" to "",
    // --- functions (before single-letter sketches like \sin \s conflicts) ---
    "\\arcsin" to "arcsin", "\\arccos" to "arccos", "\\arctan" to "arctan",
    "\\sinh" to "sinh", "\\cosh" to "cosh", "\\tanh" to "tanh",
    "\\max" to "max", "\\min" to "min", "\\deg" to "deg", "\\arg" to "arg",
    "\\sup" to "sup", "\\inf" to "inf", "\\det" to "det", "\\lim" to "lim",
    "\\log" to "log", "\\ln" to "ln", "\\lg" to "lg", "\\exp" to "exp",
    "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\sec" to "sec",
    "\\csc" to "csc", "\\cot" to "cot",
    // --- big operators ---
    "\\coprod" to "∐", "\\prod" to "∏", "\\sum" to "∑", "\\oint" to "∮",
    "\\int" to "∫", "\\infty" to "∞", "\\hbar" to "ℏ", "\\partial" to "∂", "\\nabla" to "∇",
    // --- binary ops ---
    "\\pm" to "±", "\\mp" to "∓", "\\cdot" to "·", "\\times" to "×", "\\div" to "÷",
    "\\star" to "⋆", "\\circ" to "∘", "\\odot" to "⊙", "\\oplus" to "⊕", "\\ominus" to "⊖",
    "\\otimes" to "⊗", "\\dagger" to "†", "\\ddagger" to "‡",
    // --- relations ---
    "\\equiv" to "≡", "\\simeq" to "≃", "\\cong" to "≅", "\\propto" to "∝",
    "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠", "\\ne" to "≠",
    "\\approx" to "≈", "\\sim" to "∼",
    // --- greek (lower + upper) ---
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
    "\\epsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η", "\\iota" to "ι", "\\kappa" to "κ",
    "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\omicron" to "ο",
    "\\pi" to "π", "\\rho" to "ρ", "\\sigma" to "σ", "\\tau" to "τ", "\\phi" to "φ",
    "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
    "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
    "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ", "\\Phi" to "Φ",
    "\\Psi" to "Ψ", "\\Omega" to "Ω",
    // --- set / logic ---
    "\\ll" to "≪", "\\gg" to "≫", "\\lll" to "⋘", "\\ggg" to "⋙",
    "\\in" to "∈", "\\notin" to "∉", "\\ni" to "∋", "\\subset" to "⊂", "\\supset" to "⊃",
    "\\cup" to "∪", "\\cap" to "∩", "\\forall" to "∀", "\\exists" to "∃",
    "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥", "\\degree" to "°",
    "\\prime" to "′", "\\Re" to "ℜ", "\\Im" to "ℑ", "\\aleph" to "ℵ",
    "\\triangle" to "△", "\\square" to "□",
    // --- delimiters / spacing ---
    "\\lceil" to "⌈", "\\rceil" to "⌉", "\\lfloor" to "⌊", "\\rfloor" to "⌋",
    "\\|" to "‖", "\\{" to "{", "\\}" to "}", "\\%" to "%", "\\$" to "$", "\\_" to "_",
    "\\," to " ", "\\;" to " ", "\\:" to " ", "\\!" to "",
    "\\quad" to " ", "\\qquad" to "  "
).sortedByDescending { it.first.length }

private fun substituteSymbols(expr: String): String {
    var out = expr
    for ((cmd, glyph) in LATEX_SYMBOLS) out = out.replace(cmd, glyph)
    return out
}

// ------------------------------------------------------------- linear renderer

/** Linear (single-line) representation: glyphs, scripts, and a/b fallback fractions. */
private fun mathLinearAnnotated(tokens: List<Tok>, colors: FioCustomColors): AnnotatedString =
    buildAnnotatedString { appendTokensLinear(this, tokens, colors) }

private fun appendTokensLinear(
    scope: AnnotatedString.Builder,
    tokens: List<Tok>,
    colors: FioCustomColors
) {
    for (token in tokens) {
        when (token) {
            is Tok.Text -> appendScripted(scope, token.s)
            is Tok.TextCmd -> appendScripted(scope, token.s)
            is Tok.Decorated -> {
                appendScripted(scope, token.body)
                for (c in token.body) if (!c.isWhitespace()) scope.append(token.combining.toString())
            }
            is Tok.Sqrt -> {
                when (token.index) {
                    null, "", "2" -> {
                        scope.append("√(")
                        appendTokensLinear(scope, tokenize(token.body), colors)
                        scope.append(")")
                    }
                    "3" -> {
                        scope.append("∛(")
                        appendTokensLinear(scope, tokenize(token.body), colors)
                        scope.append(")")
                    }
                    "4" -> {
                        scope.append("∜(")
                        appendTokensLinear(scope, tokenize(token.body), colors)
                        scope.append(")")
                    }
                    else -> {
                        scope.append("^(1/"); scope.append(token.index); scope.append(")√(")
                        appendTokensLinear(scope, tokenize(token.body), colors)
                        scope.append(")")
                    }
                }
            }
            is Tok.Frac -> {
                scope.append("(")
                appendTokensLinear(scope, tokenize(token.num), colors)
                scope.append(")/")
                appendTokensLinear(scope, tokenize(token.den), colors)
            }
        }
    }
}

/** Appends [expr] with glyph substitution and ^{…}/_{…} raised/lowered spans. */
private fun appendScripted(scope: AnnotatedString.Builder, expr: String) {
    val symbols = substituteSymbols(expr)
    var i = 0
    while (i < symbols.length) {
        val c = symbols[i]
        if (c == '^' || c == '_') {
            val group = scriptGroupAfter(symbols, i + 1)
            if (group != null) {
                scope.pushStyle(
                    SpanStyle(
                        baselineShift = if (c == '^') BaselineShift.Superscript else BaselineShift.Subscript
                    )
                )
                scope.append(group.first)
                scope.pop()
                i = group.second
                continue
            }
        }
        scope.append(c)
        i++
    }
}

/** Content after ^/_ : `{…}` group, `\command` name, or a single alnum char. */
private fun scriptGroupAfter(source: String, start: Int): Pair<String, Int>? {
    if (start >= source.length) return null
    if (source[start] == '{') {
        var depth = 1
        var j = start + 1
        while (j < source.length) {
            when (source[j]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start + 1, j) to (j + 1)
                }
            }
            j++
        }
        return null
    }
    val c = source[start]
    if (c == '\\') {
        var j = start + 1
        if (j < source.length && source[j] == '\\') return "\\" to (j + 1)
        while (j < source.length && source[j].isLetter()) j++
        if (j > start + 1) return source.substring(start, j) to j
        return null
    }
    if (c.isLetterOrDigit()) return c.toString() to (start + 1)
    return null
}

// ------------------------------------------------------------- stacked fraction

/**
 * A real stacked fraction: numerator over a rule over denominator. Rendered
 * as a standalone inline composable inside FlowRow paragraphs.
 */
@Composable
fun FractionView(
    num: String,
    den: String,
    colors: FioCustomColors,
    base: TextUnit,
    modifier: Modifier = Modifier
) {
    val fontSize = (base.value * 0.86f).sp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .width(IntrinsicSize.Min)
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = mathLinearAnnotated(tokenize(num), colors),
            color = colors.textPrimary,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.2f.dp)
                .background(colors.textPrimary.copy(alpha = 0.75f))
        )
        Text(
            text = mathLinearAnnotated(tokenize(den), colors),
            color = colors.textPrimary,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

// --------------------------------------------------------------- rich builder

/**
 * Splits rich prose into inline segments: annotated text runs (markdown
 * styling + linear math) and stacked-fraction slots. [FioMarkdown] lays the
 * segments out in a FlowRow so fractions sit inline inside the paragraph.
 */
fun buildMathSegments(source: String, colors: FioCustomColors): List<MathSegment> {
    val segments = mutableListOf<MathSegment>()
    var builder = AnnotatedString.Builder()
    fun flush() {
        val built = builder.toAnnotatedString()
        if (built.text.isNotEmpty()) segments.add(MathSegment.Math(built))
        builder = AnnotatedString.Builder()
    }
    for (token in tokenize(source)) {
        when (token) {
            is Tok.Frac -> {
                flush()
                segments.add(MathSegment.Frac(token.num, token.den))
            }
            is Tok.Text -> appendInlineStyled(builder, token.s, colors)
            is Tok.TextCmd -> appendScripted(builder, token.s)
            is Tok.Sqrt, is Tok.Decorated -> appendTokensLinear(builder, listOf(token), colors)
        }
    }
    flush()
    return segments
}

/** Fast check whether [source] contains a renderable (stacked) fraction. */
fun hasStackedFraction(source: String): Boolean = tokenize(source).any { it is Tok.Frac }

/** Whole-source linear render: markdown styling, math glyphs, (a)/(b) fractions. */
fun buildFullLinear(source: String, colors: FioCustomColors): AnnotatedString {
    val builder = AnnotatedString.Builder()
    for (token in tokenize(source)) {
        when (token) {
            is Tok.Frac -> {
                builder.append("(")
                appendTokensLinear(builder, tokenize(token.num), colors)
                builder.append(")/")
                appendTokensLinear(builder, tokenize(token.den), colors)
            }
            is Tok.Text -> appendInlineStyled(builder, token.s, colors)
            is Tok.TextCmd -> appendScripted(builder, token.s)
            is Tok.Sqrt, is Tok.Decorated -> appendTokensLinear(builder, listOf(token), colors)
        }
    }
    return builder.toAnnotatedString()
}

/**
 * Inline markdown scanner for plain text segments: `**bold**`, `*italic*`,
 * `__underline__`, `~~strike~~`, `` `code` ``, and `$…$` / `\(\)` / `\[\]` math.
 * Unterminated markers stay literal so streaming never flickers.
 */
private fun appendInlineStyled(
    scope: AnnotatedString.Builder,
    source: String,
    colors: FioCustomColors
) {
    var i = 0
    while (i < source.length) {
        val c = source[i]
        when {
            c == '`' -> {
                val close = source.indexOf('`', i + 1)
                if (close > i) {
                    scope.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceElevated))
                    appendTokensLinear(scope, tokenize(source.substring(i + 1, close)), colors)
                    scope.pop()
                    i = close + 1
                } else {
                    scope.append(c); i++
                }
            }
            c == '*' || c == '~' || c == '_' -> {
                val marker = when {
                    c == '~' && source.startsWith("~~", i) -> "~~"
                    c == '*' && source.startsWith("***", i) -> "***"
                    c == '*' && source.startsWith("**", i) -> "**"
                    c == '_' && source.startsWith("__", i) -> "__"
                    else -> c.toString()
                }
                val close = findInlineClose(source, i + marker.length, marker)
                if (close > i) {
                    val content = source.substring(i + marker.length, close)
                    when (marker) {
                        "**" -> scope.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                        "***" -> scope.pushStyle(
                            SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
                        )
                        "__" -> scope.pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        "~~" -> scope.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        else -> scope.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    }
                    appendTokensLinear(scope, tokenize(content), colors)
                    scope.pop()
                    i = close + marker.length
                } else {
                    scope.append(source.substring(i, i + marker.length))
                    i += marker.length
                }
            }
            c == '$' -> {
                if (source.startsWith("$$", i)) {
                    val close = source.indexOf("$$", i + 2)
                    if (close > i) {
                        appendTokensLinear(scope, tokenize(source.substring(i + 2, close)), colors)
                        i = close + 2
                    } else {
                        scope.append(source.substring(i, i + 2)); i += 2
                    }
                } else {
                    val close = source.indexOf('$', i + 1)
                    if (close > i) {
                        appendTokensLinear(scope, tokenize(source.substring(i + 1, close).trim()), colors)
                        i = close + 1
                    } else {
                        scope.append(c); i++
                    }
                }
            }
            c == '\\' && source.startsWith("\\(", i) -> {
                val close = source.indexOf("\\)", i + 2)
                if (close > i) {
                    appendTokensLinear(scope, tokenize(source.substring(i + 2, close)), colors)
                    i = close + 2
                } else {
                    scope.append(c); i++
                }
            }
            c == '\\' && source.startsWith("\\[", i) -> {
                val close = source.indexOf("\\]", i + 2)
                if (close > i) {
                    appendTokensLinear(scope, tokenize(source.substring(i + 2, close)), colors)
                    i = close + 2
                } else {
                    scope.append(c); i++
                }
            }
            else -> {
                scope.append(c)
                i++
            }
        }
    }
}

private fun findInlineClose(source: String, from: Int, marker: String): Int {
    var j = from
    while (j < source.length) {
        val idx = source.indexOf(marker[0], j)
        if (idx < 0) return -1
        if (source.startsWith(marker, idx)) return idx
        j = idx + 1
    }
    return -1
}
