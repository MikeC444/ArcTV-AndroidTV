package com.mangotv.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusPlansTest {

    @Test
    fun `there are three plans and each says what it is`() {
        assertEquals(listOf("monthly", "yearly", "lifetime"), PLUS_PLANS.map { it.id })
        assertTrue(PLUS_PLANS.all { it.label.isNotBlank() && it.blurb.isNotBlank() && it.per.isNotBlank() })
    }

    @Test
    fun `every perk says what it is, and Picked for you is the one that is on`() {
        assertTrue(PLUS_PERKS.isNotEmpty())
        assertTrue(PLUS_PERKS.all { it.title.isNotBlank() && it.detail.isNotBlank() })
        assertEquals(listOf("Picked for you", "Profiles"), PLUS_PERKS.filter { !it.comingSoon }.map { it.title })
    }
}
