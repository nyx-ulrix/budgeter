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

/**
 * Today's money: a [budget] for the day plus a soft [bonus] saved up from earlier days this month.
 * Spending into the bonus isn't overspending; only going past both is.
 */
data class Day(val budget: Long, val spent: Long, val bonus: Long = 0) {
    val available: Long get() = budget + bonus
    /** Today's own budget left, the headline number. Below zero means you're into the saved-up bonus. */
    val dailyLeft: Long get() = budget - spent
    /** Including the saved-up bonus; below zero is a real overspend. */
    val remaining: Long get() = budget + bonus - spent
    /** Saved-up money still unused after today's spending. */
    val bonusLeft: Long get() = (bonus - (spent - budget).coerceAtLeast(0)).coerceAtLeast(0)
}

/** One earlier day's spending: what used the day's budget, and monthly-category spending (groceries, bills). */
data class DaySpend(val daily: Long, val monthly: Long = 0)

/**
 * Replays the period day by day. Each day's budget is the month's unspent money (less earlier days' leftovers) spread
 * over the days left, so an unused day keeps later budgets steady and its leftover shows as the [Day.bonus].
 * Overspending and monthly-category spending use up the leftovers first, then lower every remaining day.
 * Leftovers never cross the period end; month-end leftovers become savings.
 * ponytail: replays from today's [spendable], so changing the budget mid-month re-scores earlier days; store spendable per day if that matters.
 */
fun today(spendable: Long, before: List<DaySpend>, spentToday: Long, monthlyToday: Long, date: LocalDate, period: Period): Day {
    var pool = spendable.coerceAtLeast(0)   // month's money not yet spent
    var bonus = 0L                           // of which: earlier days' leftovers
    var d = period.start
    for (s in before) {
        val budget = (pool - bonus).coerceAtLeast(0) / ChronoUnit.DAYS.between(d, period.end).coerceAtLeast(1)
        pool = (pool - s.daily - s.monthly).coerceAtLeast(0)
        bonus = (bonus + budget - s.daily - s.monthly).coerceIn(0, pool)
        d = d.plusDays(1)
    }
    val budget = (pool - bonus).coerceAtLeast(0) / ChronoUnit.DAYS.between(date, period.end).coerceAtLeast(1)
    return Day(budget, spentToday, (bonus - monthlyToday).coerceIn(0, pool))
}

/** Spending speed against an even pace through the month. Being over the whole budget is separate ([Snapshot.over]). */
enum class Pace(val label: String) { ON_TRACK("On track"), SLIGHTLY_OVER("A bit fast"), OVER("Too fast") }

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
