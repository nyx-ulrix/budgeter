package com.nyxulrix.budgeter.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesVersionsNumerically() {
        assertTrue(Updater.newer("0.10.0", "0.9.3"))
        assertTrue(Updater.newer("v1.0.0", "0.99.99"))
        assertTrue(Updater.newer("0.2.1", "0.2"))
        assertFalse(Updater.newer("0.2.0", "0.2.0"))
        assertFalse(Updater.newer("0.1.9", "0.2.0"))
    }
}
