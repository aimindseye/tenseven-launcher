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
    private enum class Failure(
        val code: String,
    ) {
        InvalidNumber("ERR:INVALID_NUMBER"),
        DivisionByZero("ERR:DIVIDE_BY_ZERO"),
        Overflow("ERR:OVERFLOW"),
        Domain("ERR:DOMAIN"),
    }

    private class Fail(
        val failure: Failure,
    ) : Exception()

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
    private class Exact(
        val numerator: BigInteger,
        val denominator: BigInteger,
    ) {
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

    private data class DecimalParts(
        val whole: String,
        val fraction: String?,
        val negative: Boolean,
    )

    private fun decimalParts(raw: String): DecimalParts {
        val text = raw.trim()
        val negative = text.startsWith("-")
        val unsigned = if (text.startsWith("+") || negative) text.substring(1) else text
        val parts = unsigned.split('.')
        val whole = parts[0]
        val fraction = parts.getOrNull(1)
        val invalidFraction = fraction != null && !decimalDigits(fraction)
        if (parts.size > 2 || !decimalDigits(whole) || invalidFraction) throw Fail(Failure.InvalidNumber)
        return DecimalParts(whole, fraction, negative)
    }

    private fun decimalDigits(text: String): Boolean = text.isNotEmpty() && text.all { it in '0'..'9' }

    private fun parseDecimal(raw: String): Exact {
        val parts = decimalParts(raw)
        val wholeValue = checked(BigInteger(parts.whole))
        val fraction = parts.fraction
        val denominator = if (fraction == null) BigInteger.ONE else checked(BigInteger.TEN.pow(fraction.length))
        val fractionValue = if (fraction == null) BigInteger.ZERO else checked(BigInteger(fraction))
        var numerator = checked(checked(wholeValue * denominator) + fractionValue)
        if (parts.negative) numerator = checked(numerator.negate())
        return make(numerator, denominator)
    }

    private fun apply(
        a: Exact,
        operation: Int,
        b: Exact,
    ): Exact =
        when (operation) {
            0 -> {
                make(
                    checked(checked(a.numerator * b.denominator) + checked(b.numerator * a.denominator)),
                    checked(a.denominator * b.denominator),
                )
            }

            1 -> {
                make(
                    checked(checked(a.numerator * b.denominator) - checked(b.numerator * a.denominator)),
                    checked(a.denominator * b.denominator),
                )
            }

            2 -> {
                make(checked(a.numerator * b.numerator), checked(a.denominator * b.denominator))
            }

            else -> {
                if (b.numerator.signum() == 0) throw Fail(Failure.DivisionByZero)
                make(checked(a.numerator * b.denominator), checked(a.denominator * b.numerator))
            }
        }

    private fun scientific(
        text: String,
        operation: Int,
    ): Double {
        val value = scientificInput(text)
        val result =
            when (operation) {
                0 -> squareRoot(value)
                1 -> value * value
                2 -> reciprocal(value)
                3 -> sin(Math.toRadians(value))
                4 -> cos(Math.toRadians(value))
                5 -> tan(Math.toRadians(value))
                else -> logarithm(value, operation)
            }
        if (result.isNaN()) throw Fail(Failure.Domain)
        if (!result.isFinite()) throw Fail(Failure.Overflow)
        return result
    }

    private fun scientificInput(text: String): Double {
        val value = text.trim().toDoubleOrNull() ?: throw Fail(Failure.InvalidNumber)
        if (!value.isFinite()) throw Fail(Failure.Overflow)
        return value
    }

    private fun squareRoot(value: Double): Double {
        if (value < 0.0) throw Fail(Failure.Domain)
        return sqrt(value)
    }

    private fun reciprocal(value: Double): Double {
        if (value == 0.0) throw Fail(Failure.DivisionByZero)
        return 1.0 / value
    }

    private fun logarithm(
        value: Double,
        operation: Int,
    ): Double {
        if (value <= 0.0) throw Fail(Failure.Domain)
        return if (operation == 6) ln(value) else log10(value)
    }

    private fun formatScientific(value: Double): String {
        val magnitude = abs(value)
        return when {
            value == 0.0 -> "0"
            magnitude < 1.0e-9 || magnitude >= 1.0e12 -> exponential(value)
            else -> fixed(value)
        }
    }

    private fun exponential(value: Double): String {
        val text = String.format(Locale.ROOT, "%.10e", value)
        val (mantissa, exponent) = text.split('e')
        return mantissa + "e" + exponent.toInt()
    }

    private fun fixed(value: Double): String {
        val rendered = String.format(Locale.ROOT, "%.12f", value)
        return if (rendered.contains('.')) rendered.trimEnd('0').trimEnd('.') else rendered
    }
}
