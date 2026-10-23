package com.nyxulrix.budgeter.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate

/*
 * Date budget maths, ported from DateBudget (github.com/nyx-ulrix/DateBudget, src/money.ts) by way of the web app's
 * src/date/money.ts, so all three work out the same top-ups and totals from the same shared data. Keep in step with
 * those files. Amounts are cents (SGD); dates are "YYYY-MM-DD" strings, compared as text like the original.
 */

/** One entry in the shared fund. [auto] = a budget top-up worked out from the plan, not a stored document. */
data class Tx(
    val id: String,
    val kind: String,              // "expense" or "topup"
    val amount: Long,              // cents, always positive
    val note: String,
    val date: String,              // YYYY-MM-DD
    val by: String,                // uid of whoever added it
    val auto: Boolean = false,
)

enum class Every(val id: String, val label: String) { WEEK("week", "week"), FORTNIGHT("fortnight", "2 weeks"), MONTH("month", "month") }

/**
 * The budget: [amount] cents is added every week, 2 weeks or month, starting with the period that begins on [from].
 * Weeks begin on Monday and months on the 1st, so [from] is always the first day of a period. A monthly top-up lands on
 * [day] of the month (1-31, the last day in shorter months); weekly ones land on the Monday.
 * A plan is a list of these changes, so periods already gone keep the amount they had. [split] is who puts in what
 * (uid, or "partner" before they joined, to cents); only Budgeter writes it and DateBudget ignores it.
 */
data class Step(val from: String, val amount: Long, val every: Every, val day: Int? = null, val split: Map<String, Long>? = null)

private const val SAFE = 9007199254740991L   // JavaScript's Number.MAX_SAFE_INTEGER: what the other apps accept

private fun isDate(d: String?) = d != null && Regex("^20\\d\\d-\\d\\d-\\d\\d$").matches(d) && runCatching { LocalDate.parse(d).toString() == d }.getOrDefault(false)

private fun advance(d: String, every: Every) = LocalDate.parse(d).let {
    when (every) { Every.MONTH -> it.withDayOfMonth(1).plusMonths(1); Every.WEEK -> it.plusDays(7); Every.FORTNIGHT -> it.plusDays(14) }
}.toString()

/** A whole JSON number (5 or 5.0, as JavaScript sees them), else null. */
private fun whole(e: JsonElement?): Long? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBigDecimalOrNull()
    ?.takeIf { it.signum() == 0 || it.stripTrailingZeros().scale() <= 0 }
    ?.takeIf { it.abs() <= BigDecimal.valueOf(SAFE) }?.toLong()

/**
 * The plan as stored. It's written by a partner's browser (and an earlier version wrote { from: "YYYY-MM", amount }
 * for months only), so anything malformed is ignored instead of trusted.
 */
fun steps(plan: JsonElement?): List<Step> = valid((plan as? JsonArray).orEmpty().mapNotNull { e ->
    val o = e as? JsonObject ?: return@mapNotNull null
    var from = (o["from"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val ev = o["every"]
    val noEvery = ev == null || ev is JsonNull || (ev is JsonPrimitive && if (ev.isString) ev.content.isEmpty() else ev.content == "false" || whole(ev) == 0L)   // JavaScript's !s.every
    val every = if (from != null && noEvery && Regex("^\\d{4}-\\d\\d$").matches(from)) { from = "$from-01"; Every.MONTH }
        else (ev as? JsonPrimitive)?.takeIf { it.isString }?.let { p -> Every.entries.firstOrNull { it.id == p.content } }
    val day = o["day"]
    val dayOk = day == null || whole(day)?.let { it in 1..31 } == true
    val amount = whole(o["amount"])
    val split = (o["split"] as? JsonObject)?.mapNotNull { (k, v) -> whole(v)?.let { k to it } }?.toMap()
    if (from == null || every == null || !dayOk || amount == null) null
    else Step(from, amount, every, day?.let { whole(it)!!.toInt() }, split)
})

/** The same checks for a plan already in hand, sorted by start. */
private fun valid(plan: List<Step>) = plan.filter {
    isDate(it.from) && (it.every != Every.MONTH || it.from.endsWith("-01")) && (it.day == null || it.day in 1..31) && it.amount in 0..SAFE
}.sortedBy { it.from }

/** First day of the period (Monday, or the 1st) that contains [today]. */
fun periodStart(today: String, every: Every): String = LocalDate.parse(today).let {
    if (every == Every.MONTH) it.withDayOfMonth(1) else it.minusDays(it.dayOfWeek.value - 1L)
}.toString()

/** The day a period's top-up lands: its first day, or for months the chosen day (the last day if the month is shorter). */
private fun creditDate(period: String, s: Step): String {
    if (s.every != Every.MONTH) return period
    val p = LocalDate.parse(period)
    return p.withDayOfMonth(minOf(s.day ?: 1, p.lengthOfMonth())).toString()
}

/** One top-up per period, from each step's start up to and including [today], until the next step takes over. */
fun budgetRows(plan: List<Step>, today: String): List<Tx> {
    val list = valid(plan)
    val rows = mutableListOf<Tx>()
    list.forEachIndexed { i, s ->
        val end = list.getOrNull(i + 1)?.from ?: "9999"
        var p = s.from
        while (s.amount > 0) {
            val d = creditDate(p, s)
            if (d > today || d >= end) break   // dates only go up, so nothing later can count either
            rows += Tx("budget-$d", "topup", s.amount, "Budget · every ${s.every.label}", d, "", auto = true)
            p = advance(p, s.every)
        }
    }
    return rows
}

/** The step in force today (amount 0 = off), or null if a budget was never set. */
fun currentStep(plan: List<Step>, today: String): Step? = valid(plan).lastOrNull { it.from <= today }

/**
 * New plan with [amount] every [every] (on [day] for months), starting with the current period. Earlier periods keep
 * what they had; setting it again in the same period replaces it, which also moves this period's top-up to the new day.
 */
fun withBudget(plan: List<Step>, today: String, amount: Long, every: Every, day: Int = 1, split: Map<String, Long>? = null): List<Step> {
    val from = periodStart(today, every)
    return valid(plan).filter { it.from < from } + Step(from, amount, every, if (every == Every.MONTH) day else null, split)
}

data class Contributions(val step: Step?, val mine: Long, val theirs: Long)

/** Who puts in what for the step in force today: its split, else half each (mine rounded up). */
fun contributions(plan: List<Step>, today: String, uid: String, partnerUid: String?): Contributions {
    val step = currentStep(plan, today)
    val split = step?.split.orEmpty()
    val amount = step?.amount ?: 0
    val mine = split[uid] ?: ((amount + 1) / 2)
    val theirs = partnerUid?.let { split[it] } ?: split["partner"] ?: (amount - mine)
    return Contributions(step, mine, theirs)
}

data class Totals(val topped: Long, val spent: Long, val left: Long)

/** Left to spend = top-ups - expenses. Derived, never stored, so it can't drift. */
fun totals(txs: List<Tx>): Totals {
    fun sum(kind: String) = txs.filter { it.kind == kind }.sumOf { it.amount }
    val topped = sum("topup"); val spent = sum("expense")
    return Totals(topped, spent, topped - spent)
}

/**
 * How far an expense of [amount] (replacing one of [old] when editing) takes the fund below zero, given [left] now:
 * only the part this expense overdraws, never what was already short. The person who paid covers it from their budget.
 */
fun overdrawn(left: Long, amount: Long, old: Long = 0): Long = minOf(amount, amount - old - left).coerceAtLeast(0)
