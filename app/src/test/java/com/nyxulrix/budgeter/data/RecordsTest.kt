package com.nyxulrix.budgeter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {
    private val setup = Setup("SG", "SGD", 1)

    @Test fun changesFlowBothWaysAndDeletesWin() {
        val phone0 = AppState(setup, txns = listOf(Txn(id = "a", date = "2026-10-01", total = 500, updatedAt = 1)))
        // First sync from an existing phone: everything uploads with stamp 1 (never stamped).
        val sheet = phone0.toUpload(emptyMap()).toMutableMap()
        assertTrue(sheet.keys.containsAll(listOf("setup", "categories", "txn:a")))
        // The web starts empty and takes it all.
        val web = AppState().mergeRemote(sheet)
        assertEquals(500L, web.txns.single().total)
        assertEquals(setup, web.setup)
        assertTrue(web.toUpload(sheet).isEmpty())
        // Web edits the txn and adds one; the phone deletes nothing yet.
        val web2 = web.copy(txns = listOf(web.txns.single().copy(total = 700), Txn(id = "b", date = "2026-10-02", total = 100))).stampedSince(web, 10)
        sheet += web2.toUpload(sheet)
        val phone = phone0.mergeRemote(sheet)
        assertEquals(listOf(700L, 100L), phone.txns.sortedBy { it.id }.map { it.total })
        // The phone removes b; the web gets the delete even though it still has b.
        val phone2 = phone.copy(txns = phone.txns.filterNot { it.id == "b" }).stampedSince(phone, 20)
        sheet += phone2.toUpload(sheet)
        assertNull(sheet["txn:b"]!!.json)
        assertEquals(listOf("a"), web2.mergeRemote(sheet).txns.map { it.id })
        // An older remote copy never overwrites a newer local edit.
        val web3 = web2.copy(categories = web2.categories + "Pets").stampedSince(web2, 30)
        assertTrue("Pets" in web3.mergeRemote(sheet).categories)
    }
}
