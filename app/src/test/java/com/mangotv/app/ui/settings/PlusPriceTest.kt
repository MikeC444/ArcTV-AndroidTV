package com.mangotv.app.ui.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlusPriceTest {

    @Test
    fun `an amount in pence reads as pounds`() {
        assertEquals("£59.99", formatPlusPrice(5999, "gbp", Locale.UK))
        assertEquals("£2.99", formatPlusPrice(299, "GBP", Locale.UK))
    }

    @Test
    fun `a currency without minor units isn't divided`() {
        assertEquals(true, formatPlusPrice(500, "jpy", Locale.UK).orEmpty().contains("500"))
    }

    @Test
    fun `missing or unknown parts give no price`() {
        assertNull(formatPlusPrice(null, "gbp"))
        assertNull(formatPlusPrice(5999, null))
        assertNull(formatPlusPrice(5999, "  "))
        assertNull(formatPlusPrice(5999, "not-a-currency"))
    }

    @Test
    fun `the countdown shows minutes and seconds and never goes below zero`() {
        assertEquals("09:58", formatCountdown(598))
        assertEquals("10:00", formatCountdown(600))
        assertEquals("00:00", formatCountdown(-3))
    }

    @Test
    fun `each plan says how it is billed`() {
        assertEquals("Monthly", billingLabel("monthly"))
        assertEquals("/ year", billingSuffix("yearly"))
        assertNull(billingSuffix("lifetime"))
        assertEquals("One-time payment", billingLabel("lifetime"))
    }
}
