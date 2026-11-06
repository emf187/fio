package com.example.fio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.calculator.CasioEngine
import com.example.fio.ui.components.FioCard
import com.example.fio.ui.theme.FioCustomColors
import com.example.fio.ui.theme.LocalFioColors

/**
 * Scientific Calculator — native port of the CasioEmuX calculator into Fio.
 *
 * Mirrors the fx-570/991 ES-PLUS experience (Shift second functions, Ans
 * memory, DEG/RAD/GRA, auto-closing parentheses, Math/Syntax errors,
 * 10-digit rounding) while every surface and key uses Fio's theme.
 */
@Composable
fun CalculatorScreen() {
    val colors = LocalFioColors.current

    var expr by remember { mutableStateOf<List<CasioEngine.Tok>>(emptyList()) }
    var shiftOn by remember { mutableStateOf(false) }
    var angleMode by remember { mutableStateOf(CasioEngine.AngleMode.DEG) }
    var ansValue by remember { mutableStateOf<Double?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // (expression, result display) pairs, newest first; browsing index, -1 = live.
    var history by remember { mutableStateOf(listOf<Pair<List<CasioEngine.Tok>, String>>()) }
    var historyIndex by remember { mutableStateOf(-1) }

    val exprScroll = rememberScrollState()
    val exprText = expr.joinToString("") { CasioEngine.label(it) }

    // Keep the input end in view as the expression grows (Casio-style display).
    LaunchedEffect(exprText.length) {
        exprScroll.animateScrollTo(exprScroll.maxValue)
    }

    fun press(tokens: List<CasioEngine.Tok>) {
        // After '=', digits/dot/π/( start fresh, everything else chains on Ans
        // — exactly the hardware behavior.
        val hadResult = resultText != null || errorText != null
        val startsFresh = tokens.size == 1 && when (tokens[0]) {
            is CasioEngine.Tok.Num, is CasioEngine.Tok.Pi, is CasioEngine.Tok.E,
            is CasioEngine.Tok.Open -> true
            else -> false
        }
        val base: List<CasioEngine.Tok> = when {
            hadResult && startsFresh -> emptyList()
            hadResult -> listOf(CasioEngine.Tok.Ans)
            else -> expr
        }
        // appendTokens keeps the running expression and merges digit keys,
        // so typed numbers stay whole and functions render as you type.
        expr = CasioEngine.appendTokens(base, tokens)
        resultText = null
        errorText = null
        historyIndex = -1
    }

    fun evaluateNow() {
        if (expr.isEmpty()) return
        val res = CasioEngine.evaluate(expr, angleMode, ansValue)
        if (res.error != null) {
            errorText = res.error
        } else {
            resultText = res.display
            ansValue = res.value
            history = listOf(expr to res.display) + history.take(24)
        }
        historyIndex = -1
    }

    fun browseHistory(step: Int) {
        if (history.isEmpty()) return
        val next = when {
            historyIndex == -1 && step < 0 -> 0
            historyIndex == -1 && step > 0 -> history.lastIndex
            else -> (historyIndex + step).coerceIn(0, history.lastIndex)
        }
        historyIndex = next
        expr = history[next].first
        resultText = null
        errorText = null
    }

    fun onKey(key: KeyDef) {
        val act = if (shiftOn) key.shiftAct ?: key.act else key.act
        when (act) {
            is KeyAct.ToggleShift -> shiftOn = !shiftOn
            is KeyAct.Tok -> { shiftOn = false; press(act.tokens) }
            is KeyAct.Delete -> { shiftOn = false; if (expr.isNotEmpty()) expr = expr.dropLast(1); historyIndex = -1 }
            is KeyAct.Clear -> { shiftOn = false; expr = emptyList(); resultText = null; errorText = null; historyIndex = -1 }
            is KeyAct.Equal -> { shiftOn = false; evaluateNow() }
            is KeyAct.Up -> { shiftOn = false; browseHistory(-1) }
            is KeyAct.Down -> { shiftOn = false; browseHistory(+1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("calculator_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ------------------------------------------------------- display unit
        FioCard(
            backgroundColor = colors.surfaceElevated,
            borderColor = colors.border,
            padding = PaddingValues(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        text = "SHIFT",
                        show = shiftOn,
                        fg = Color.White,
                        bg = colors.warning,
                        tag = "calc_shift_indicator"
                    )
                    StatusChip(
                        text = angleMode.name,
                        show = true,
                        fg = colors.textMuted,
                        bg = colors.surface,
                        tag = "calc_angle_indicator"
                    )
                }
                Text(
                    text = ansValue?.let { "Ans=${CasioEngine.formatResult(it)}" } ?: "",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.size(6.dp))

            // Input expression, right-aligned like the hardware LCD.
            Text(
                text = exprText.ifBlank { " " },
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 24.sp,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(exprScroll)
            )

            Spacer(modifier = Modifier.size(4.dp))

            val shownError = errorText
            val shownResult = resultText
            when {
                shownError != null -> Text(
                    text = shownError,
                    color = colors.error,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().testTag("calc_error")
                )
                shownResult != null -> Text(
                    text = shownResult,
                    color = colors.textPrimary,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().testTag("calc_result")
                )
                else -> Text(
                    text = livePreviewText(expr, angleMode, ansValue)?.let { "≈ $it" } ?: " ",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().testTag("calc_preview")
                )
            }
        }

        // ------------------------------------------------ angle mode selector
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CasioEngine.AngleMode.entries.forEach { mode ->
                val selected = mode == angleMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.primaryContainer else colors.surface)
                        .border(
                            1.dp,
                            if (selected) colors.primary else colors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { angleMode = mode }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("calc_angle_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = mode.name,
                        color = if (selected) colors.primary else colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // -------------------------------------------------------- the keypad
        keypadRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { key ->
                    CalcKey(
                        key = key,
                        shiftOn = shiftOn,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = { onKey(key) }
                    )
                }
            }
        }
    }
}

/** Small live value under the input when the expression is evaluatable. */
private fun livePreviewText(
    expr: List<CasioEngine.Tok>,
    angle: CasioEngine.AngleMode,
    ans: Double?
): String? {
    if (expr.isEmpty()) return null
    val res = CasioEngine.evaluate(expr, angle, ans)
    return res.takeIf { it.error == null && it.display.isNotBlank() }?.display
}

// ----------------------------------------------------------------- key model

private enum class KeyStyle { DIGIT, FUNC, SHIFT, AC, DEL, EQUAL }

private sealed class KeyAct {
    data class Tok(val tokens: List<CasioEngine.Tok>) : KeyAct()
    object ToggleShift : KeyAct()
    object Delete : KeyAct()
    object Clear : KeyAct()
    object Up : KeyAct()
    object Down : KeyAct()
    object Equal : KeyAct()
}

private data class KeyDef(
    val label: String,
    val act: KeyAct,
    val shiftLabel: String? = null,
    val shiftAct: KeyAct? = null,
    val style: KeyStyle = KeyStyle.FUNC
)

private fun tokKey(label: String, vararg tokens: CasioEngine.Tok) =
    KeyDef(label, KeyAct.Tok(tokens.toList()))

private fun digitKey(label: String) =
    KeyDef(label, KeyAct.Tok(listOf(CasioEngine.Tok.Num(label))), style = KeyStyle.DIGIT)

private val funcOpen = CasioEngine.Tok.Open

/** fx-ES style grid — 5 columns × 7 rows, SHIFT column unlocks second functions. */
private val keypadRows: List<List<KeyDef>> = listOf(
    listOf(
        KeyDef("SHIFT", KeyAct.ToggleShift, style = KeyStyle.SHIFT),
        KeyDef("▲", KeyAct.Up),
        KeyDef("▼", KeyAct.Down),
        KeyDef("DEL", KeyAct.Delete, style = KeyStyle.DEL),
        KeyDef("AC", KeyAct.Clear, style = KeyStyle.AC)
    ),
    listOf(
        tokKey("sin", CasioEngine.Tok.Func("sin"), funcOpen)
            .copy(shiftLabel = "sin⁻¹", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Func("asin"), funcOpen))),
        tokKey("cos", CasioEngine.Tok.Func("cos"), funcOpen)
            .copy(shiftLabel = "cos⁻¹", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Func("acos"), funcOpen))),
        tokKey("tan", CasioEngine.Tok.Func("tan"), funcOpen)
            .copy(shiftLabel = "tan⁻¹", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Func("atan"), funcOpen))),
        tokKey("x²", CasioEngine.Tok.Postfix("sq"))
            .copy(shiftLabel = "x³", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Postfix("cube")))),
        tokKey("^", CasioEngine.Tok.Op('^'))
    ),
    listOf(
        tokKey("log", CasioEngine.Tok.Func("log"), funcOpen)
            .copy(shiftLabel = "10^", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Func("pow10"), funcOpen))),
        tokKey("ln", CasioEngine.Tok.Func("ln"), funcOpen)
            .copy(shiftLabel = "e^", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Func("exp"), funcOpen))),
        tokKey("x⁻¹", CasioEngine.Tok.Postfix("inv")),
        tokKey("√", CasioEngine.Tok.Func("sqrt"), funcOpen)
            .copy(shiftLabel = "³√", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Func("cbrt"), funcOpen))),
        tokKey("Ans", CasioEngine.Tok.Ans)
            .copy(shiftLabel = "π", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.Pi)))
    ),
    listOf(
        digitKey("7"), digitKey("8"), digitKey("9"),
        tokKey("(", CasioEngine.Tok.Open),
        tokKey(")", CasioEngine.Tok.Close)
    ),
    listOf(
        digitKey("4"), digitKey("5"), digitKey("6"),
        tokKey("÷", CasioEngine.Tok.Op('÷')),
        tokKey("×", CasioEngine.Tok.Op('×'))
    ),
    listOf(
        digitKey("1"), digitKey("2"), digitKey("3"),
        tokKey("−", CasioEngine.Tok.Op('−')),
        tokKey("+", CasioEngine.Tok.Op('+'))
    ),
    listOf(
        digitKey("0"),
        tokKey(".", CasioEngine.Tok.Num("."))
            .copy(shiftLabel = "e", shiftAct = KeyAct.Tok(listOf(CasioEngine.Tok.E))),
        tokKey("(−)", CasioEngine.Tok.Op('−')),
        tokKey("×10^", CasioEngine.Tok.Op('×'), CasioEngine.Tok.Num("10"), CasioEngine.Tok.Op('^')),
        KeyDef("=", KeyAct.Equal, style = KeyStyle.EQUAL)
    )
)

// ----------------------------------------------------------------- key views

@Composable
private fun CalcKey(
    key: KeyDef,
    shiftOn: Boolean,
    colors: FioCustomColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shiftKey = key.style == KeyStyle.SHIFT
    val activeShift = shiftKey && shiftOn

    val bg: Color = when {
        activeShift -> colors.warning
        shiftKey -> colors.surface
        key.style == KeyStyle.EQUAL -> colors.primary
        key.style == KeyStyle.DIGIT -> colors.surface
        key.style == KeyStyle.AC || key.style == KeyStyle.DEL -> colors.surface
        else -> colors.surfaceElevated
    }
    val fg: Color = when {
        activeShift -> Color.White
        shiftKey -> colors.warning
        key.style == KeyStyle.EQUAL -> Color.White
        key.style == KeyStyle.AC -> colors.error
        key.style == KeyStyle.DEL -> colors.textSecondary
        key.style == KeyStyle.DIGIT -> colors.textPrimary
        else -> colors.primary
    }

    val shown = if (shiftOn && key.shiftLabel != null) key.shiftLabel else key.label

    Column(
        modifier = modifier.height(46.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(11.dp))
                .background(bg, RoundedCornerShape(11.dp))
                .border(1.dp, if (activeShift) Color.White.copy(alpha = 0.4f) else colors.border, RoundedCornerShape(11.dp))
                .clickable(onClick = onClick)
                .let { it },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shown,
                color = fg,
                fontSize = when {
                    shown.length > 4 -> 10.sp
                    shown.length > 2 -> 12.sp
                    else -> 14.sp
                },
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, show: Boolean, fg: Color, bg: Color, tag: String) {
    if (!show) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(tag)
    ) {
        Text(text = text, color = fg, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}
