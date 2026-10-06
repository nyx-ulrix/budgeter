package com.nyxulrix.budgeter.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Currency

/** Decimal places for an ISO currency code (JPY 0, SGD 2, KWD 3). Unknown codes get 2. */
fun digits(currency: String): Int =
    runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

/** Minor units → "12.50". */
fun plain(minor: Long, currency: String): String =
    BigDecimal.valueOf(minor).movePointLeft(digits(currency)).setScale(digits(currency)).toPlainString()

/** Minor units → "S$12.50" / "-¥300". */
fun money(minor: Long, currency: String): String {
    val sym = runCatching { Currency.getInstance(currency).symbol }.getOrDefault(currency)
    return (if (minor < 0) "-" else "") + sym + plain(kotlin.math.abs(minor), currency)
}

/** User text → minor units. Accepts "12", "12.5", "1,234.50", "12,50". Null if not a number. */
fun parseMoney(text: String, currency: String): Long? {
    val t = text.trim().replace(Regex("[^0-9.,\\-]"), "")
    if (t.none { it.isDigit() }) return null
    // The last separator is the decimal point, unless exactly 3 digits follow it (a thousands mark, "1,234")
    // in a currency that doesn't use 3 decimals.
    val last = maxOf(t.lastIndexOf('.'), t.lastIndexOf(','))
    val num = if (last < 0 || (t.length - last - 1 == 3 && digits(currency) != 3)) t.replace(Regex("[.,]"), "")
    else t.substring(0, last).replace(Regex("[.,]"), "") + "." + t.substring(last + 1)
    return runCatching {
        BigDecimal(num).movePointRight(digits(currency)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}

/** Converts a foreign amount to home currency at [rate] home units per 1 foreign unit. */
fun convert(minor: Long, from: String, to: String, rate: Double): Long =
    BigDecimal.valueOf(minor).movePointLeft(digits(from)).multiply(BigDecimal.valueOf(rate))
        .movePointRight(digits(to)).setScale(0, RoundingMode.HALF_UP).toLong()

/**
 * Splits [total] in proportion to [weights] so the parts sum exactly to [total] (largest remainder).
 * All-zero weights split equally. Negative totals are split by magnitude and keep their sign.
 */
fun allocate(total: Long, weights: List<Long>): List<Long> {
    if (weights.isEmpty()) return emptyList()
    require(weights.all { it >= 0 }) { "Weights can't be negative" }
    val w = if (weights.all { it == 0L }) weights.map { 1L } else weights
    val sum = BigInteger.valueOf(w.sum())
    val t = BigInteger.valueOf(total).abs()
    val exact = w.map { t * BigInteger.valueOf(it) }
    val parts = exact.map { (it / sum).toLong() }.toLongArray()
    val left = (t - BigInteger.valueOf(parts.sum())).toInt()
    exact.indices.sortedByDescending { exact[it] % sum }.take(left).forEach { parts[it]++ }
    return parts.map { if (total < 0) -it else it }
}
