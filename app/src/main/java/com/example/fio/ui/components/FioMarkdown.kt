package com.example.fio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 *
 * Math (LaTeX) is rendered as real typography anywhere in the reply — wrapped
 * in $…$ or written raw. `frac((a+b),(c))`-style commands become actual stacked
 * fractions (paragraph split into FlowRow segments; see MathRenderer.kt);
 * Greek letters, ∫, ×, superscripts etc. become real characters.
 * Unterminated markdown (mid-stream `**`, open fences) shows as literal text,
 * never flicker.
 *
 * Pure function of `text`: recomposition only happens when the message grows.
 */
@Composable
fun FioMarkdown(
    text: String,
    baseFontSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier,
    colors: FioCustomColors = LocalFioColors.current
) {
    val blocks = remember(text) {
        parseBlocks(
            collapseEscapedCommands(stripMathDelimiters(joinDisplayMath(text.lines())))
        )
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

/**
 * Models sometimes double-escape LaTeX commands (`\\hat{y}`) while writing
 * prose. TeX row separators in matrices are followed by whitespace, so only
 * collapsing `\\` directly before a letter is safe.
 */
private val DOUBLE_ESCAPED_CMD = Regex("""\\\\(?=[A-Za-z])""")

private fun collapseEscapedCommands(lines: List<String>): List<String> {
    var inFence = false
    return lines.map { line ->
        when {
            line.trimStart().startsWith("```") -> {
                inFence = !inFence
                line
            }
            inFence -> line
            else -> line.replace(DOUBLE_ESCAPED_CMD, "\\")
        }
    }
}

/**
 * AI models write display math as `$$` on its own line with the equation on
 * following lines. The line-based block parser would then render each lone
 * `$$` literally (dollar signs around answers), so re-join every multi-line
 * `$$…$$` block into one line before parsing.
 */
private fun joinDisplayMath(all: List<String>): List<String> {
    val out = mutableListOf<String>()
    var open = false
    val body = StringBuilder()
    for (raw in all) {
        val t = raw.trim()
        if (!open) {
            if (t.startsWith("$$") && !t.removePrefix("$$").contains("$$")) {
                open = true
                body.setLength(0)
                body.append(t.removePrefix("$$").trim())
            } else {
                out += raw
            }
        } else {
            val end = t.indexOf("$$")
            if (end >= 0) {
                if (t.substring(0, end).isNotBlank()) body.append(' ').append(t.substring(0, end).trim())
                out += "$$ $body $$".trim()
                open = false
            } else if (t.isNotBlank()) {
                body.append(' ').append(t)
            }
        }
    }
    if (open) out += "$$ $body $$".trim() // half-streamed block: show content
    return out
}

/**
 * Globally removes LaTeX math delimiters while keeping their contents:
 * `$$…$$`, `$…$`, `\(…\)` and `\[…\]`. The tokenizer renders bare LaTeX
 * identically, so stripping the wrappers here guarantees no dollar sign can
 * ever leak into any block type (paragraphs, headings, quotes, tables) —
 * pairing in the inline scanner otherwise fails when a match spans lines in
 * streaming text. Escaped dollars (\$) are preserved literally.
 */
private fun stripMathDelimiters(lines: List<String>): List<String> {
    val guard = "\u0000"
    var text = lines.joinToString("\n").replace("\\$", guard)
    text = Regex("""\\\[([\s\S]*?)\\\]""").replace(text) { it.groupValues[1] }
    text = Regex("""\\\(([\s\S]*?)\\\)""").replace(text) { it.groupValues[1] }
    text = Regex("""\$\$([\s\S]*?)\$\$""").replace(text) { it.groupValues[1] }
    text = Regex("""\$([^\$\n]*?)\$""").replace(text) { it.groupValues[1].trim() }
    return text.replace(guard, "$").split("\n")
}

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

/**
 * One line of rich text (markdown + math). Paragraphs containing stacked
 * fractions render as FlowRow segments (text runs + fraction slots); plain
 * ones render through a single Text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RichLine(
    source: String,
    colors: FioCustomColors,
    base: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit? = null
) {
    // Standalone math lines: real TeX typesetting via JLaTeXMath — complete
    // characters, \frac/\sum/\int/\boxed/fonts. Rendered at its natural size
    // and centered like a professional AI app: prominent typography, generous
    // spacing; over-long equation chains become a swipeable full-width row
    // instead of shrinking. Legacy renderer remains the fallback for anything
    // the engine cannot parse (e.g. mid-stream).
    if (isPureMathParagraph(source)) {
        val px = with(LocalDensity.current) { (base.value * 1.25f).sp.toPx() }
        val argb = colors.textPrimary.toArgb()
        val bmp = remember(source, px, argb) { latexBitmap(source.trim(), px, argb) }
        if (bmp != null) {
            val (wDp, hDp) = with(LocalDensity.current) { bmp.width.toDp() to bmp.height.toDp() }
            val screenW = LocalConfiguration.current.screenWidthDp.dp
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (wDp >= screenW * 0.92f) {
                    // Wider than the conversation column: scroll sideways.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .width(wDp)
                                .height(hDp)
                        )
                    }
                } else {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .width(wDp)
                            .height(hDp)
                    )
                }
            }
            return
        }
        // TeX parse failed — continue to the classic renderer below.
    }

    if (hasStackedFraction(source) || hasInlineMathExpr(source) || source.contains('\\')) {
        InlineMathText(source, colors, base, fontWeight, lineHeight, modifier)
    } else {
        val annotated = remember(source, colors) { buildFullLinear(source, colors) }
        Text(
            text = annotated,
            color = colors.textPrimary,
            fontSize = base,
            fontWeight = fontWeight,
            lineHeight = lineHeight ?: TextUnit.Unspecified,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MathFlowRow(
    source: String,
    colors: FioCustomColors,
    base: TextUnit,
    fontWeight: FontWeight?,
    lineHeight: TextUnit?,
    modifier: Modifier = Modifier
) {
    val segments = remember(source, colors) { buildMathSegments(source, colors) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MathSegment.Math -> Text(
                    text = segment.text,
                    color = colors.textPrimary,
                    fontSize = base,
                    fontWeight = fontWeight,
                    lineHeight = lineHeight ?: TextUnit.Unspecified,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                is MathSegment.Frac -> FractionView(
                    num = segment.num,
                    den = segment.den,
                    colors = colors,
                    base = base,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                is MathSegment.Expr -> InlineMathView(
                    latex = segment.latex,
                    colors = colors,
                    base = base,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun BlockView(block: Block, colors: FioCustomColors, base: TextUnit) {
    when (block) {
        is Block.Paragraph -> RichLine(
            source = block.lines.joinToString(" "),
            colors = colors,
            base = base
        )
        is Block.Heading -> {
            val headingText = block.text.replace("**", "").replace("`", "")
            RichLine(
                source = headingText,
                colors = colors,
                base = headingSize[minOf(block.level, 6)] ?: base,
                fontWeight = when (block.level) {
                    1, 2, 3, 4 -> FontWeight.SemiBold
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
                    RichLine(
                        source = quoteLine,
                        colors = colors,
                        base = base
                    )
                }
            }
        }
        is Block.ListItem -> {
            val marker = block.ordinal ?: "•"
            val indent = if (block.ordinal == null) 8.dp else (marker.length + 1).dp
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = marker,
                    color = colors.textSecondary,
                    fontSize = base
                )
                Box(modifier = Modifier.width(indent))
                Box(modifier = Modifier.weight(1f)) {
                    RichLine(source = block.text, colors = colors, base = base)
                }
            }
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
                    RichLine(
                        source = table.header.getOrNull(c) ?: "",
                        colors = colors,
                        base = base,
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
                    // Cells flow through the same rich pipeline as paragraphs,
                    // so \frac, ^, tables of formulas like "a(1 - a)" render
                    // as real math — matching pro AI chat interfaces.
                    RichLine(
                        source = row.getOrNull(c) ?: "",
                        colors = colors,
                        base = base,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            if (index < table.rows.lastIndex) Divider(color = colors.border)
        }
    }
}
