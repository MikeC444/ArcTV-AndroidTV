package com.mangotv.app.data.plus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusPromoRulesTest {

    @Test
    fun `someone who has never answered it is due`() {
        assertTrue(promoDue(PromoRecord(), shownThisSession = false, now = 1_000L))
    }

    @Test
    fun `it is not shown twice in one launch`() {
        assertFalse(promoDue(PromoRecord(), shownThisSession = true, now = 1_000L))
    }

    @Test
    fun `close snoozes it for five days and then it may come back`() {
        val snoozed = PromoRecord(snoozedUntil = 1_000L + PROMO_SNOOZE_MS)
        assertFalse(promoDue(snoozed, shownThisSession = false, now = 1_000L + PROMO_SNOOZE_MS - 1))
        assertTrue(promoDue(snoozed, shownThisSession = false, now = 1_000L + PROMO_SNOOZE_MS))
    }

    @Test
    fun `dont show me again ends it for good`() {
        val never = PromoRecord(never = true)
        assertFalse(promoDue(never, shownThisSession = false, now = Long.MAX_VALUE))
    }
}
