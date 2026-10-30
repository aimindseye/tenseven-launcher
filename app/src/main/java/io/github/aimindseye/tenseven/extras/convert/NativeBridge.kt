// Ported from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f), sable-src/apps/r8/rust/crates/
// convert-core and convert-jni (Rust), Apache License 2.0. Changed: written in Kotlin with Long and BigInteger, so
// the app needs no native library; the "OK:" and "ERR:" text protocol and the conversion table are unchanged.
package io.github.aimindseye.tenseven.extras.convert

import java.math.BigInteger

/** Fixed-point (thousandths) unit conversion, answered as "OK:<milli>" or "ERR:<reason>". */
internal object NativeBridge {
    private enum class Category { Length, Mass, Temperature, Volume }

    // The frozen unit codes, in order: 0 mm, 1 cm, 2 m, 3 km, 4 in, 5 ft, 6 mi, 7 g, 8 kg, 9 oz, 10 lb,
    // 11 C, 12 F, 13 K, 14 mL, 15 L, 16 cup, 17 gal.
    private val categories =
        listOf(
            Category.Length, Category.Length, Category.Length, Category.Length, Category.Length, Category.Length, Category.Length,
            Category.Mass, Category.Mass, Category.Mass, Category.Mass,
            Category.Temperature, Category.Temperature, Category.Temperature,
            Category.Volume, Category.Volume, Category.Volume, Category.Volume,
        )

    // numerator, denominator relative to the category's base unit (mm, g, mL); temperatures have none.
    private val ratios: List<Pair<Long, Long>?> =
        listOf(
            1L to 1L, 10L to 1L, 1_000L to 1L, 1_000_000L to 1L, 254L to 10L, 3_048L to 10L, 1_609_344L to 1L,
            1L to 1L, 1_000L to 1L, 28_349_523_125L to 1_000_000_000L, 45_359_237L to 100_000L,
            null, null, null,
            1L to 1L, 1_000L to 1L, 2_365_882_365L to 10_000_000L, 3_785_411_784L to 1_000_000L,
        )

    private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
    private val I128_MAX = BigInteger.ONE.shiftLeft(127) - BigInteger.ONE
    private val I128_MIN = BigInteger.ONE.shiftLeft(127).negate()

    fun selfTest(): String = convertMilli(5_000, 6, 3).let { if (it == "OK:8047") "PASS:convert-core:8047" else "FAIL:convert-core:$it" }

    fun convertMilli(
        valueMilli: Long,
        fromUnit: Int,
        toUnit: Int,
    ): String {
        if (fromUnit !in categories.indices || toUnit !in categories.indices) return "ERR:INVALID_UNIT"
        if (categories[fromUnit] != categories[toUnit]) return "ERR:INCOMPATIBLE_UNITS"
        val converted =
            if (categories[fromUnit] == Category.Temperature) {
                temperature(valueMilli, fromUnit, toUnit)
            } else {
                linear(valueMilli, fromUnit, toUnit)
            }
        return if (converted == null) "ERR:OUT_OF_RANGE" else "OK:$converted"
    }

    private fun linear(
        value: Long,
        from: Int,
        to: Int,
    ): Long? {
        val (fromNum, fromDen) = ratios[from]!!
        val (toNum, toDen) = ratios[to]!!
        val numerator = BigInteger.valueOf(value) * BigInteger.valueOf(fromNum) * BigInteger.valueOf(toDen)
        val denominator = BigInteger.valueOf(fromDen) * BigInteger.valueOf(toNum)
        if (numerator > I128_MAX || numerator < I128_MIN) return null
        return toLongOrNull(divRound(numerator, denominator))
    }

    private fun temperature(
        value: Long,
        from: Int,
        to: Int,
    ): Long? {
        val v = BigInteger.valueOf(value)
        val celsius =
            when (from) {
                11 -> v
                12 -> divRound((v - BigInteger.valueOf(32_000)) * BigInteger.valueOf(5), BigInteger.valueOf(9))
                else -> v - BigInteger.valueOf(273_150)
            }
        if (celsius < BigInteger.valueOf(-273_150)) return null
        val converted =
            when (to) {
                11 -> celsius
                12 -> divRound(celsius * BigInteger.valueOf(9), BigInteger.valueOf(5)) + BigInteger.valueOf(32_000)
                else -> celsius + BigInteger.valueOf(273_150)
            }
        return toLongOrNull(converted)
    }

    /** Rounds half away from zero, as the Rust core did. The denominator is positive. */
    private fun divRound(
        numerator: BigInteger,
        denominator: BigInteger,
    ): BigInteger {
        val half = denominator / BigInteger.valueOf(2)
        return if (numerator.signum() >= 0) {
            (numerator + half) / denominator
        } else {
            ((numerator.negate() + half) / denominator).negate()
        }
    }

    private fun toLongOrNull(value: BigInteger): Long? = if (value < LONG_MIN || value > LONG_MAX) null else value.toLong()
}
