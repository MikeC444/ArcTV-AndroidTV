package com.mangotv.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusPlansTest {

    @Test
    fun `there are three plans and Plus is not on sale until one has a checkout page`() {
        assertEquals(listOf("monthly", "yearly", "lifetime"), PLUS_PLANS.map { it.id })
        assertFalse(plusIsOnSale())
        assertTrue(plusIsOnSale(PLUS_PLANS.map { it.copy(checkoutUrl = "https://pay.example/${it.id}") }))
        assertFalse(plusIsOnSale(PLUS_PLANS.map { it.copy(checkoutUrl = "  ") }))
    }

    @Test
    fun `every perk is announced as coming soon and every plan says what it is`() {
        assertTrue(PLUS_PERKS.isNotEmpty())
        assertTrue(PLUS_PERKS.all { it.comingSoon && it.title.isNotBlank() && it.detail.isNotBlank() })
        assertTrue(PLUS_PLANS.all { it.label.isNotBlank() && it.blurb.isNotBlank() && it.per.isNotBlank() })
    }
}
