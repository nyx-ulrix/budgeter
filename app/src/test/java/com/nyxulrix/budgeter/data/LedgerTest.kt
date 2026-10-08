package com.nyxulrix.budgeter.data

import com.nyxulrix.budgeter.core.SplitMethod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LedgerTest {
    private val setup = Setup("SG", "SGD", 1)
    private val oct5 = LocalDate.of(2026, 10, 5)

    @Test fun planCarriesOverRecurringLinesOnly() {
        val st = AppState(setup, plans = mapOf("2026-09" to Plan(300_000, listOf(
            Line(kind = LineKind.FIXED, name = "Rent", amount = 100_000),
            Line(kind = LineKind.SAVINGS, name = "Save", amount = 50_000),
            Line(kind = LineKind.EXTRA, name = "Gift", amount = 9_999),
            Line(kind = LineKind.RESERVE, name = "Shoes", amount = 1_000, plannedId = "p"),
        ))))
        val oct = st.planFor("2026-10")
        assertEquals(300_000L, oct.income)
        assertEquals(listOf(LineKind.FIXED, LineKind.SAVINGS), oct.lines.map { it.kind })
        assertEquals(150_000L, oct.spendable)
        assertEquals(Plan(), AppState(setup).planFor("2026-10"))
    }

    @Test fun snapshotCountsMyShareAndSkipsTrips() {
        val st = AppState(
            setup,
            plans = mapOf("2026-10" to Plan(310_000)),
            txns = listOf(
                Txn(date = "2026-10-01", total = 10_000),                                                  // mine
                Txn(date = "2026-10-02", total = 8_000, shares = mapOf(ME to 2_000, "a" to 6_000)),         // my share 20
                Txn(date = "2026-10-03", total = 50_000, tripId = "t"),                                     // trip: not counted
                Txn(date = "2026-10-05", total = 3_000),                                                    // today
                Txn(date = "2026-10-04", total = 99_999, deleted = true),                                   // deleted
            ),
        )
        val s = st.snapshot(oct5)
        assertEquals(15_000L, s.spent)
        // base 310000/31 = 10000 a day; 4 days before today = 40000, 12000 spent → 28000 saved up
        assertEquals(10_000L, s.day.budget)
        assertEquals(28_000L, s.day.bonus)
        assertEquals(3_000L, s.day.spent)
    }

    @Test fun editingReservedAddsNowAndReleasesLatestFirst() {
        val p = Planned(id = "p", name = "Shoes", price = 50_000, targetMonth = "2026-12")
        val st = AppState(setup, plans = mapOf(
            "2026-09" to Plan(100_000, listOf(Line(kind = LineKind.RESERVE, name = "Shoes", amount = 10_000, plannedId = "p"))),
            "2026-10" to Plan(100_000, listOf(Line(kind = LineKind.RESERVE, name = "Shoes", amount = 5_000, plannedId = "p"))),
        ))
        val up = st.withReserved(p, 20_000, oct5)
        assertEquals(20_000L, up.reserved("p"))
        assertEquals(st.spendable("2026-10") - 5_000, up.spendable("2026-10"))
        val down = st.withReserved(p, 8_000, oct5)
        assertEquals(8_000L, down.reserved("p"))
        assertEquals(st.spendable("2026-10") + 5_000, down.spendable("2026-10"))   // October's fully released first
        assertEquals(8_000L, down.plans["2026-09"]!!.lines.single().amount)
        assertEquals(0L, st.withReserved(p, 0, oct5).reserved("p"))
        // With a monthly budget set, reserving still comes off what I can spend
        val budgeted = st.copy(plans = st.plans + ("2026-10" to st.plans["2026-10"]!!.copy(budget = 60_000)))
        assertEquals(55_000L, budgeted.spendable("2026-10"))
        assertEquals(40_000L, budgeted.withReserved(p, 30_000, oct5).spendable("2026-10"))
    }

    @Test fun savedUpIsKeptAsidePlusUnspentDays() {
        val plan = Plan(310_000, listOf(Line(kind = LineKind.SAVINGS, name = "Save", amount = 20_000)), savingsTarget = 31_000)
        // spendable 310000 - 20000 - 31000 = 259000 → 8354 a day; 4 days before Oct 5 = 33416, 10000 spent → 23416 unspent
        val s = AppState(setup, plans = mapOf("2026-10" to plan), txns = listOf(Txn(date = "2026-10-02", total = 10_000))).snapshot(oct5)
        assertEquals(51_000L, s.keptAside)
        assertEquals(51_000L + s.day.bonusLeft, s.savedUp)
        assertEquals(23_416L, s.day.bonusLeft)
    }

    @Test fun buyNowDoesNotDoubleCountReservation() {
        val t = Txn(date = "2026-10-05", total = 20_000, plannedId = "p", fromReserve = 5_000)
        assertEquals(15_000L, t.budgetImpact)
        val fully = Txn(date = "2026-10-05", total = 20_000, plannedId = "p", fromReserve = 30_000)
        assertEquals(0L, fully.budgetImpact)
    }


    @Test fun tripFundAndSpend() {
        val st = AppState(
            setup,
            plans = mapOf(
                "2026-09" to Plan(lines = listOf(Line(kind = LineKind.TRIP_FUND, name = "Trip", amount = 100_000, tripId = "t"))),
                "2026-10" to Plan(lines = listOf(Line(kind = LineKind.TRIP_FUND, name = "Top up", amount = 20_000, tripId = "t"))),
            ),
            trips = listOf(Trip("t", "Tokyo", "JP", "JPY", "2026-10-01", "2026-10-07")),
            txns = listOf(Txn(date = "2026-10-02", total = 30_000, tripId = "t")),
        )
        assertEquals(120_000L, st.tripFund("t"))
        assertEquals(30_000L, st.tripSpent("t"))
        assertEquals("t", st.activeTrip(oct5)?.id)
    }

    @Test fun stateRoundTripsThroughJson() {
        val st = AppState(setup, txns = listOf(Txn(date = "2026-10-01", total = 1, items = listOf(TxnItem("a", 1)))))
        assertEquals(st, json.decodeFromString(AppState.serializer(), json.encodeToString(AppState.serializer(), st)))
        // Older files missing newer fields still load.
        assertEquals(AppState(), json.decodeFromString(AppState.serializer(), "{}"))
    }

    @Test fun unspentMoneyBecomesSavings() {
        val st = AppState(
            setup,
            plans = mapOf("2026-09" to Plan(300_000, listOf(Line(kind = LineKind.FIXED, name = "Rent", amount = 100_000)), budget = 150_000)),
            txns = listOf(Txn(date = "2026-09-10", total = 120_000)),
        )
        assertEquals(200_000L, st.available("2026-09"))      // income − fixed
        assertEquals(150_000L, st.spendable("2026-09"))      // the budget I chose
        assertEquals(80_000L, st.monthSaved("2026-09"))      // 200k available − 120k spent
        assertEquals(150_000L, st.spendable("2026-10"))      // budget carries over
        assertEquals(80_000L, st.savedToDate("2026-10", LocalDate.of(2026, 10, 15)))  // only finished months
        assertEquals(0L, st.savedToDate("2026-09", LocalDate.of(2026, 9, 20)))
        val over = st.copy(txns = listOf(Txn(date = "2026-09-10", total = 250_000)))
        assertEquals(-50_000L, over.monthSaved("2026-09"))   // overspend comes out of savings
        assertEquals(true, over.snapshot(LocalDate.of(2026, 9, 20)).over)
        val noBudget = st.copy(plans = mapOf("2026-09" to Plan(300_000)))
        assertEquals(300_000L, noBudget.spendable("2026-09"))  // no budget set: everything available
    }

    @Test fun monthlyCategoriesLowerTheMonthNotToday() {
        val oct = Plan(310_000)
        val base = AppState(setup, plans = mapOf("2026-10" to oct))
        val groceries = base.copy(txns = listOf(Txn(date = "2026-10-05", total = 27_000, category = "Groceries")))
        val s = groceries.snapshot(oct5)
        assertEquals(0L, s.day.spent)                                  // not counted against today
        assertEquals(10_000L, s.day.budget)
        assertEquals(40_000L - 27_000, s.day.bonus)                    // but today's money already reflects it
        assertEquals(27_000L, s.spent)                                 // and the month counts it
        val food = base.copy(txns = listOf(Txn(date = "2026-10-05", total = 2_000, category = "Food")))
        assertEquals(2_000L, food.snapshot(oct5).day.spent)
        assertEquals(40_000L, food.snapshot(oct5).day.bonus)
        val allDaily = groceries.copy(monthlyCategories = emptySet())
        assertEquals(27_000L, allDaily.snapshot(oct5).day.spent)
    }

    @Test fun savingsTargetComesOffFirstAndBuffersOverspend() {
        val plan = Plan(300_000, budget = 150_000, savingsTarget = 30_000)
        val st = AppState(setup, plans = mapOf("2026-10" to plan))
        assertEquals(120_000L, st.spendable("2026-10"))                      // 1500 budget − 300 target
        assertEquals(120_000L, st.spendable("2026-11"))                      // carries over
        fun at(spent: Long) = st.copy(txns = listOf(Txn(date = "2026-10-02", total = spent))).snapshot(oct5)
        at(100_000).let { assertEquals(false, it.dipping); assertEquals(false, it.over) }
        at(130_000).let { assertEquals(true, it.dipping); assertEquals(false, it.over); assertEquals(10_000L, it.dipped) }
        at(150_000).let { assertEquals(true, it.dipping); assertEquals(30_000L, it.dipped) }   // exactly the whole target
        at(150_001).let { assertEquals(false, it.dipping); assertEquals(true, it.over) }
        val noBudget = AppState(setup, plans = mapOf("2026-10" to Plan(300_000, savingsTarget = 50_000)))
        assertEquals(250_000L, noBudget.spendable("2026-10"))                 // everything available − target
    }
}
