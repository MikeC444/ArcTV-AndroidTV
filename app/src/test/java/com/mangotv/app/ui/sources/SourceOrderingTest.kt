package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceOrderingTest {

    private fun stream(id: String, tier: ResolutionTier, seeders: Int? = null, size: Long? = null) = Stream(
        id = id, providerId = "p", providerLabel = "P", resolutionTier = tier, qualityBadge = "", releaseTitle = id,
        seeders = seeders, sizeBytes = size
    )

    private val hd = stream("hd", ResolutionTier.HD_720P, seeders = 900)
    private val fhd = stream("fhd", ResolutionTier.FHD_1080P, seeders = 10)
    private val uhd = stream("uhd", ResolutionTier.UHD_4K, seeders = 50)
    private val all = listOf(hd, fhd, uhd)

    private fun ids(streams: List<Stream>) = streams.map { it.id }

    @Test
    fun `quality sort puts the best resolution first then the most seeded`() {
        assertEquals(listOf("uhd", "fhd", "hd"), ids(sortSources(all, SourceSort.QUALITY)))
    }

    @Test
    fun `the recommended source is first whatever the sort`() {
        assertEquals(listOf("hd", "uhd", "fhd"), ids(orderSources(all, all, "hd", SourceSort.QUALITY)))
        assertEquals(listOf("fhd", "hd", "uhd"), ids(orderSources(all, all, "fhd", SourceSort.SEEDERS)))
    }

    @Test
    fun `the recommended source stays first even when the filter would hide it`() {
        val only720 = listOf(hd)
        assertEquals(listOf("uhd", "hd"), ids(orderSources(all, only720, "uhd", SourceSort.QUALITY)))
    }

    @Test
    fun `the recommended source is listed once`() {
        val out = orderSources(all, all, "uhd", SourceSort.SIZE)
        assertEquals(1, out.count { it.id == "uhd" })
    }

    @Test
    fun `with no recommendation the list is just the sorted filter`() {
        assertEquals(listOf("uhd", "fhd", "hd"), ids(orderSources(all, all, null, SourceSort.QUALITY)))
        assertEquals(listOf("uhd", "fhd", "hd"), ids(orderSources(all, all, "gone", SourceSort.QUALITY)))
    }
}
