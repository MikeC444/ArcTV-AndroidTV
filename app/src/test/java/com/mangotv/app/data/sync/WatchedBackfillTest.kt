package com.mangotv.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedBackfillTest {

    @Test
    fun `catch-up runs for an account with an empty My List`() {
        assertTrue(shouldRunWatchedBackfill(myListSize = 0))
    }

    @Test
    fun `catch-up is skipped once the account already has titles so removed ones stay removed`() {
        assertFalse(shouldRunWatchedBackfill(myListSize = 1))
        assertFalse(shouldRunWatchedBackfill(myListSize = 40))
    }
}
