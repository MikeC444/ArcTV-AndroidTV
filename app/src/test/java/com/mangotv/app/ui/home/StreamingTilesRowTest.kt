package com.mangotv.app.ui.home

import com.mangotv.app.data.model.RowStyle
import com.mangotv.app.data.provider.HomeRowPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingTilesRowTest {

    private fun ids(services: List<String>, prefs: HomeRowPreferences = HomeRowPreferences()) = streamingTilesSection(services, prefs)?.items?.map { it.id }

    @Test
    fun `the tiles are the services an addon has, in the default order, leaving out the ones that are off until turned on`() {
        assertEquals(listOf("streaming:netflix", "streaming:disney", "streaming:hulu"), ids(listOf("hulu", "crunchyroll", "disney", "netflix")))
    }

    @Test
    fun `a service the person turned off is left out, and one they turned on is added`() {
        val prefs = HomeRowPreferences(hiddenRowIds = setOf("streaming:netflix", "streaming-on:crunchyroll"))
        assertEquals(listOf("streaming:disney", "streaming:crunchyroll"), ids(listOf("netflix", "disney", "crunchyroll"), prefs))
    }

    @Test
    fun `the persons own order wins`() {
        val prefs = HomeRowPreferences(order = listOf("Popular_base", "streaming:hulu", "streaming:netflix"))
        assertEquals(listOf("streaming:hulu", "streaming:netflix", "streaming:disney"), ids(listOf("netflix", "disney", "hulu"), prefs))
    }

    @Test
    fun `there is no row when no service is left to show`() {
        assertNull(streamingTilesSection(emptyList(), HomeRowPreferences()))
        assertNull(streamingTilesSection(listOf("netflix"), HomeRowPreferences(hiddenRowIds = setOf("streaming:netflix"))))
        assertNull(streamingTilesSection(listOf("crunchyroll"), HomeRowPreferences()))
    }

    @Test
    fun `the row is a streaming row whose tiles are stand-ins named after the service`() {
        val section = streamingTilesSection(listOf("prime"), HomeRowPreferences())!!
        assertEquals(STREAMING_SECTION_ID, section.id)
        assertEquals(RowStyle.STREAMING_SERVICES, section.style)
        assertEquals("Prime Video", section.items.single().title)
        assertEquals("prime", serviceOfTileId(section.items.single().id))
        assertNull(serviceOfTileId("tt0111161"))
    }
}
