package com.mangotv.app.data.plus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusNudgeRulesTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun `nothing is asked before the account's record has been read`() {
        assertFalse(nudgeDue(null, NudgeTrigger.AFTER_WATCH, now))
    }

    @Test
    fun `an account that has never been asked may be`() {
        assertTrue(nudgeDue(NudgeRecord(), NudgeTrigger.AFTER_WATCH, now))
    }

    @Test
    fun `two asks are at least three days apart`() {
        val record = NudgeRecord(lastShownAt = now - 2 * day, shown = mapOf(NudgeTrigger.AFTER_WATCH to 1))
        assertFalse("two days after the last ask", nudgeDue(record, NudgeTrigger.AFTER_WATCH, now))
        assertTrue("three days after", nudgeDue(record.copy(lastShownAt = now - 3 * day), NudgeTrigger.AFTER_WATCH, now))
    }

    @Test
    fun `a kind is shown at most three times and then never again`() {
        val old = now - 10 * day
        assertTrue(nudgeDue(NudgeRecord(old, mapOf(NudgeTrigger.AFTER_WATCH to 2)), NudgeTrigger.AFTER_WATCH, now))
        assertFalse(nudgeDue(NudgeRecord(old, mapOf(NudgeTrigger.AFTER_WATCH to 3)), NudgeTrigger.AFTER_WATCH, now))
    }

    @Test
    fun `the gap applies across kinds but the cap is counted per kind`() {
        val record = NudgeRecord(lastShownAt = now - day, shown = mapOf("other" to 3))
        assertFalse("another kind was shown yesterday", nudgeDue(record, NudgeTrigger.AFTER_WATCH, now))
        assertTrue("another kind being capped does not cap this one", nudgeDue(record.copy(lastShownAt = now - 4 * day), NudgeTrigger.AFTER_WATCH, now))
    }
}
