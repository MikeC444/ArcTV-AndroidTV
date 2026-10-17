package com.mangotv.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemovalRetryTimeTest {

    @Test
    fun `a removal turned down because the entry was written later is stamped just after the entry`() {
        // this device's clock is a day behind the time the entry was last written
        val written = 1_000_000_000L
        assertEquals(written + 1, removalRetryTime(nowMs = written - 86_400_000, sentAtMs = written - 86_400_000, entryWrittenAtMs = written))
    }

    @Test
    fun `it is never earlier than now`() {
        assertEquals(5_000L, removalRetryTime(nowMs = 5_000L, sentAtMs = 4_000L, entryWrittenAtMs = 100L))
    }

    @Test
    fun `there is nothing to ask again when it would not be later than what was sent`() {
        assertNull(removalRetryTime(nowMs = 1_000L, sentAtMs = 2_000L, entryWrittenAtMs = 500L))
        assertNull(removalRetryTime(nowMs = 1_000L, sentAtMs = 1_000L, entryWrittenAtMs = 999L))
    }
}
