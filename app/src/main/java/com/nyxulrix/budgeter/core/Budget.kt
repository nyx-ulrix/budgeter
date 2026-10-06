package com.nyxulrix.budgeter.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A budget period: [start] inclusive to [end] exclusive, named by the month it starts in ("2026-10"). */
data class Period(val start: LocalDate, val end: LocalDate) {
    val key: String get() = YearMonth.from(start).toString()
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt()
    operator fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(end)
    fun next() = periodOf(end, start.dayOfMonth)
    fun prev() = periodOf(start.minusDays(1), start.dayOfMonth)

    companion object {
        fun ofKey(key: String, startDay: Int): Period = periodOf(YearMonth.parse(key).atDay(startDay), startDay)
    }
}

/** The period containing [date] when periods start on day [startDay] (1–28) of each month. */
fun periodOf(date: LocalDate, startDay: Int): Period {
    require(startDay in 1..28) { "Start day must be 1 to 28" }
    val start = if (date.dayOfMonth >= startDay) date.withDayOfMonth(startDay)
    else date.minusMonths(1).withDayOfMonth(startDay)
    return Period(start, start.plusMonths(1))
}

/** Today's budget is fixed at the start of the day; spending today only lowers what's left of it. */
data class Day(val budget: Long, val spent: Long) {
    val remaining: Long get() = budget - spent
}

/**
 * Today's budget = what's left of [spendable] before today, spread over the days left including today.
 * Overspending today therefore lowers tomorrow's budget automatically. Never negative.
 */
fun today(spendable: Long, spentBeforeToday: Long, spentToday: Long, date: LocalDate, period: Period): Day {
    val daysLeft = ChronoUnit.DAYS.between(date, period.end).coerceAtLeast(1)
    val budget = (spendable - spentBeforeToday).coerceAtLeast(0) / daysLeft
    return Day(budget, spentToday)
}

enum class Pace(val label: String) { ON_TRACK("On track"), SLIGHTLY_OVER("Slightly over"), OVER("Over") }

/** Compares spending with an even burn of [spendable] through the period, counting today as elapsed. */
fun pace(spendable: Long, spent: Long, date: LocalDate, period: Period): Pace {
    val elapsed = (ChronoUnit.DAYS.between(period.start, date) + 1).coerceIn(1, period.days.toLong())
    val expected = spendable.coerceAtLeast(0) * elapsed / period.days
    return when {
        spent <= expected -> Pace.ON_TRACK
        spent * 10 <= expected * 11 -> Pace.SLIGHTLY_OVER
        else -> Pace.OVER
    }
}
