package com.nyxulrix.budgeter.ui

import com.nyxulrix.budgeter.core.Tx
import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.Setup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateCoverTest {
    private val st = AppState(Setup("SG", "SGD", 1))
    private fun tx(id: String, kind: String, amount: Long, date: String) = Tx(id, kind, amount, "", date, "me")
    private val oct31 = LocalDate.of(2026, 10, 31)

    // September left 50 unspent; October has 57 left. Months don't carry over.
    private val all = listOf(
        tx("s1", "topup", 15_000, "2026-09-01"), tx("s2", "expense", 10_000, "2026-09-20"),
        tx("o1", "topup", 10_000, "2026-10-01"), tx("o2", "expense", 4_300, "2026-10-10"),
    )

    @Test fun overspendIsCountedAgainstTheExpensesOwnMonth() {
        assertEquals(1_300L, st.dateCover(all, oct31, 7_000))                 // 70 dinner, 57 left this month; September's 50 doesn't help
        val dinner = tx("o3", "expense", 7_000, "2026-10-31")
        val covered = all + dinner + Tx("o4", "topup", 1_300, "Over budget: sweet cheeks", "2026-10-31", "me")
        val named = dinner.copy(note = "sweet cheeks")
        val coveredNamed = all + named + covered.last()
        assertEquals(1_300L, st.dateCover(coveredNamed, oct31, 7_000, named)) // re-saving: the same cover again, replacing its old top-up
        assertEquals(0L, st.dateCover(coveredNamed, oct31, 5_000, named))     // lowered to 50: fits, so no cover
        assertEquals(0L, st.dateCover(covered, oct31, 7_000, dinner))         // a top-up not matched to it still counts in the month: nothing more
        val sepDinner = tx("s3", "expense", 7_000, "2026-09-25")
        assertEquals(1_300L, st.dateCover(all + sepDinner, oct31, 7_000, sepDinner)) // moved from September: its old amount freed September, not October
    }
}
