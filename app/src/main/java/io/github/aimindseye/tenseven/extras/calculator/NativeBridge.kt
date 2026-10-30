// Ported from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f), sable-src/apps/r8/rust/crates/
// calculator-core and calculator-jni (Rust), Apache License 2.0. Changed: written in Kotlin with BigInteger, so the
// app needs no native library; the "OK:" and "ERR:" text protocol and the arithmetic rules are unchanged.
package io.github.aimindseye.tenseven.extras.calculator

import java.math.BigInteger
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Exact decimal arithmetic and the unary scientific functions, answered as "OK:<value>" or "ERR:<reason>". */
internal object NativeBridge {
    private enum class Failure(val code: String) {
        InvalidNumber("ERR:INVALID_NUMBER"),
        DivisionByZero("ERR:DIVIDE_BY_ZERO"),
        Overflow("ERR:OVERFLOW"),
        Domain("ERR:DOMAIN"),
    }

    private class Fail(val failure: Failure) : Exception()

    private val I128_MAX = BigInteger.ONE.shiftLeft(127) - BigInteger.ONE
    private val I128_MIN = BigInteger.ONE.shiftLeft(127).negate()

    fun selfTest(): String {
        val binary = applyBinary("0.1", 0, "0.2")
        val scientific = applyScientific("9", 0)
        return if (binary == "OK:3/10" && scientific == "OK:3") {
            "PASS:calculator-core:3/10:scientific:3"
        } else {
            "FAIL:calculator-core:$binary:$scientific"
        }
    }

    fun applyBinary(
        left: String,
        operation: Int,
        right: String,
    ): String {
        if (operation !in 0..3) return "ERR:INVALID_OPERATION"
        return try {
            val a = parseDecimal(left)
            val b = parseDecimal(right)
            "OK:" + apply(a, operation, b)
        } catch (fail: Fail) {
            fail.failure.code
        }
    }

    fun applyScientific(
        input: String,
        operation: Int,
    ): String {
        if (operation !in 0..7) return "ERR:INVALID_OPERATION"
        return try {
            "OK:" + formatScientific(scientific(input, operation))
        } catch (fail: Fail) {
            fail.failure.code
        }
    }

    /** value = numerator / denominator, lowest terms, denominator > 0, both within the signed 128-bit range. */
    private class Exact(val numerator: BigInteger, val denominator: BigInteger) {
        override fun toString() = "$numerator/$denominator"
    }

    private fun checked(value: BigInteger): BigInteger {
        if (value > I128_MAX || value < I128_MIN) throw Fail(Failure.Overflow)
        return value
    }

    private fun make(
        numerator: BigInteger,
        denominator: BigInteger,
    ): Exact {
        if (denominator.signum() == 0) throw Fail(Failure.DivisionByZero)
        if (numerator.signum() == 0) return Exact(BigInteger.ZERO, BigInteger.ONE)
        val gcd = numerator.abs().gcd(denominator.abs())
        var n = numerator.abs() / gcd
        val d = denominator.abs() / gcd
        if (numerator.signum() * denominator.signum() < 0) n = n.negate()
        return Exact(checked(n), checked(d))
    }

    private fun parseDecimal(raw: String): Exact {
        val text = raw.trim()
        if (text.isEmpty()) throw Fail(Failure.InvalidNumber)
        val negative = text.startsWith("-")
        val unsigned = if (text.startsWith("+") || text.startsWith("-")) text.substring(1) else text
        if (unsigned.isEmpty()) throw Fail(Failure.InvalidNumber)
        val parts = unsigned.split('.')
        if (parts.size > 2) throw Fail(Failure.InvalidNumber)
        val whole = parts[0]
        if (whole.isEmpty() || !whole.all { it in '0'..'9' }) throw Fail(Failure.InvalidNumber)
        val fraction = parts.getOrNull(1)
        if (fraction != null && (fraction.isEmpty() || !fraction.all { it in '0'..'9' })) throw Fail(Failure.InvalidNumber)
        val wholeValue = checked(BigInteger(whole))
        val denominator = if (fraction == null) BigInteger.ONE else checked(BigInteger.TEN.pow(fraction.length))
        val fractionValue = if (fraction == null) BigInteger.ZERO else checked(BigInteger(fraction))
        var numerator = checked(checked(wholeValue * denominator) + fractionValue)
        if (negative) numerator = checked(numerator.negate())
        return make(numerator, denominator)
    }

    private fun apply(
        a: Exact,
        operation: Int,
        b: Exact,
    ): Exact =
        when (operation) {
            0 -> make(checked(checked(a.numerator * b.denominator) + checked(b.numerator * a.denominator)), checked(a.denominator * b.denominator))
            1 -> make(checked(checked(a.numerator * b.denominator) - checked(b.numerator * a.denominator)), checked(a.denominator * b.denominator))
            2 -> make(checked(a.numerator * b.numerator), checked(a.denominator * b.denominator))
            else -> {
                if (b.numerator.signum() == 0) throw Fail(Failure.DivisionByZero)
                make(checked(a.numerator * b.denominator), checked(a.denominator * b.numerator))
            }
        }

    private fun scientific(
        text: String,
        operation: Int,
    ): Double {
        val value = text.trim().toDoubleOrNull() ?: throw Fail(Failure.InvalidNumber)
        if (!value.isFinite()) throw Fail(Failure.Overflow)
        val result =
            when (operation) {
                0 -> if (value < 0.0) throw Fail(Failure.Domain) else sqrt(value)
                1 -> value * value
                2 -> if (value == 0.0) throw Fail(Failure.DivisionByZero) else 1.0 / value
                3 -> sin(Math.toRadians(value))
                4 -> cos(Math.toRadians(value))
                5 -> tan(Math.toRadians(value))
                6 -> if (value <= 0.0) throw Fail(Failure.Domain) else ln(value)
                else -> if (value <= 0.0) throw Fail(Failure.Domain) else log10(value)
            }
        if (result.isNaN()) throw Fail(Failure.Domain)
        if (!result.isFinite()) throw Fail(Failure.Overflow)
        return result
    }

    private fun formatScientific(value: Double): String {
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        if (magnitude < 1.0e-9 || magnitude >= 1.0e12) {
            // Rust's {:.10e}: 1.2345678900e15, no plus sign and no zero padding in the exponent.
            val text = String.format(Locale.ROOT, "%.10e", value)
            val (mantissa, exponent) = text.split('e')
            return mantissa + "e" + exponent.toInt()
        }
        var rendered = String.format(Locale.ROOT, "%.12f", value)
        if (rendered.contains('.')) rendered = rendered.trimEnd('0').trimEnd('.')
        return rendered
    }
}
