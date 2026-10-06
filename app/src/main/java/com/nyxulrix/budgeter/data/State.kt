package com.nyxulrix.budgeter.data

import com.nyxulrix.budgeter.core.SplitMethod
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** P1, the phone's owner. Other people in a split are "p2", "p3"... and shown as P2, P3. */
const val ME = "me"

fun personId(n: Int) = if (n == 1) ME else "p$n"
fun personLabel(id: String) = if (id == ME) "P1" else id.uppercase()

val DEFAULT_CATEGORIES = listOf("Food", "Transport", "Groceries", "Shopping", "Bills", "Fun", "Health", "Other")

/** Everything the app knows. Saved as one JSON file; new fields need defaults so old files still load. */
@Serializable
data class AppState(
    val setup: Setup? = null,                         // null until first-run setup is finished
    val plans: Map<String, Plan> = emptyMap(),        // by period key "2026-10"
    val txns: List<Txn> = emptyList(),
    val planned: List<Planned> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val categories: List<String> = DEFAULT_CATEGORIES,
    // Categories spread over the month (groceries, bills): they lower the month's money, and so every remaining
    // day's budget, but don't use up today's budget.
    val monthlyCategories: Set<String> = setOf("Groceries", "Bills"),
    val sync: SyncInfo = SyncInfo(),
)

@Serializable
data class Setup(
    val country: String,          // ISO 3166 alpha-2, e.g. "SG"
    val currency: String,         // ISO 4217, e.g. "SGD"
    val startDay: Int = 1,        // budget period starts on this day of the month (1–28)
    val name: String = "",
)

/**
 * One period's plan. Everything that changes spendable money is a [Line], so reservations and trip funds
 * land in the period they were made in.
 */
@Serializable
data class Plan(
    val income: Long = 0,
    val lines: List<Line> = emptyList(),
    val caps: Map<String, Long> = emptyMap(),         // optional per-category caps
    val budget: Long? = null,                         // what I plan to spend this month; null = all that's available
    val savingsTarget: Long = 0,                      // forced saving: taken off spendable money straight away
)

@Serializable
enum class LineKind(val label: String, val adds: Boolean) {
    EXTRA("Extra money", true),
    FIXED("Fixed cost", false),
    SAVINGS("Savings", false),
    RESERVE("Reserved for planned item", false),
    TRIP_FUND("Trip fund", false),
}

@Serializable
data class Line(
    val id: String = newId(),
    val kind: LineKind,
    val name: String,
    val amount: Long,
    val plannedId: String? = null,
    val tripId: String? = null,
)

@Serializable
data class TxnItem(
    val name: String,
    val price: Long,               // printed line price
    val qty: Int = 1,
    val cost: Long = price,        // price + share of printed tax / service charge / discount
    val owners: Set<String> = emptySet(),  // P ids sharing this item; empty = everyone in the split
    val category: String? = null,
)

@Serializable
data class Foreign(val currency: String, val amount: Long, val rate: Double)  // rate = home units per 1 foreign unit

@Serializable
data class Txn(
    val id: String = newId(),
    val date: String,                                  // ISO yyyy-MM-dd
    val total: Long,                                   // home currency, what the bill came to
    // tax, serviceCharge, discount and item prices are in the currency paid (foreign?.currency, else home);
    // total, item costs and shares are always home currency.
    val category: String = "Other",
    val merchant: String = "",
    val note: String = "",
    val tax: Long = 0,
    val serviceCharge: Long = 0,
    val discount: Long = 0,
    val taxIncluded: Boolean = false,
    val payer: String = ME,
    val method: SplitMethod = SplitMethod.EQUAL,
    val people: Int = 1,                               // split between P1 (me) .. P[people]
    val shares: Map<String, Long> = emptyMap(),        // P id → share in home minor units; empty = all mine
    val paidBack: Set<String> = emptySet(),            // P ids who have settled their share with the payer
    val splitInput: Map<String, Long> = emptyMap(),    // what the user typed per person (percent bp, weights, amounts)
    val items: List<TxnItem> = emptyList(),
    val tripId: String? = null,
    val foreign: Foreign? = null,
    val plannedId: String? = null,
    val fromReserve: Long = 0,                         // already deducted earlier as a reservation
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val syncedTab: String? = null,                     // sheet tab the row was written to
    val deleted: Boolean = false,                      // kept until the delete reaches Sheets
) {
    val myShare: Long get() = if (shares.isEmpty()) total else shares[ME] ?: 0
    val isSplit: Boolean get() = people > 1 || shares.size > 1
    /** People who owe the payer for this bill: everyone with a share except the payer. */
    val debtors: List<String> get() = shares.filter { (who, amt) -> who != payer && amt > 0 }.keys.sortedBy { if (it == ME) 1 else it.drop(1).toIntOrNull() ?: 99 }
    /** What this transaction takes from my budget. */
    val budgetImpact: Long get() = (myShare - fromReserve).coerceAtLeast(0)
}

@Serializable
enum class PlannedStatus { OPEN, BOUGHT }

@Serializable
data class Planned(
    val id: String = newId(),
    val name: String,
    val price: Long,
    val priority: Int = 2,        // 1 high, 2 medium, 3 low
    val targetMonth: String,      // "2026-12"
    val status: PlannedStatus = PlannedStatus.OPEN,
)

/**
 * A trip you budget for: planned costs set the target, and money is set aside each month until it starts.
 * Trip spending then comes out of what was set aside, never the daily budget.
 */
@Serializable
data class Trip(
    val id: String = newId(),
    val name: String,
    val country: String,
    val currency: String,
    val start: String,
    val end: String,
    val costs: List<TripCost> = emptyList(),
    val monthly: Long = 0,                 // set aside each period from [monthlyFrom] until the trip starts
    val monthlyFrom: String? = null,       // period key the current monthly amount began
) {
    /** What the trip is planned to cost: the sum of its planned costs. */
    val target: Long get() = costs.sumOf { it.amount }
}

val TRIP_COST_KINDS = listOf("Flights", "Stay", "Food", "Transport", "Activities", "Shopping", "Other")

/** One planned cost, in home currency. [paidTxnId] links the expense recorded when it was paid. */
@Serializable
data class TripCost(
    val id: String = newId(),
    val name: String,
    val amount: Long,
    val kind: String = "Other",
    val paidTxnId: String? = null,
)

@Serializable
data class SyncInfo(
    val enabled: Boolean = false,
    val account: String? = null,
    val spreadsheetId: String? = null,
    val paused: String? = null,     // reason when sync is paused, null when fine
    val lastSync: Long? = null,
)

