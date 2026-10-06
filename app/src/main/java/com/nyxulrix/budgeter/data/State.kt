package com.nyxulrix.budgeter.data

import com.nyxulrix.budgeter.core.SplitMethod
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

const val ME = "me"

val DEFAULT_CATEGORIES = listOf("Food", "Transport", "Groceries", "Shopping", "Bills", "Fun", "Health", "Other")

/** Everything the app knows. Saved as one JSON file; new fields need defaults so old files still load. */
@Serializable
data class AppState(
    val setup: Setup? = null,                         // null until first-run setup is finished
    val plans: Map<String, Plan> = emptyMap(),        // by period key "2026-10"
    val txns: List<Txn> = emptyList(),
    val planned: List<Planned> = emptyList(),
    val people: List<Person> = listOf(Person(ME, "Me")),
    val groups: List<Group> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val categories: List<String> = DEFAULT_CATEGORIES,
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
    val owners: Set<String> = emptySet(),  // person ids sharing this item; empty = everyone in the split
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
    val shares: Map<String, Long> = emptyMap(),        // person id → share in home minor units; empty = all mine
    val splitInput: Map<String, Long> = emptyMap(),    // what the user typed per person (percent bp, weights, amounts)
    val items: List<TxnItem> = emptyList(),
    val groupId: String? = null,
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

@Serializable
data class Person(val id: String = newId(), val name: String)

@Serializable
data class Group(val id: String = newId(), val name: String, val members: List<String> = listOf(ME))

@Serializable
data class Settlement(
    val id: String = newId(),
    val groupId: String,
    val from: String,
    val to: String,
    val amount: Long,
    val date: String,
)

@Serializable
data class Trip(
    val id: String = newId(),
    val name: String,
    val country: String,
    val currency: String,
    val start: String,
    val end: String,
    val groupId: String? = null,
)

@Serializable
data class SyncInfo(
    val enabled: Boolean = false,
    val account: String? = null,
    val spreadsheetId: String? = null,
    val paused: String? = null,     // reason when sync is paused, null when fine
    val lastSync: Long? = null,
)

