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
    fun `every perk and plan says what it is, and the early access perks are the ones that are on`() {
        assertTrue(PLUS_PERKS.isNotEmpty())
        assertTrue(PLUS_PERKS.all { it.title.isNotBlank() && it.detail.isNotBlank() })
        assertEquals(listOf("Picked for you"), PLUS_PERKS.filter { !it.comingSoon }.map { it.title })
        assertTrue(PLUS_EARLY_ACCESS && PLUS_TAB_VISIBLE)
        assertTrue(PLUS_PLANS.all { it.label.isNotBlank() && it.blurb.isNotBlank() && it.per.isNotBlank() })
    }
}
