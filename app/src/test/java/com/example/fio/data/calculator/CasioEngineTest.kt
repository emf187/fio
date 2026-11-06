package com.example.fio.data.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local correctness tests for the Casio engine port (fx-570/991 ES behavior).
 */
class CasioEngineTest {

    private val deg = CasioEngine.AngleMode.DEG
    private val rad = CasioEngine.AngleMode.RAD

    private fun Toks(vararg parts: CasioEngine.Tok) = parts.toList()

    private fun num(n: String) = CasioEngine.Tok.Num(n)
    private val op = { c: Char -> CasioEngine.Tok.Op(c) }
    private val open = CasioEngine.Tok.Open
    private val close = CasioEngine.Tok.Close
    private val fx = { id: String -> CasioEngine.Tok.Func(id) }

    private fun expect(expr: List<CasioEngine.Tok>, expectedDisplay: String, angle: CasioEngine.AngleMode = deg, ans: Double? = null) {
        val res = CasioEngine.evaluate(expr, angle, ans)
        assertEquals("expr=${expr.map { it::class.simpleName }}", expectedDisplay, res.display)
        assertNull(res.error)
    }

    private fun expectError(expr: List<CasioEngine.Tok>, kind: String, angle: CasioEngine.AngleMode = deg) {
        val res = CasioEngine.evaluate(expr, angle, null)
        assertEquals(kind, res.error)
    }

    @Test
    fun basicArithmetic() {
        expect(Toks(num("1"), op('+'), num("2"), op('×'), num("3")), "7")
        expect(Toks(num("10"), op('÷'), num("4")), "2.5")
        // (−) inserts the same '−' token; a leading '−' parses as negation: 2−(−3) = 5
        expect(Toks(num("2"), op('−'), op('−'), num("3")), "5")
    }

    @Test
    fun digitKeysBuildRunningNumbers() {
        // Digits typed one key at a time must build ONE number ("12" = 12,
        // never 1×2) — regression test for the press-replacing bug.
        var e = CasioEngine.appendTokens(emptyList(), Toks(num("1")))
        e = CasioEngine.appendTokens(e, Toks(num("2")))
        e = CasioEngine.appendTokens(e, Toks(num("3")))
        expect(e, "123")
        // "123+34" typed digit-wise → 157
        e = CasioEngine.appendTokens(e, Toks(op('+')))
        e = CasioEngine.appendTokens(e, Toks(num("3")))
        e = CasioEngine.appendTokens(e, Toks(num("4")))
        expect(e, "157")
        // Also verify the merged token evaluates exactly like its typed text.
        expect(e, CasioEngine.evaluate(Toks(num("123"), op('+'), num("34")), deg, null).display)
    }

    @Test
    fun decimalTyping() {
        // 0.5 typed key by key
        var e = CasioEngine.appendTokens(emptyList(), Toks(num("0")))
        e = CasioEngine.appendTokens(e, Toks(num(".")))
        e = CasioEngine.appendTokens(e, Toks(num("5")))
        expect(e, "0.5")
        // leading dot alone, then digits: ".5" evaluates like the hardware
        var d = CasioEngine.appendTokens(emptyList(), Toks(num(".")))
        d = CasioEngine.appendTokens(d, Toks(num("5")))
        expect(d, "0.5")
        // a second dot inside the same number is a dead key: ".57"
        d = CasioEngine.appendTokens(d, Toks(num(".")))
        d = CasioEngine.appendTokens(d, Toks(num("7")))
        expect(d, "0.57")
    }

    @Test
    fun degreesTrig() {
        expect(Toks(fx("sin"), open, num("30"), close), "0.5")
        expect(Toks(fx("cos"), open, num("60"), close), "0.5")
        expect(Toks(fx("tan"), open, num("45"), close), "1")
        // Ten-digit rounding snaps floating-point residue: tan 180° → 0
        expect(Toks(fx("tan"), open, num("180"), close), "0")
        // Inverse
        expect(Toks(fx("asin"), open, num("0.5"), close), "30")
    }

    @Test
    fun radiansMode() {
        expect(Toks(fx("sin"), open, CasioEngine.Tok.Pi, op('÷'), num("2"), close), "1", rad)
        expect(Toks(CasioEngine.Tok.Pi, op('÷'), num("2")), "1.570796327", rad)
    }

    @Test
    fun implicitMultiplicationQuirk() {
        // The famous Casio behavior: implicit × binds tighter than explicit ÷.
        expect(Toks(num("1"), op('÷'), num("2"), fx("sqrt"), open, num("9"), close), "0.1666666667")
        // ...while explicit × does not:
        expect(Toks(num("1"), op('÷'), num("2"), op('×'), fx("sqrt"), open, num("9"), close), "1.5")
        // 2π, juxtaposed parens:
        expect(Toks(num("2"), CasioEngine.Tok.Pi), "6.283185307")
        expect(Toks(num("2"), open, num("3"), op('+'), num("4"), close), "14")
        // 2^2π = (2^2)·π per ES priority (π is implicit-mult level, below ^)
        expect(Toks(num("2"), op('^'), num("2"), CasioEngine.Tok.Pi), "12.56637061")
    }

    @Test
    fun powersAndPostfix() {
        expect(Toks(num("2"), CasioEngine.Tok.Postfix("sq")), "4")
        expect(Toks(open, num("2"), op('+'), num("3"), close, CasioEngine.Tok.Postfix("sq")), "25")
        expect(Toks(num("2"), CasioEngine.Tok.Postfix("inv")), "0.5")
        expect(Toks(num("2"), op('^'), num("2"), op('^'), num("3")), "256") // right-assoc: 2^(2^3)
        // −2² = −4 (unary minus is low precedence, ES-style)
        expect(Toks(op('−'), num("2"), CasioEngine.Tok.Postfix("sq")), "-4")
        expect(Toks(num("2"), CasioEngine.Tok.Postfix("sq"), op('^'), num("2")), "16")
        // 2^3×2: ^ sits above explicit × → 8×2 = 16
        expect(Toks(num("2"), op('^'), num("3"), op('×'), num("2")), "16")
    }

    @Test
    fun exponentialsAndRoots() {
        expect(Toks(fx("sqrt"), open, num("16"), close), "4")
        expect(Toks(fx("cbrt"), open, num("27"), close), "3")
        expect(Toks(fx("cbrt"), open, op('−'), num("8"), close), "-2")
        expect(Toks(fx("log"), open, num("1000"), close), "3")
        expect(Toks(fx("ln"), open, CasioEngine.Tok.E, close), "1")
        expect(Toks(fx("pow10"), open, num("3"), close), "1,000")
        expect(Toks(fx("exp"), open, num("0"), close), "1")
    }

    @Test
    fun autoCloseParens() {
        expect(Toks(fx("sin"), open, num("30")), "0.5")
        expect(Toks(open, num("1"), op('+'), num("2")), "3")
    }

    @Test
    fun ansChain() {
        expect(Toks(CasioEngine.Tok.Ans, op('+'), num("5")), "10", ans = 5.0)
        expect(Toks(CasioEngine.Tok.Ans), "5", ans = 5.0)
    }

    @Test
    fun errors() {
        expectError(Toks(num("1"), op('÷'), num("0")), "Math ERROR")
        expectError(Toks(fx("log"), open, op('−'), num("1"), close), "Math ERROR")
        expectError(Toks(fx("sqrt"), open, op('−'), num("4"), close), "Math ERROR")
        expectError(Toks(fx("asin"), open, num("2"), close), "Math ERROR")
        expectError(Toks(num("2"), close), "Syntax ERROR")
        expectError(Toks(open, num("2"), op('+')), "Syntax ERROR")
        expectError(Toks(op('+')), "Syntax ERROR")
    }

    @Test
    fun displayFormatting() {
        assertEquals("0.3", CasioEngine.formatResult(0.1 + 0.2))
        assertEquals("0", CasioEngine.formatResult(-0.0))
        assertEquals("1", CasioEngine.formatResult(0.9999999999 + 0.0000000001))
        assertEquals("1,234,567.89", CasioEngine.formatResult(1234567.89))
        assertEquals("100,000", CasioEngine.formatResult(100000.0))
        assertEquals("1×10¹⁰", CasioEngine.formatResult(1e10))
        assertEquals("1.5×10¹²", CasioEngine.formatResult(1.5e12))
        // Casio Normal-1 display range: plain down to 1e-9 (exclusive), sci below/at 1e10
        assertEquals("0.000000001", CasioEngine.formatResult(1e-9))
        assertEquals("0.000000015", CasioEngine.formatResult(1.5e-8))
        assertEquals("5×10⁻¹⁰", CasioEngine.formatResult(1e-9 / 2))
        assertEquals("5", CasioEngine.formatResult(4.9999999999))
    }

    @Test
    fun tokenLabels() {
        // Func label omits the auto-opened paren; the adjacent Open token shows it.
        assertEquals("sin", CasioEngine.label(CasioEngine.Tok.Func("sin")))
        assertEquals("√", CasioEngine.label(CasioEngine.Tok.Func("sqrt")))
        assertEquals("sin⁻¹", CasioEngine.label(CasioEngine.Tok.Func("asin")))
        assertEquals("π", CasioEngine.label(CasioEngine.Tok.Pi))
        assertEquals("²", CasioEngine.label(CasioEngine.Tok.Postfix("sq")))
    }

    @Test
    fun graMode() {
        val gra = CasioEngine.AngleMode.GRA
        expect(Toks(fx("sin"), open, num("100"), close), "1", gra)
        // asin(0.5) = 30° = 100/3 grad
        expect(Toks(fx("asin"), open, num("0.5"), close), "33.33333333", gra)
    }

    @Test
    fun deepPrecedenceMix() {
        // sin(30)+2³×2 = 0.5+16 = 16.5
        expect(
            Toks(fx("sin"), open, num("30"), close, op('+'), num("2"), op('^'), num("3"), op('×'), num("2")),
            "16.5"
        )
        // (1+2)(3+4) = 21 via juxtaposition
        expect(Toks(open, num("1"), op('+'), num("2"), close, open, num("3"), op('+'), num("4"), close), "21")
        // √(4)² = 4
        expect(Toks(fx("sqrt"), open, num("4"), close, CasioEngine.Tok.Postfix("sq")), "4")
    }
}
