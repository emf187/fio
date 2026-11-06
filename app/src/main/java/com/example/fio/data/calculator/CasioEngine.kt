package com.example.fio.data.calculator

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

/**
 * Casio fx-570/991 ES-family scientific calculator engine, ported from the
 * CasioEmuX emulator project's behavior into pure Kotlin.
 *
 * Reproduces the ES calculator's conventions:
 *  - Functions auto-open parentheses; unmatched parentheses auto-close on [=]
 *  - Implicit multiplication binds tighter than explicit ÷ (Casio quirk:
 *    1÷2√<4>  =  1/(2·√4) = 0.25, while 1÷2×√<4> = 1)
 *  - Unary minus is low precedence (−2² = −4, like the real one)
 *  - x² / x³ / x⁻¹ postfix keys sit above powers
 *  - Results round to 10 significant digits (0.1+0.2 → 0.3 exactly)
 *  - DEG / RAD / GRA angle modes for trig
 *  - "Math ERROR" / "Syntax ERROR" status strings
 */

object CasioEngine {

    enum class AngleMode { DEG, RAD, GRA }

    /** One calculator token — exactly what a key press inserts. */
    sealed class Tok {
        /** Digit run, e.g. "123", "0.5". */
        data class Num(val text: String) : Tok()
        object Pi : Tok()
        /** Natural constant e. */
        object E : Tok()
        object Ans : Tok()
        object Open : Tok()
        object Close : Tok()
        /** '+', '−', '×', '÷', '^'. Unary minus resolved by the parser. */
        data class Op(val sym: Char) : Tok()
        /** Postfix x² / x³ / x⁻¹. */
        data class Postfix(val id: String) : Tok()
        /** sin/cos/tan/asin/acos/atan/log/ln/pow10/exp/sqrt/cbrt — auto-opened paren. */
        data class Func(val id: String) : Tok()
    }

    class CalcException(val kind: String) : Exception()

    // ------------------------------------------------------------- token labels

    /** How a token renders on the calculator display. */
    fun label(token: Tok): String = when (token) {
        is Tok.Num -> token.text
        Tok.Pi -> "π"
        Tok.E -> "e"
        Tok.Ans -> "Ans"
        Tok.Open -> "("
        Tok.Close -> ")"
        is Tok.Op -> when (token.sym) {
            '×' -> "×"
            '÷' -> "÷"
            '−' -> "−"
            else -> token.sym.toString()
        }
        is Tok.Postfix -> when (token.id) {
            "sq" -> "²"
            "cube" -> "³"
            "inv" -> "⁻¹"
            else -> token.id
        }
        is Tok.Func -> when (token.id) {
            "asin" -> "sin⁻¹"
            "acos" -> "cos⁻¹"
            "atan" -> "tan⁻¹"
            "pow10" -> "10^"
            "exp" -> "e^"
            "sqrt" -> "√"
            "cbrt" -> "³√"
            else -> token.id
        }
    }

    // ---------------------------------------------------------------- evaluate

    /** Result of evaluating: value (for Ans) plus display text or error. */
    data class EvalResult(val value: Double?, val display: String, val error: String?)

    fun evaluate(tokens: List<Tok>, angle: AngleMode, ans: Double?): EvalResult {
        // Casio auto-closes unmatched parentheses.
        val src = ArrayList<Tok>(tokens)
        var opens = 0
        for (t in tokens) {
            if (t is Tok.Open) opens++
            else if (t is Tok.Close) opens--
        }
        if (sourcesEmpty(tokens)) return EvalResult(null, "", null)
        repeat(maxOf(0, opens)) { src.add(Tok.Close) }
        val parser = Parser(src, angle, ans)
        return parser.run()
    }

    private fun sourcesEmpty(tokens: List<Tok>): Boolean {
        if (tokens.isEmpty()) return true
        // A lone Ans key (e.g. after AC) is a valid quick recall.
        return tokens.size == 1 && tokens[0] is Tok.Open
    }

    private class Parser(
        private val src: List<Tok>,
        private val angle: AngleMode,
        private val ans: Double?
    ) {
        private var pos = 0

        fun run(): EvalResult {
            return try {
                if (pos >= src.size) throw CalcException("Syntax ERROR")
                val value = add()
                if (pos < src.size) {
                    // Anything unconsumed (e.g. stray ")") is invalid input.
                    throw CalcException("Syntax ERROR")
                }
                if (value.isNaN() || value.isInfinite()) {
                    EvalResult(null, "Math ERROR", "Math ERROR")
                } else {
                    EvalResult(value, formatResult(value), null)
                }
            } catch (e: CalcException) {
                EvalResult(null, e.kind, e.kind)
            } catch (e: Exception) {
                EvalResult(null, "Math ERROR", "Math ERROR")
            }
        }

        /** add := unary (('+'|'−') unary)* — '−' here is binary after the first. */
        private fun add(): Double {
            var left = unary()
            while (pos < src.size) {
                val t = src[pos]
                if (t is Tok.Op && (t.sym == '+' || t.sym == '−')) {
                    pos++
                    val right = unary()
                    left = if (t.sym == '+') left + right else left - right
                } else break
            }
            return left
        }

        /** Handles the leading unary minus of each term (low precedence, Casio-style). */
        private fun unary(): Double {
            if (pos < src.size) {
                val t = src[pos]
                if (t is Tok.Op && t.sym == '−') {
                    pos++
                    return -unary()
                }
            }
            return multDiv()
        }

        /** multDiv := imperative (('*'|'÷') imperative)* */
        private fun multDiv(): Double {
            var left = implicit()
            while (pos < src.size) {
                val t = src[pos]
                if (t is Tok.Op && (t.sym == '×' || t.sym == '÷')) {
                    pos++
                    val right = implicit()
                    if (t.sym == '×') left *= right
                    else {
                        if (right == 0.0) throw CalcException("Math ERROR")
                        left /= right
                    }
                } else break
            }
            return left
        }

        /**
         * implicit := power ( <juxtaposed> power )* — implicit multiplication
         * (2π, 2sin(, )(, 2(5+1)) has HIGHER precedence than explicit ×/÷,
         * reproducing the Casio ES behavior.
         */
        private fun implicit(): Double {
            var left = power()
            while (pos < src.size) {
                if (startsAtom(src[pos])) {
                    val right = power()
                    left *= right
                } else break
            }
            return left
        }

        /** power := postfix ( '^' signedRhs )? — right-associative... */
        private fun power(): Double {
            val base = postfix()
            if (pos < src.size) {
                val t = src[pos]
                if (t is Tok.Op && t.sym == '^') {
                    pos++
                    // Allow a negative exponent directly: 2^−3 (Casio accepts it).
                    var sign = 1.0
                    if (pos < src.size) {
                        val nt = src[pos]
                        if (nt is Tok.Op && nt.sym == '−') {
                            pos++
                            sign = -1.0
                        }
                    }
                    val exponent = power()
                    val result = base.pow(sign * exponent)
                    if (result.isNaN()) throw CalcException("Math ERROR")
                    return result
                }
            }
            return base
        }

        /** postfix := atom ( '²' | '³' | '⁻¹' )* — the squared keys bind tightest. */
        private fun postfix(): Double {
            var value = atom()
            while (pos < src.size) {
                val t = src[pos]
                if (t is Tok.Postfix) {
                    pos++
                    value = when (t.id) {
                        "sq" -> value * value
                        "cube" -> {
                            val r = value.pow(3)
                            r
                        }
                        "inv" -> {
                            if (value == 0.0) throw CalcException("Math ERROR")
                            1.0 / value
                        }
                        else -> value
                    }
                    if (value.isNaN() || value.isInfinite()) throw CalcException("Math ERROR")
                } else break
            }
            return value
        }

        private fun atom(): Double {
            if (pos >= src.size) throw CalcException("Syntax ERROR")
            return when (val t = src[pos]) {
                is Tok.Num -> {
                    pos++
                    t.text.toDoubleOrNull() ?: throw CalcException("Syntax ERROR")
                }
                Tok.Pi -> {
                    pos++
                    PI
                }
                Tok.E -> {
                    pos++
                    E
                }
                Tok.Ans -> {
                    pos++
                    ans ?: 0.0
                }
                is Tok.Open -> {
                    pos++
                    val v = add()
                    if (pos < src.size && src[pos] is Tok.Close) pos++
                    else throw CalcException("Syntax ERROR")
                    v
                }
                is Tok.Close -> throw CalcException("Syntax ERROR")
                is Tok.Postfix -> throw CalcException("Syntax ERROR")
                is Tok.Func -> {
                    pos++
                    // Functions always open a paren (inserted together with the key).
                    if (pos >= src.size || src[pos] !is Tok.Open) throw CalcException("Syntax ERROR")
                    pos++
                    val arg = add()
                    if (pos < src.size && src[pos] is Tok.Close) pos++
                    else throw CalcException("Syntax ERROR")
                    applyFunc(t.id, arg)
                }
                is Tok.Op -> throw CalcException("Syntax ERROR")
            }
        }

        private fun applyFunc(id: String, x: Double): Double {
            fun toRad(degOrGrad: Double): Double = when (angle) {
                AngleMode.DEG -> degOrGrad * PI / 180.0
                AngleMode.RAD -> degOrGrad
                AngleMode.GRA -> degOrGrad * PI / 200.0
            }
            fun fromRad(rad: Double): Double = when (angle) {
                AngleMode.DEG -> rad * 180.0 / PI
                AngleMode.RAD -> rad
                AngleMode.GRA -> rad * 200.0 / PI
            }
            return when (id) {
                "sin" -> sin(toRad(x))
                "cos" -> cos(toRad(x))
                "tan" -> {
                    // Casio math-error on π/2-style poles.
                    val v = cos(toRad(x))
                    if (abs(v) < 1e-15) throw CalcException("Math ERROR")
                    sin(toRad(x)) / v
                }
                "asin" -> {
                    if (x < -1.0 || x > 1.0) throw CalcException("Math ERROR")
                    fromRad(asin(x))
                }
                "acos" -> {
                    if (x < -1.0 || x > 1.0) throw CalcException("Math ERROR")
                    fromRad(acos(x))
                }
                "atan" -> fromRad(atan(x))
                "log" -> {
                    if (x <= 0) throw CalcException("Math ERROR")
                    log10(x)
                }
                "ln" -> {
                    if (x <= 0) throw CalcException("Math ERROR")
                    ln(x)
                }
                "pow10" -> {
                    val v = 10.0.pow(x)
                    v
                }
                "exp" -> exp(x)
                "sqrt" -> {
                    if (x < 0) throw CalcException("Math ERROR")
                    sqrt(x)
                }
                "cbrt" -> {
                    // Real cube root of negatives (Java's cbrt does this).
                    val v = x.pow(1.0 / 3.0)
                    if (x < 0) -(-x).pow(1.0 / 3.0) else v
                }
                else -> throw CalcException("Syntax ERROR")
            }
        }

        private fun startsAtom(t: Tok): Boolean = when (t) {
            is Tok.Num, Tok.Pi, Tok.E, Tok.Ans, is Tok.Open, is Tok.Func -> true
            else -> false
        }
    }

    // -------------------------------------------------------------- formatting

    /** Rounds to 10 significant digits like the ES hardware (0.1+0.2 → 0.3). */
    private fun round10(x: Double): Double = when {
        x.isNaN() || x.isInfinite() -> x
        else -> {
            val r = BigDecimal(x).round(MathContext(10)).toDouble()
            // Sub-picorad trig residue snaps to a clean zero (tan 180° → 0).
            if (abs(r) < 1e-12) 0.0 else r
        }
    }

    /** Casio-style result text: plain decimals, ×10ⁿ beyond range, comma groups. */
    fun formatResult(x: Double): String {
        val r = round10(x)
        if (r.isNaN() || r.isInfinite()) return "Math ERROR"
        if (r == 0.0) return "0"
        val ax = abs(r)
        return when {
            ax >= 1e10 || ax < 1e-9 -> sciString(r)
            else -> groupDigits(plainString(r))
        }
    }

    private fun plainString(r: Double): String =
        BigDecimal(r).round(MathContext(10)).stripTrailingZeros().toPlainString()

    private fun sciString(r: Double): String {
        // Format via java's scientific output, then restyle: 1.500000000e+03 → 1.5×10³
        val formatted = String.format(java.util.Locale.ROOT, "%.9e", round10(r))
        val eIdx = formatted.indexOf('e')
        var mantissa = formatted.substring(0, eIdx).trimEnd('0').trimEnd('.')
        var exponent = formatted.substring(eIdx + 1).toInt()
        if (mantissa == "1") mantissa = ""
        return if (mantissa.isEmpty()) "1×10${superscript(exponent)}"
        else "${mantissa}×10${superscript(exponent)}"
    }

    private fun superscript(v: Int): String {
        val digits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        val s = v.toString()
        if (s.startsWith("-")) return "⁻" + s.substring(1).map { digits[it - '0'] }.joinToString("")
        return s.map { digits[it - '0'] }.joinToString("")
    }

    private fun groupDigits(plain: String): String {
        val neg = plain.startsWith("-")
        val body = if (neg) plain.substring(1) else plain
        val dot = body.indexOf('.')
        val intPart = if (dot >= 0) body.substring(0, dot) else body
        val fracPart = if (dot >= 0) body.substring(dot) else ""
        if (intPart.length <= 3) return plain
        val grouped = StringBuilder()
        val lead = intPart.length % 3
        if (lead > 0) grouped.append(intPart.substring(0, lead))
        for (i in lead until intPart.length step 3) {
            if (grouped.isNotEmpty()) grouped.append(',')
            grouped.append(intPart.substring(i, (i + 3).coerceAtMost(intPart.length)))
        }
        return (if (neg) "-" else "") + grouped.toString() + fracPart
    }
}
