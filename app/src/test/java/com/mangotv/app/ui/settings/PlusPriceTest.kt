package com.mangotv.app.ui.settings

import com.mangotv.app.data.network.PlusPriceDto
import com.mangotv.app.data.network.PlusPricesDto
import com.mangotv.app.data.plus.PlanPrice
import com.mangotv.app.data.plus.PlanPrices
import com.mangotv.app.data.plus.parsePlanPrices
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

    @Test
    fun `a free trial is only for the yearly plan, and only when the backend offers it`() {
        assertEquals(false, hasTrial("monthly", 5)) // monthly never gets one
        assertEquals(true, hasTrial("yearly", 5))
        assertEquals(false, hasTrial("lifetime", 5))
        assertEquals(false, hasTrial("monthly", 0))
        assertEquals("Start 5-day free trial", planButtonLabel("yearly", "Yearly", 5))
        assertEquals("Choose Monthly", planButtonLabel("monthly", "Monthly", 0))
        assertEquals("Choose Monthly", planButtonLabel("monthly", "Monthly", 5))
        assertEquals("Get Lifetime", planButtonLabel("lifetime", "Lifetime", 5))
        assertEquals(true, trialBillingNote("yearly", 5).contains("5 days"))
    }

    private val usd = PlanPrices(PlanPrice(499, "usd"), PlanPrice(3999, "usd"), PlanPrice(9999, "usd"))

    @Test
    fun `the yearly card shows the saving, the monthly price struck against it and the per day figure`() {
        val yearly = planPriceText("yearly", usd, Locale.US)!!
        assertEquals("$39.99", yearly.price)
        assertEquals("$4.99", yearly.was)
        assertEquals("Save 33%", yearly.saving)
        assertEquals("$3.33/mo billed yearly, about $0.11 a day", yearly.perDay)
    }

    @Test
    fun `monthly and lifetime cards say their price, and lifetime compares itself with yearly`() {
        assertEquals("$4.99", planPriceText("monthly", usd, Locale.US)!!.price)
        val lifetime = planPriceText("lifetime", usd, Locale.US)!!
        assertEquals("$99.99", lifetime.price)
        assertEquals("One payment instead of $39.99 every year", lifetime.versus)
    }

    @Test
    fun `no saving or strike-through unless yearly really is cheaper per month`() {
        val dearer = PlanPrices(PlanPrice(300, "usd"), PlanPrice(3999, "usd"), PlanPrice(9999, "usd"))
        val card = planPriceText("yearly", dearer, Locale.US)!!
        assertNull(card.saving)
        assertNull(card.was)
        assertEquals(true, card.perDay.orEmpty().contains("a day"))
    }

    @Test
    fun `mixed currencies give no saving and no comparison`() {
        val mixed = PlanPrices(PlanPrice(499, "usd"), PlanPrice(3999, "gbp"), PlanPrice(9999, "usd"))
        assertNull(savingPercent(mixed))
        assertNull(planPriceText("lifetime", mixed, Locale.US)!!.versus)
    }

    @Test
    fun `a whole amount drops its zero cents and no prices means no price text`() {
        assertEquals("$40", formatPlusPrice(4000, "usd", Locale.US, dropWholeCents = true))
        assertEquals("$40.00", formatPlusPrice(4000, "usd", Locale.US))
        assertNull(planPriceText("yearly", null, Locale.US))
        assertNull(planPriceText("unknown", usd, Locale.US))
    }

    @Test
    fun `the backend's prices are read defensively`() {
        val good = PlusPricesDto(PlusPriceDto(499.0, "GBP"), PlusPriceDto(3999.0, "gbp"), PlusPriceDto(9999.0, "gbp"))
        assertEquals(PlanPrice(499, "gbp"), parsePlanPrices(good)!!.monthly)
        assertNull(parsePlanPrices(null))
        assertNull(parsePlanPrices(good.copy(yearly = null)))
        assertNull(parsePlanPrices(good.copy(lifetime = PlusPriceDto(0.0, "gbp"))))
        assertNull(parsePlanPrices(good.copy(monthly = PlusPriceDto(499.0, "pounds"))))
    }
}
