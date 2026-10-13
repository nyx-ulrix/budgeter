package com.nyxulrix.budgeter.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DateMoneyTest {
    @Test fun weeklyRowsLandOnMondaysUpToToday() {
        val rows = budgetRows(listOf(Step("2026-09-28", 2000, Every.WEEK)), "2026-10-13")   // Mon 28 Sep, today Tue 13 Oct
        assertEquals(listOf("2026-09-28", "2026-10-05", "2026-10-12"), rows.map { it.date })
        assertEquals("budget-2026-10-12", rows.last().id)
        assertEquals("Budget · every week", rows.last().note)
        assertEquals(true, rows.all { it.auto && it.kind == "topup" && it.amount == 2000L })
        assertEquals("2026-10-12", periodStart("2026-10-13", Every.WEEK))
        assertEquals("2026-10-12", periodStart("2026-10-18", Every.FORTNIGHT))   // a Sunday still belongs to Monday's week
    }

    @Test fun monthlyDay31LandsOnLastDayOfShortMonths() {
        val rows = budgetRows(listOf(Step("2026-01-01", 10000, Every.MONTH, 31)), "2026-04-30")
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), rows.map { it.date })
        assertEquals("Budget · every month", rows.first().note)
    }

    @Test fun laterStepReplacesEarlierFromItsStart() {
        val plan = listOf(Step("2026-01-01", 10000, Every.MONTH), Step("2026-03-01", 5000, Every.MONTH))
        val rows = budgetRows(plan, "2026-04-15")
        assertEquals(listOf("2026-01-01" to 10000L, "2026-02-01" to 10000L, "2026-03-01" to 5000L, "2026-04-01" to 5000L), rows.map { it.date to it.amount })
        assertEquals(plan[1], currentStep(plan, "2026-04-15"))
        // Amount 0 turns it off from then on.
        assertEquals(1, budgetRows(listOf(Step("2026-01-01", 100, Every.MONTH), Step("2026-02-01", 0, Every.MONTH)), "2026-06-01").size)
    }

    @Test fun withBudgetMidPeriodReplacesCurrentAndKeepsEarlier() {
        val plan = listOf(Step("2026-08-01", 10000, Every.MONTH, 5), Step("2026-10-01", 8000, Every.MONTH, 5))
        val next = withBudget(plan, "2026-10-13", 12000, Every.MONTH, 20)
        assertEquals(listOf(Step("2026-08-01", 10000, Every.MONTH, 5), Step("2026-10-01", 12000, Every.MONTH, 20)), next)
        val weekly = withBudget(next, "2026-10-14", 3000, Every.WEEK, 20)
        assertEquals(Step("2026-10-12", 3000, Every.WEEK, null), weekly.last())
        assertEquals(3, weekly.size)
    }

    @Test fun totalsAreTopupsMinusExpenses() {
        val t = totals(listOf(
            Tx("a", "topup", 5000, "", "2026-10-01", "u"),
            Tx("b", "expense", 1200, "Dinner", "2026-10-02", "u"),
            Tx("c", "expense", 800, "Movie", "2026-10-03", "v"),
        ))
        assertEquals(Totals(topped = 5000, spent = 2000, left = 3000), t)
    }

    @Test fun legacyMonthStepIsMonthlyFromTheFirst() {
        val plan = steps(Json.parseToJsonElement("""[{"from":"2026-02","amount":4000},{"from":"bad","amount":1,"every":"week"},{"from":"2026-03-02","amount":-5,"every":"week"}]"""))
        assertEquals(listOf(Step("2026-02-01", 4000, Every.MONTH)), plan)
        assertEquals(listOf("2026-02-01", "2026-03-01"), budgetRows(plan, "2026-03-10").map { it.date })
        assertEquals(listOf(Step("2026-03-02", 700, Every.FORTNIGHT)),
            steps(Json.parseToJsonElement("""[{"from":"2026-03-02","amount":700.0,"every":"fortnight"},{"from":"2026-03-02","amount":1,"every":"month"}]""")))
    }
}
