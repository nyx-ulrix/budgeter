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
    Plan(p.income, p.lines.filter { it.kind == LineKind.FIXED || it.kind == LineKind.SAVINGS }.map { it.copy(id = newId()) }, p.caps)
} ?: Plan()

fun Plan.total(kind: LineKind): Long = lines.filter { it.kind == kind }.sumOf { it.amount }

/** Income + extra − fixed − savings − reservations − trip funds. */
val Plan.spendable: Long get() = income + lines.sumOf { if (it.kind.adds) it.amount else -it.amount }

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
    val spendable = planFor(p.key).spendable
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

fun AppState.tripFund(tripId: String): Long =
    plans.values.sumOf { p -> p.lines.filter { it.tripId == tripId && it.kind == LineKind.TRIP_FUND }.sumOf { it.amount } }

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
