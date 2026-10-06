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
        // (310000 - 12000) / 27 days left including today
        assertEquals(11_037L, s.day.budget)
        assertEquals(3_000L, s.day.spent)
    }

    @Test fun buyNowDoesNotDoubleCountReservation() {
        val t = Txn(date = "2026-10-05", total = 20_000, plannedId = "p", fromReserve = 5_000)
        assertEquals(15_000L, t.budgetImpact)
        val fully = Txn(date = "2026-10-05", total = 20_000, plannedId = "p", fromReserve = 30_000)
        assertEquals(0L, fully.budgetImpact)
    }

    @Test fun groupBalances() {
        val st = AppState(
            setup,
            groups = listOf(Group("g", "Flat", listOf(ME, "a", "b"))),
            txns = listOf(
                Txn(date = "2026-10-01", total = 9_000, groupId = "g", payer = ME, method = SplitMethod.EQUAL,
                    shares = mapOf(ME to 3_000, "a" to 3_000, "b" to 3_000)),
                Txn(date = "2026-10-02", total = 3_000, groupId = "g", payer = "a", method = SplitMethod.EQUAL,
                    shares = mapOf(ME to 1_000, "a" to 1_000, "b" to 1_000)),
            ),
            settlements = listOf(Settlement(groupId = "g", from = "b", to = ME, amount = 1_000, date = "2026-10-03")),
        )
        assertEquals(mapOf(ME to 4_000L, "a" to -1_000L, "b" to -3_000L), st.groupNet("g"))
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
}
