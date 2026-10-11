package com.mangotv.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStampTest {
    @Test
    fun `a change is stamped with now when the account is behind`() {
        assertEquals(10_000L, SettingsStamp.next(nowMs = 10_000L, accountMs = 5_000L))
        assertEquals(10_000L, SettingsStamp.next(nowMs = 10_000L, accountMs = 0L))
    }

    @Test
    fun `a change is stamped just after the account when this clock is behind it`() {
        assertEquals(20_001L, SettingsStamp.next(nowMs = 10_000L, accountMs = 20_000L))
        assertEquals(10_001L, SettingsStamp.next(nowMs = 10_000L, accountMs = 10_000L))
    }

    @Test
    fun `a write is refused only when the account holds a later one`() {
        assertTrue(SettingsStamp.refused(sentMs = 10_000L, accountMs = 10_001L))
        assertFalse(SettingsStamp.refused(sentMs = 10_000L, accountMs = 10_000L))
        assertFalse(SettingsStamp.refused(sentMs = 10_000L, accountMs = 5_000L))
        assertFalse(SettingsStamp.refused(sentMs = 10_000L, accountMs = null))
    }
}
