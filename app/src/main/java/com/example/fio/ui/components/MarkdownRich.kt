package com.example.fio.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * Inline Markdown styling for Fio note editors and previews.
 *
 * Supported markers: `**bold**`, `*italic*`, `__underline__`, `~~strikethrough~~`, `` `code` ``
 * Matches are single-line with non-space edges; unmatched or half-formed markers render literally,
 * so unfinished typing never scrambles the text.
 */

private class MdMatch(
    val rawStart: Int,      // first delimiter char (inclusive)
    val rawEnd: Int,        // end of the whole expression (exclusive)
    val innerStart: Int,    // first styled char (inclusive)
    val innerEnd: Int,      // end of styled content (exclusive)
    val style: SpanStyle
)

private fun mdPatterns(textColor: Color): List<Pair<Regex, SpanStyle>> = listOf(
    Regex("`(\\S(?:[^`\\n]*?\\S)?)`") to SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = textColor.copy(alpha = 0.14f)
    ),
    Regex("\\*\\*(\\S(?:[^*\\n]*?\\S)?)\\*\\*") to SpanStyle(fontWeight = FontWeight.Bold),
    Regex("__(\\S(?:[^_\\n]*?\\S)?)__") to SpanStyle(textDecoration = TextDecoration.Underline),
    Regex("~~(\\S(?:[^~\\n]*?\\S)?)~~") to SpanStyle(textDecoration = TextDecoration.LineThrough),
    Regex("\\*(\\S(?:[^*\\n]*?\\S)?)\\*") to SpanStyle(fontStyle = FontStyle.Italic)
)

/** Collects non-overlapping inline-marker matches in priority order (code > bold > underline > strike > italic). */
private fun collectMarkdownMatches(raw: String, textColor: Color): List<MdMatch> {
    val taken = mutableListOf<Pair<Int, Int>>()
    val matches = mutableListOf<MdMatch>()
    for (pattern in mdPatterns(textColor)) {
        for (found in pattern.first.findAll(raw)) {
            val group = found.groups[1] ?: continue
            val start = found.range.first
            val end = found.range.last + 1
            if (taken.any { start < it.second && end > it.first }) continue
            taken.add(start to end)
            matches.add(
                MdMatch(
                    rawStart = start,
                    rawEnd = end,
                    innerStart = group.range.first,
                    innerEnd = group.range.last + 1,
                    style = pattern.second
                )
            )
        }
    }
    return matches.sortedBy { it.rawStart }
}

/**
 * Builds the styled, marker-hidden rendering:
 * the returned [TransformedText] keeps caret/selection correct via the offset mapping.
 */
fun buildMarkdownTransformed(raw: String, textColor: Color): TransformedText {
    val matches = collectMarkdownMatches(raw, textColor)
    val styleRanges = mutableListOf<Triple<Int, Int, SpanStyle>>()
    val fwd = IntArray(raw.length + 1) { raw.length }     // transformed char -> raw char
    val rev = IntArray(raw.length + 1) { 0 }              // raw char -> transformed position
    val sb = StringBuilder()

    var i = 0
    var k = 0
    var emitted = 0
    while (i < raw.length) {
        while (k < matches.size && i >= matches[k].rawEnd) k++
        val active = if (k < matches.size && i >= matches[k].rawStart) matches[k] else null
        if (active == null) {
            rev[i] = emitted
            sb.append(raw[i])
            fwd[emitted] = i
            emitted++
            i++
        } else {
            for (j in i until active.innerStart) rev[j] = emitted          // opener maps to block start
            val styleStart = emitted
            for (j in active.innerStart until active.innerEnd) {
                rev[j] = emitted
                sb.append(raw[j])
                fwd[emitted] = j
                emitted++
            }
            styleRanges.add(Triple(styleStart, emitted, active.style))
            for (j in active.innerEnd until active.rawEnd) rev[j] = emitted // closer maps to block end
            i = active.rawEnd
        }
    }
    rev[raw.length] = emitted

    val annotated: AnnotatedString = buildAnnotatedString {
        append(sb.toString())
        for ((start, end, style) in styleRanges) addStyle(style, start, end)
    }
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            rev[offset.coerceIn(0, rev.size - 1)]

        override fun transformedToOriginal(offset: Int): Int =
            fwd[offset.coerceIn(0, fwd.size - 1)]
    }
    return TransformedText(annotated, mapping)
}

/** Styled AnnotatedString for non-editable previews (note preview pane, card snippets). */
fun buildMarkdownAnnotated(raw: String, textColor: Color): AnnotatedString =
    buildMarkdownTransformed(raw, textColor).text

/** Visual transformation that hides Markdown delimiters while they style the text under the caret. */
fun markdownVisualTransformation(textColor: Color): VisualTransformation {
    // Single-entry memo per editor instance: typing re-transforms on each keystroke,
    // but plain recompositions with an unchanged value reuse the last result.
    var lastInput = "\u0000-never-matches"
    var lastResult: TransformedText? = null
    return VisualTransformation { input ->
        val raw = input.text
        if (raw == lastInput && lastResult != null) {
            lastResult!!
        } else {
            val built = buildMarkdownTransformed(raw, textColor)
            lastInput = raw
            lastResult = built
            built
        }
    }
}

/** Strips inline marker characters for plain-text snippets (note list previews). */
private val MARKER_CHARS = Regex("""\*\*|\*|__|~~|`""")

fun stripMarkdownMarkers(raw: String): String =
    raw.replace(MARKER_CHARS, "")
