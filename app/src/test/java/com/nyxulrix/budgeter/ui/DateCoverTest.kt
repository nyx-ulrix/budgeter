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
        val covered = all + dinner + tx("o4", "topup", 1_300, "2026-10-31")
        assertEquals(0L, st.dateCover(covered, oct31, 7_000, dinner))        // re-saving a covered expense charges nothing
        val undercharged = all + dinner                                       // saved before the fix: nothing was charged
        assertEquals(1_300L, st.dateCover(undercharged, oct31, 7_000, dinner)) // saving it again charges the rest
        val sepDinner = tx("s3", "expense", 7_000, "2026-09-25")
        assertEquals(1_300L, st.dateCover(all + sepDinner, oct31, 7_000, sepDinner)) // moved from September: its old amount freed September, not October
    }
}
