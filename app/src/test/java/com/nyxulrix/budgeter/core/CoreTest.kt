package com.nyxulrix.budgeter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoreTest {
    @Test fun allocateSumsExactly() {
        assertEquals(listOf(34L, 33L, 33L), allocate(100, listOf(1, 1, 1)))
        assertEquals(listOf(-34L, -33L, -33L), allocate(-100, listOf(1, 1, 1)))
        assertEquals(listOf(0L, 0L), allocate(0, listOf(5, 5)))
        assertEquals(listOf(5L, 5L), allocate(10, listOf(0, 0)))
        val parts = allocate(1001, listOf(333, 333, 334))
        assertEquals(1001L, parts.sum())
    }

    @Test fun parsesMoney() {
        assertEquals(1250L, parseMoney("12.50", "SGD"))
        assertEquals(1250L, parseMoney("12,50", "EUR"))
        assertEquals(123450L, parseMoney("1,234.50", "SGD"))
        assertEquals(123400L, parseMoney("1,234", "SGD"))
        assertEquals(1200L, parseMoney("S$12", "SGD"))
        assertEquals(300L, parseMoney("300", "JPY"))
        assertEquals(1234L, parseMoney("1.234", "KWD"))
        assertNull(parseMoney("abc", "SGD"))
        assertEquals("12.50", plain(1250, "SGD"))
        assertTrue(money(123_456_78, "SGD").endsWith("123,456.78"))
        assertTrue(money(-500, "JPY").startsWith("-") && money(-500, "JPY").endsWith("500"))
        assertEquals(9_000L, convert(10_000, "JPY", "SGD", 0.009))
    }

    @Test fun periodsWithCustomStartDay() {
        val p = periodOf(LocalDate.of(2026, 10, 3), 25)
        assertEquals(LocalDate.of(2026, 9, 25), p.start)
        assertEquals(LocalDate.of(2026, 10, 25), p.end)
        assertEquals("2026-09", p.key)
        assertEquals(30, p.days)
        assertEquals(p, Period.ofKey("2026-09", 25))
        assertEquals("2026-10", p.next().key)
        assertEquals(LocalDate.of(2026, 1, 1), periodOf(LocalDate.of(2026, 1, 31), 1).start)
    }

    @Test fun dailyBudgetAdjustsAfterOverspend() {
        val p = periodOf(LocalDate.of(2026, 10, 1), 1)            // 31 days
        val d1 = today(31_000, emptyList(), 5_000, 0, LocalDate.of(2026, 10, 1), p)
        assertEquals(1_000L, d1.budget)
        assertEquals(-4_000L, d1.remaining)
        val d2 = today(31_000, listOf(DaySpend(5_000)), 0, 0, LocalDate.of(2026, 10, 2), p)  // 30 days left
        assertEquals(866L, d2.budget)
        val broke = today(1_000, listOf(DaySpend(5_000)), 100, 0, LocalDate.of(2026, 10, 2), p)
        assertEquals(0L, broke.budget)
        val last = today(31_000, List(30) { DaySpend(1_000) }, 0, 0, LocalDate.of(2026, 10, 31), p)
        assertEquals(1_000L, last.budget)
        // Underspending carries forward as a bonus: 2 days × 1000 base − 500 spent = 1500 saved up.
        val saver = today(31_000, listOf(DaySpend(500), DaySpend(0)), 0, 0, LocalDate.of(2026, 10, 3), p)
        assertEquals(1_000L, saver.budget)
        assertEquals(1_500L, saver.bonus)
        assertEquals(2_500L, saver.remaining)
        assertEquals(0L, today(31_000, emptyList(), 0, 0, LocalDate.of(2026, 10, 1), p).bonus)   // nothing before day 1
        // Headline shows only today's own budget; spending past it uses the saved-up bonus first.
        val dipped = today(31_000, listOf(DaySpend(500), DaySpend(0)), 1_400, 0, LocalDate.of(2026, 10, 3), p)
        assertEquals(-400L, dipped.dailyLeft)
        assertEquals(1_100L, dipped.bonusLeft)
        assertEquals(1_100L, dipped.remaining)
        // Regression: an overspent day doesn't stop a later unused day from carrying forward.
        val after = today(31_000, listOf(DaySpend(1_500), DaySpend(790)), 0, 0, LocalDate.of(2026, 10, 3), p)
        assertEquals(983L, after.budget)
        assertEquals(193L, after.bonus)
        // Groceries eat the leftovers first, then lower the remaining days.
        val groceries = today(31_000, listOf(DaySpend(0), DaySpend(0, monthly = 2_500)), 0, 0, LocalDate.of(2026, 10, 3), p)
        assertEquals(0L, groceries.bonus)
        assertEquals(982L, groceries.budget)
    }

    @Test fun pacing() {
        val p = periodOf(LocalDate.of(2026, 10, 1), 1)
        val day10 = LocalDate.of(2026, 10, 10)                    // expected 31000*10/31 = 10000
        assertEquals(Pace.ON_TRACK, pace(31_000, 10_000, day10, p))
        assertEquals(Pace.SLIGHTLY_OVER, pace(31_000, 11_000, day10, p))
        assertEquals(Pace.OVER, pace(31_000, 11_001, day10, p))
        assertEquals(Pace.ON_TRACK, pace(31_000, 0, LocalDate.of(2026, 10, 1), p))
        assertEquals(Pace.OVER, pace(-500, 1, day10, p))
    }

    @Test fun splits() {
        assertEquals(listOf(3334L, 3333L, 3333L), split(10_000, SplitMethod.EQUAL, listOf(0, 0, 0)))
        assertEquals(listOf(7_500L, 2_500L), split(10_000, SplitMethod.PERCENT, listOf(7_500, 2_500)))
        assertEquals(listOf(2_000L, 8_000L), split(10_000, SplitMethod.SHARES, listOf(1, 4)))
        assertEquals(listOf(1L, 9_999L), split(10_000, SplitMethod.EXACT, listOf(1, 9_999)))
        assertTrue(runCatching { split(10_000, SplitMethod.PERCENT, listOf(5_000, 4_000)) }.isFailure)
        assertTrue(runCatching { split(10_000, SplitMethod.EXACT, listOf(1, 2)) }.isFailure)
        // item 0 shared by everyone, item 1 only person 1
        assertEquals(listOf(500L, 1_500L), itemShares(listOf(1_000, 1_000), listOf(emptySet(), setOf(1)), 2))
    }


    @Test fun receiptAllocationUsesOnlyPrintedCharges() {
        // Singapore restaurant: 10% service charge then 9% GST, both printed.
        val r = ParsedReceipt(
            items = listOf(ReceiptItem("Laksa", 1_000), ReceiptItem("Kopi", 300), ReceiptItem("Kaya toast", 450)),
            subtotal = 1_750, serviceCharge = 175, tax = 173, total = 2_098,
        )
        assertEquals(0L, r.mismatch)
        val costs = r.itemCosts()
        assertEquals(2_098L, costs.sum())
        assertEquals(1_199L, costs[0])
        // Prices already include GST: tax line is informational, nothing added.
        val incl = ParsedReceipt(items = listOf(ReceiptItem("A", 1_090)), tax = 90, taxIncluded = true, total = 1_090)
        assertEquals(listOf(1_090L), incl.itemCosts())
        assertEquals(0L, incl.mismatch)
        // Bill-level discount spreads proportionally.
        val disc = ParsedReceipt(items = listOf(ReceiptItem("A", 1_000), ReceiptItem("B", 3_000)), discount = 400, total = 3_600)
        assertEquals(listOf(900L, 2_700L), disc.itemCosts())
        // Nothing printed, nothing added.
        assertEquals(listOf(500L), ParsedReceipt(items = listOf(ReceiptItem("X", 500))).itemCosts())
        // Wrong total is flagged, not guessed.
        assertEquals(100L, ParsedReceipt(items = listOf(ReceiptItem("X", 500)), total = 600).mismatch)
    }

    @Test fun parsesReceiptText() {
        val text = """
            BREAD & BUTTER CAFE
            12 Orchard Road #01-02
            Date: 06/10/2026 13:45
            2 x Kopi              6.00
            Laksa                12.50
            Iced Lemon Tea
            4.50
            Member Disc          -1.00
            Subtotal             22.00
            Svc Chg 10%           2.20
            GST 9%                2.18
            TOTAL               26.38
            VISA                26.38
            Change                0.00
        """.trimIndent()
        val r = ReceiptText.parse(text, "SGD")
        assertEquals("BREAD & BUTTER CAFE", r.merchant)
        assertEquals("2026-10-06", r.date)
        assertEquals(listOf("Kopi", "Laksa", "Iced Lemon Tea", "Member Disc"), r.items.map { it.name })
        assertEquals(2, r.items[0].qty)
        assertEquals(-100L, r.items[3].price)
        assertEquals(2_200L, r.subtotal)
        assertEquals(220L, r.serviceCharge)
        assertEquals(218L, r.tax)
        assertEquals(2_638L, r.total)
        assertEquals(0L, r.mismatch)

        val yen = ReceiptText.parse("RAMEN YA\nShoyu Ramen ¥1,200\nGyoza 450\nTotal ¥1,650", "JPY")
        assertEquals(listOf(1_200L, 450L), yen.items.map { it.price })
        assertEquals(1_650L, yen.total)
        val dinar = ReceiptText.parse("CAFE\nTea 1.250\nTotal 1.250", "KWD")
        assertEquals(1_250L, dinar.total)

        val inclusive = ReceiptText.parse("SHOP\nApple 3.27\nTotal 3.27\nGST incl. 0.27", "SGD")
        assertTrue(inclusive.taxIncluded)
        assertEquals(0L, inclusive.mismatch)

        // Header rows aren't items, even when a tilted photo puts the price on the header's row
        val body = listOf("1 Signature Crispy Chicken Ricebox ALC", "More Spicy*", "1 SubTotal   7.90", "GST (9% Incl.)   0.65", "Net Total   7.90", "Master   7.90")
        for (rows in listOf(
            listOf("SHIHLIN TAIWAN STREET FOOD", "*** Retail/Takeaway ***", "1 Signature Crispy Chicken Ricebox   7.90") + body,
            listOf("SHIHLIN TAIWAN STREET FOOD", "*** Retail/Takeaway ***   7.90", "1 Signature Crispy Chicken Ricebox") + body,
        )) {
            val r = ReceiptText.parse(rows.joinToString(System.lineSeparator()), "SGD")
            assertEquals(listOf(ReceiptItem("Signature Crispy Chicken Ricebox", 790, 1)), r.items)
            assertEquals(790L, r.total)
            assertEquals(65L, r.tax)
            assertTrue(r.taxIncluded)
        }
        // A misread "Total" is still the total, not an item
        val misread = ReceiptText.parse(listOf("SHOP", "Ricebox   7.90", "1 Subtotao   7.90", "Net Tota1   7.90", "Net Totai   7.90").joinToString("\n"), "SGD")
        assertEquals(listOf("Ricebox"), misread.items.map { it.name })
        assertEquals(790L, misread.total)
        assertEquals(790L, misread.subtotal)
    }

    @Test fun findsDates() {
        assertEquals("2026-10-06", ReceiptText.findDate("2026-10-06 10:00"))
        assertEquals("2026-10-06", ReceiptText.findDate("6 Oct 2026"))
        assertEquals("2026-12-31", ReceiptText.findDate("12/31/26"))   // impossible day-first, read month-first
        assertNull(ReceiptText.findDate("Table 12"))
    }
}
