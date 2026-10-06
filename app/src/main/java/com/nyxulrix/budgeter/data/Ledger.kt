package com.nyxulrix.budgeter.data

import com.nyxulrix.budgeter.core.Day
import com.nyxulrix.budgeter.core.Pace
import com.nyxulrix.budgeter.core.Period
import com.nyxulrix.budgeter.core.allocate
import com.nyxulrix.budgeter.core.pace
import com.nyxulrix.budgeter.core.periodOf
import com.nyxulrix.budgeter.core.today
import java.time.LocalDate

/** Derived numbers. Pure functions of [AppState], so screens and widgets agree. */

val AppState.currency: String get() = setup?.currency ?: "USD"
val AppState.startDay: Int get() = setup?.startDay ?: 1

fun AppState.periodOf(date: LocalDate): Period = periodOf(date, startDay)

val AppState.liveTxns: List<Txn> get() = txns.filter { !it.deleted }

/**
 * The plan for a period. A period nobody has edited yet inherits the latest earlier plan's income, fixed costs,
 * savings and caps (not extras, reservations or trip funds, which are one-offs).
 */
fun AppState.planFor(key: String): Plan = plans[key] ?: plans.filterKeys { it < key }.maxByOrNull { it.key }?.value?.let { p ->
    Plan(p.income, p.lines.filter { it.kind == LineKind.FIXED || it.kind == LineKind.SAVINGS }, p.caps)
} ?: Plan()

/** Savings set aside from the first planned month through [key], counting months that inherit their plan. */
fun AppState.savedToDate(key: String): Long {
    val first = plans.keys.minOrNull() ?: return 0
    var p = Period.ofKey(first, startDay)
    var sum = 0L
    while (p.key <= key) { sum += planFor(p.key).total(LineKind.SAVINGS); p = p.next() }
    return sum
}

fun Plan.total(kind: LineKind): Long = lines.filter { it.kind == kind }.sumOf { it.amount }

/** Income + extra − fixed − savings − reservations − one-off trip set-asides (not monthly trip savings). */
val Plan.spendable: Long get() = income + lines.sumOf { if (it.kind.adds) it.amount else -it.amount }

/** What's spendable in a period after everything is set aside, including monthly trip savings. */
fun AppState.spendable(key: String): Long = planFor(key).spendable - tripMonthlyTotal(key)

private fun Txn.inBudget(p: Period) = !deleted && tripId == null && LocalDate.parse(date) in p

fun AppState.spent(p: Period): Long = txns.filter { it.inBudget(p) }.sumOf { it.budgetImpact }

data class Snapshot(
    val period: Period,
    val spendable: Long,
    val spent: Long,
    val day: Day,
    val pace: Pace,
) {
    val left: Long get() = spendable - spent
    val fraction: Float get() = if (spendable <= 0) 1f else (spent.toFloat() / spendable).coerceIn(0f, 1f)
}

/** Home-screen numbers for [date]. */
fun AppState.snapshot(date: LocalDate = LocalDate.now()): Snapshot {
    val p = periodOf(date)
    val spendable = spendable(p.key)
    val inP = txns.filter { it.inBudget(p) }
    val before = inP.filter { LocalDate.parse(it.date).isBefore(date) }.sumOf { it.budgetImpact }
    val onDay = inP.filter { LocalDate.parse(it.date) == date }.sumOf { it.budgetImpact }
    val spent = inP.sumOf { it.budgetImpact }
    return Snapshot(p, spendable, spent, today(spendable, before, onDay, date, p), pace(spendable, spent, date, p))
}

/** Budget-impact per category in a period. Itemised bills with per-item categories are split across them. */
fun AppState.byCategory(p: Period): Map<String, Long> {
    val out = linkedMapOf<String, Long>()
    for (t in txns.filter { it.inBudget(p) }) {
        if (t.items.any { it.category != null }) {
            allocate(t.budgetImpact, t.items.map { it.cost.coerceAtLeast(0) }).forEachIndexed { i, part ->
                val c = t.items[i].category ?: t.category
                out[c] = (out[c] ?: 0) + part
            }
        } else out[t.category] = (out[t.category] ?: 0) + t.budgetImpact
    }
    return out
}

// Planned purchases

fun AppState.reserved(plannedId: String): Long =
    plans.values.sumOf { p -> p.lines.filter { it.plannedId == plannedId && it.kind == LineKind.RESERVE }.sumOf { it.amount } }

// Trips

/** One-off set-asides for a trip (top-ups and money already moved in past months). */
fun AppState.tripLines(tripId: String): Long =
    plans.values.sumOf { p -> p.lines.filter { it.tripId == tripId && it.kind == LineKind.TRIP_FUND }.sumOf { it.amount } }

/**
 * Monthly set-aside per period for a trip: from [Trip.monthlyFrom] through the period the trip starts in,
 * stopping once the planned costs are covered. Future periods are included so their budgets show it ahead.
 */
fun AppState.tripMonthly(t: Trip): Map<String, Long> {
    val from = t.monthlyFrom ?: return emptyMap()
    if (t.monthly <= 0) return emptyMap()
    val last = periodOf(LocalDate.parse(t.start)).key
    var saved = tripLines(t.id)
    val out = linkedMapOf<String, Long>()
    var p = Period.ofKey(from, startDay)
    while (p.key <= last) {
        val amt = if (t.target > 0) minOf(t.monthly, (t.target - saved).coerceAtLeast(0)) else t.monthly
        if (amt > 0) out[p.key] = amt
        saved += amt
        p = p.next()
    }
    return out
}

fun AppState.tripMonthlyTotal(key: String): Long = trips.sumOf { tripMonthly(it)[key] ?: 0 }

/** Money set aside for the trip so far (up to and including the current period). */
fun AppState.tripFund(tripId: String, date: LocalDate = LocalDate.now()): Long {
    val t = trips.firstOrNull { it.id == tripId } ?: return tripLines(tripId)
    val now = periodOf(date).key
    return tripLines(tripId) + tripMonthly(t).filterKeys { it <= now }.values.sum()
}

/** Money that will have been set aside by the time the trip starts, if the plan holds. */
fun AppState.tripFundAtStart(t: Trip): Long = tripLines(t.id) + tripMonthly(t).values.sum()

/** Periods left to save in, counting the current one, until the trip's start period. 0 once it has started. */
fun AppState.monthsToSave(t: Trip, date: LocalDate = LocalDate.now()): Int {
    var p = periodOf(date); val last = periodOf(LocalDate.parse(t.start)).key
    var n = 0
    while (p.key <= last && !LocalDate.parse(t.start).isBefore(date)) { n++; p = p.next() }
    return n
}

fun AppState.tripSpent(tripId: String): Long = liveTxns.filter { it.tripId == tripId }.sumOf { it.myShare }

fun AppState.activeTrip(date: LocalDate = LocalDate.now()): Trip? = trips.firstOrNull {
    !date.isBefore(LocalDate.parse(it.start)) && !date.isAfter(LocalDate.parse(it.end))
}

// Groups

/** Net balance per person in a group: positive means they are owed money. */
fun AppState.groupNet(groupId: String): Map<String, Long> {
    val net = linkedMapOf<String, Long>()
    groups.firstOrNull { it.id == groupId }?.members?.forEach { net[it] = 0 }
    for (t in liveTxns.filter { it.groupId == groupId }) {
        net[t.payer] = (net[t.payer] ?: 0) + t.total
        val shares = t.shares.ifEmpty { mapOf(t.payer to t.total) }
        shares.forEach { (who, amt) -> net[who] = (net[who] ?: 0) - amt }
    }
    for (s in settlements.filter { it.groupId == groupId }) {
        net[s.from] = (net[s.from] ?: 0) + s.amount
        net[s.to] = (net[s.to] ?: 0) - s.amount
    }
    return net
}

fun AppState.personName(id: String): String = people.firstOrNull { it.id == id }?.name ?: "?"
