package com.example.fio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.ui.theme.FioCustomColors
import com.example.fio.ui.theme.LocalFioColors

/**
 * Lightweight offline Markdown renderer for AI replies (chat/tutor/notes).
 *
 * Trims to the essentials so streaming re-renders stay cheap on weak hardware:
 * headings, bold/italic/strikethrough, inline code, fenced code blocks, bullet
 * and numbered lists, blockquotes, horizontal rules and simple pipe tables.
 * Math in $…$ / $$…$$ is rendered as actual characters (Greek letters, ∫, ×,
 * raised exponents) instead of raw LaTeX commands. Unterminated markdown
 * (mid-stream `**`, open fences) is shown as literal text, never flicker.
 *
 * Pure function of `text`: recompose only happens when the message grows.
 */
@Composable
fun FioMarkdown(
    text: String,
    baseFontSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier,
    colors: FioCustomColors = LocalFioColors.current
) {
    val blocks = remember(text) { parseBlocks(text.lines()) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block -> BlockView(block, colors, baseFontSize) }
    }
}

// ---------------------------------------------------------------- block model

private sealed class Block {
    data class Paragraph(val lines: List<String>) : Block()
    data class Heading(val level: Int, val text: String) : Block()
    data class CodeBlock(val lines: List<String>) : Block()
    data class Blockquote(val lines: List<String>) : Block()
    data class ListItem(val ordinal: String?, val text: String) : Block()
    data class HRule(val source: String) : Block()
    data class Table(val header: List<String>, val rows: List<List<String>>) : Block()
}

private val HR_REGEX = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
private val BULLET_REGEX = Regex("""^\s*[-*+]\s+(.*)$""")
private val ORDINAL_REGEX = Regex("""^\s*(\d{1,2})[.)]\s+(.*)$""")
private val HEADING_REGEX = Regex("""^\s*(#{1,6})\s+(.*)$""")
private val QUOTE_REGEX = Regex("""^\s*>\s?(.*)$""")
private val TABLE_ROW_REGEX = Regex("""^\s*\|.*\|\s*$""")
private val TABLE_SEP_REGEX = Regex("""^\s*\|(\s*:?-{2,}:?\s*\|)+\s*$""")

private fun parseBlocks(all: List<String>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0
    val n = all.size
    while (i < n) {
        val line = all[i]
        when {
            HEADING_REGEX.containsMatchIn(line) -> {
                val m = HEADING_REGEX.find(line)!!
                blocks += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                i++
            }
            HR_REGEX.containsMatchIn(line) -> {
                blocks += Block.HRule(line.trim())
                i++
            }
            line.trimStart().startsWith("```") -> {
                val code = mutableListOf<String>()
                i++
                while (i < n && !all[i].trimStart().startsWith("```")) {
                    code += all[i].replace("\t", "    ")
                    i++
                }
                if (i >= n) code += "… (code block still streaming)"
                blocks += Block.CodeBlock(code)
                if (i < n) i++
            }
            TABLE_ROW_REGEX.containsMatchIn(line) -> {
                // Pipe table: header line, optional separator line, then rows.
                var j = i + 1
                val raw = mutableListOf(line)
                while (j < n && TABLE_ROW_REGEX.containsMatchIn(all[j])) {
                    raw += all[j]
                    j++
                }
                var headerCells: List<String>? = null
                var startRow = 0
                if (raw.size >= 2 && TABLE_SEP_REGEX.containsMatchIn(raw[1])) {
                    headerCells = splitTableRow(raw[0])
                    startRow = 2
                }
                val rows = raw.drop(startRow).map { splitTableRow(it) }.filter { it.isNotEmpty() }
                if (headerCells != null || rows.isNotEmpty()) {
                    blocks += Block.Table(
                        header = headerCells ?: rows.firstOrNull() ?: emptyList(),
                        rows = if (headerCells == null) rows.drop(1) else rows
                    )
                    i = j
                } else {
                    // Stray pipe text — keep it visible as a paragraph.
                    blocks += Block.Paragraph(listOf(line.trim()))
                    i++
                }
            }
            QUOTE_REGEX.containsMatchIn(line) -> {
                val quote = mutableListOf<String>()
                while (i < n && QUOTE_REGEX.containsMatchIn(all[i])) {
                    quote += QUOTE_REGEX.find(all[i])!!.groupValues[1]
                    i++
                }
                blocks += Block.Blockquote(quote)
            }
            BULLET_REGEX.containsMatchIn(line) || ORDINAL_REGEX.containsMatchIn(line) -> {
                val bullet = BULLET_REGEX.find(line)
                val ordinal = ORDINAL_REGEX.find(line)
                val text = bullet?.groupValues?.get(1) ?: ordinal!!.groupValues[2]
                val num = if (bullet != null) null else ordinal!!.groupValues[1] + ". "
                // Merge wrapped lines into the same item.
                val item = StringBuilder(text)
                i++
                while (i < n && all[i].isNotBlank() &&
                    !BULLET_REGEX.containsMatchIn(all[i]) && !ORDINAL_REGEX.containsMatchIn(all[i]) &&
                    !HEADING_REGEX.containsMatchIn(all[i]) && !QUOTE_REGEX.containsMatchIn(all[i]) &&
                    !all[i].trimStart().startsWith("```")
                ) {
                    item.append(' ').append(all[i].trim())
                    i++
                }
                blocks += Block.ListItem(num, item.toString())
            }
            else -> {
                // Plain paragraph: merge until blank line / special block.
                val para = mutableListOf<String>()
                while (i < n && all[i].isNotBlank() && !isBlockStart(all[i])) {
                    para += all[i].trim()
                    i++
                }
                if (para.isNotEmpty()) blocks += Block.Paragraph(para)
                if (i < n && all[i].isBlank()) i++
            }
        }
    }
    return blocks
}

private fun isBlockStart(line: String): Boolean =
    HEADING_REGEX.containsMatchIn(line) || HR_REGEX.containsMatchIn(line) ||
        BULLET_REGEX.containsMatchIn(line) || ORDINAL_REGEX.containsMatchIn(line) ||
        QUOTE_REGEX.containsMatchIn(line) || line.trimStart().startsWith("```") ||
        TABLE_ROW_REGEX.containsMatchIn(line)

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

// ------------------------------------------------------------------ rendering

private val headingSize = mapOf(1 to 17.sp, 2 to 16.sp, 3 to 15.sp, 4 to 14.sp, 5 to 14.sp, 6 to 13.sp)

@Composable
private fun BlockView(block: Block, colors: FioCustomColors, base: TextUnit) {
    when (block) {
        is Block.Paragraph -> Text(
            text = remember(block.lines) { inlineAnnotated(block.lines.joinToString(" "), colors) },
            color = colors.textPrimary,
            fontSize = base
        )
        is Block.Heading -> {
            // Heading text: strip inline marks, render bold so hierarchy is obvious.
            Text(
                text = block.text.replace("**", "").replace("`", ""),
                color = colors.textPrimary,
                fontSize = headingSize[minOf(block.level, 6)] ?: base,
                fontWeight = when (block.level) {
                    1, 2 -> FontWeight.SemiBold
                    3, 4 -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                }
            )
            if (block.level == 1 || block.level == 2) {
                Divider(color = colors.border, thickness = Dp.Hairline)
            }
        }
        is Block.CodeBlock -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                block.lines.forEach { codeLine ->
                    Text(
                        text = codeLine,
                        color = colors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (base.value * 0.86).sp,
                        softWrap = false
                    )
                }
            }
        }
        is Block.Blockquote -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp * block.lines.size.coerceAtMost(6))
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border)
            )
            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                block.lines.forEach { quoteLine ->
                    Text(
                        text = inlineAnnotated(quoteLine, colors),
                        color = colors.textSecondary,
                        fontSize = base
                    )
                }
            }
        }
        is Block.ListItem -> Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = block.ordinal ?: "•",
                color = colors.textSecondary,
                fontSize = base
            )
            Box(modifier = Modifier.width(if (block.ordinal == null) 6.dp else 4.dp))
            Text(
                text = remember(block.text) { inlineAnnotated(block.text, colors) },
                color = colors.textPrimary,
                fontSize = base,
                modifier = Modifier.weight(1f)
            )
        }
        is Block.HRule -> {
            Divider(color = colors.border)
        }
        is Block.Table -> TableView(block, colors, base)
    }
}

@Composable
private fun TableView(table: Block.Table, colors: FioCustomColors, base: TextUnit) {
    val cols = (listOf(table.header.size) + table.rows.map { it.size }).maxOrNull() ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
    ) {
        if (table.header.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
                for (c in 0 until cols) {
                    Text(
                        text = table.header.getOrNull(c) ?: "",
                        color = colors.textPrimary,
                        fontSize = base,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            Divider(color = colors.border)
        }
        table.rows.forEachIndexed { index, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until cols) {
                    Text(
                        text = row.getOrNull(c) ?: "",
                        color = colors.textSecondary,
                        fontSize = base,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            if (index < table.rows.lastIndex) Divider(color = colors.border)
        }
    }
}

// -------------------------------------------------------- inline parser

/**
 * Parses **bold**, *italic*, ~~strike~~ and `code` into spans. Anything
 * unterminated (mid-stream `**`) survives as literal characters, so streaming
 * output never flickers between styled/unstyled states.
 */
private fun inlineAnnotated(source: String, colors: FioCustomColors): AnnotatedString =
    buildAnnotatedString {
        append("")
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '`' -> {
                    val close = source.indexOf('`', i + 1)
                    if (close > i) {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceElevated))
                        append(source.substring(i + 1, close))
                        pop()
                        i = close + 1
                    } else {
                        append(c); i++
                    }
                }
                c == '*' || c == '~' || c == '_' -> {
                    val marker = when {
                        c == '~' && source.startsWith("~~", i) -> "~~"
                        c == '*' && source.startsWith("***", i) -> "***"
                        c == '*' && source.startsWith("**", i) -> "**"
                        else -> c.toString()
                    }
                    val close = findInlineClose(source, i + marker.length, marker)
                    if (close > i) {
                        val content = source.substring(i + marker.length, close)
                        when (marker) {
                            "**" -> pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                            "***" -> pushStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            )
                            "~~" -> pushStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough))
                            else -> pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                        }
                        append(content)
                        pop()
                        i = close + marker.length
                    } else {
                        append(source.substring(i, i + marker.length))
                        i += marker.length
                    }
                }
                c == '$' -> {
                    // Math: render as actual characters — Greek letters (∑, ∫,
                    // α…Ω), ×/·/≤ operators, and superscripts/subscripts as
                    // raised/lowered spans. Streamed LaTeX that later completes
                    // simply re-renders once more glyphs resolve; unmatched $
                    // survives as literal text so nothing flickers or crashes.
                    if (source.startsWith("$$", i)) {
                        val close = source.indexOf("$$", i + 2)
                        if (close > i) {
                            append(mathAnnotated(source.substring(i + 2, close), colors))
                            i = close + 2
                        } else {
                            append(source.substring(i, i + 2)); i += 2
                        }
                    } else {
                        val close = source.indexOf('$', i + 1)
                        if (close > i) {
                            append(mathAnnotated(source.substring(i + 1, close), colors))
                            i = close + 1
                        } else {
                            append(c); i++
                        }
                    }
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

/**
 * Renders inline math ($…$ / $$…$$) as real characters: LaTeX commands become
 * Unicode symbols (α, Σ, ∫, ×, ≤, →, ∞ …), \frac{a}{b} and \sqrt{x} become
 * a/b and √(x), and ^{…} / _{…} become raised/lowered spans. If a command is
 * unknown it stays as readable plain text — math never disappears.
 */
private fun mathAnnotated(expr: String, colors: FioCustomColors): AnnotatedString =
    buildAnnotatedString {
        var symbols = expr
        symbols = FRACTION_REGEX.replace(symbols) { m -> "(${m.groupValues[1]})/(${m.groupValues[2]})" }
        symbols = SQRT_REGEX.replace(symbols) { m -> "√(${m.groupValues[1]})" }
        for ((cmd, glyph) in LATEX_SYMBOLS) symbols = symbols.replace(cmd, glyph)

        var i = 0
        while (i < symbols.length) {
            val c = symbols[i]
            if (c == '^' || c == '_') {
                val group = scriptGroupAfter(symbols, i + 1)
                if (group != null) {
                    pushStyle(
                        SpanStyle(
                            baselineShift = if (c == '^') BaselineShift.Superscript else BaselineShift.Subscript
                        )
                    )
                    append(group.first)
                    pop()
                    i = group.second
                    continue
                }
            }
            append(c)
            i++
        }
    }

/** 1-based content after ^/_ : either a braced group or a single script char. */
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
                    if (depth == 0) {
                        val content = source.substring(start + 1, j)
                        return content to (j + 1)
                    }
                }
            }
            j++
        }
        return null
    }
    val c = source[start]
    if (c == '\\' || c.isLetterOrDigit()) return c.toString() to (start + 1)
    return null
}

private val FRACTION_REGEX = Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}""")
private val SQRT_REGEX = Regex("""\\sqrt\{([^{}]*)\}""")

/** Longest-first so \rightarrow wins over \right, \int wins over \in, etc. */
private val LATEX_SYMBOLS: List<Pair<String, String>> = listOf(
    "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
    "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
    "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
    "\\lambda" to "λ", "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\omicron" to "ο",
    "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ", "\\varrho" to "ϱ", "\\sigma" to "σ",
    "\\varsigma" to "ς", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
    "\\varphi" to "ϕ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
    "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
    "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ", "\\Phi" to "Φ",
    "\\Psi" to "Ψ", "\\Omega" to "Ω",
    "\\sum" to "∑", "\\prod" to "∏", "\\coprod" to "∐", "\\int" to "∫", "\\oint" to "∮",
    "\\partial" to "∂", "\\nabla" to "∇", "\\infty" to "∞", "\\hbar" to "ℏ",
    "\\pm" to "±", "\\mp" to "∓", "\\cdot" to "·", "\\times" to "×", "\\div" to "÷",
    "\\star" to "⋆", "\\circ" to "∘", "\\odot" to "⊙", "\\oplus" to "⊕", "\\ominus" to "⊖",
    "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠",
    "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡", "\\sim" to "∼", "\\simeq" to "≃",
    "\\cong" to "≅", "\\propto" to "∝",
    "\\Rightarrow" to "⇒", "\\rightarrow" to "→", "\\leftrightarrow" to "↔",
    "\\leftarrow" to "←", "\\downarrow" to "↓", "\\uparrow" to "↑", "\\mapsto" to "↦",
    "\\in" to "∈", "\\notin" to "∉", "\\ni" to "∋", "\\subset" to "⊂", "\\supset" to "⊃",
    "\\subseteq" to "⊆", "\\supseteq" to "⊇", "\\cup" to "∪", "\\cap" to "∩",
    "\\emptyset" to "∅", "\\varnothing" to "∅", "\\forall" to "∀", "\\exists" to "∃",
    "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥", "\\degree" to "°", "\\prime" to "′",
    "\\Re" to "ℜ", "\\Im" to "ℑ", "\\aleph" to "ℵ",
    "\\left" to "", "\\right" to "", "\\displaystyle" to "", "\\textstyle" to "",
    "\\big" to "", "\\Big" to "", "\\bigg" to "", "\\Bigg" to "",
    "\\text" to "", "\\mathrm" to "", "\\mathbf" to "", "\\mathcal" to "", "\\mathbb" to "",
    "\\operatorname" to "", "\\limits" to "", "\\nolimits" to "",
    "\\quad" to "  ", "\\qquad" to "    ", "\\," to " ", "\\;" to " ", "\\:" to " ",
    "\\!" to "", "\\{" to "{", "\\}" to "}", "\\|" to "‖",
    "\\ll" to "≪", "\\gg" to "≫", "\\lceil" to "⌈", "\\rceil" to "⌉", "\\lfloor" to "⌊", "\\rfloor" to "⌋"
).sortedByDescending { it.first.length }

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
