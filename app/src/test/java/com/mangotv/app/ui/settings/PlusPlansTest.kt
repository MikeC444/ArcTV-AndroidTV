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
        assertEquals(listOf("Picked for you", "Profiles", "Smart source picking", "Your stats"), PLUS_PERKS.filter { !it.comingSoon && !it.otherDevicesOnly }.map { it.title })
    }

    @Test
    fun `perks that are coming soon come last, and Download is listed but is not on the TV`() {
        val firstSoon = PLUS_PERKS.indexOfFirst { it.comingSoon }
        assertTrue(firstSoon >= 0)
        assertTrue(PLUS_PERKS.drop(firstSoon).all { it.comingSoon })
        val download = PLUS_PERKS.single { it.title == "Download" }
        assertTrue(download.otherDevicesOnly && !download.comingSoon)
    }
}
