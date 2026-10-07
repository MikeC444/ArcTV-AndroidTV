package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.DebridState
import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class SurePickTest {
    private fun stream(url: String? = "https://x.test/a.mp4", infoHash: String? = null, seeders: Int? = null, debrid: DebridState? = null) =
        Stream(id = "s", providerId = "p", providerLabel = "P", resolutionTier = ResolutionTier.FHD_1080P, qualityBadge = "1080p", releaseTitle = "r", url = url, infoHash = infoHash, seeders = seeders, debrid = debrid)

    @Test
    fun `a direct or cached debrid link is a sure pick`() {
        assertEquals(true, isSurePick(stream()))
        assertEquals(true, isSurePick(stream(debrid = DebridState("RD", cached = true))))
    }

    @Test
    fun `a debrid link that still has to be fetched is not`() {
        assertEquals(false, isSurePick(stream(debrid = DebridState("RD", cached = false))))
    }

    @Test
    fun `a torrent is a sure pick unless it is known to have no seeders`() {
        assertEquals(true, isSurePick(stream(url = null, infoHash = "abc", seeders = 12)))
        assertEquals(true, isSurePick(stream(url = null, infoHash = "abc", seeders = null)))
        assertEquals(false, isSurePick(stream(url = null, infoHash = "abc", seeders = 0)))
    }

    @Test
    fun `a source with no link at all is not`() {
        assertEquals(false, isSurePick(stream(url = null, infoHash = null)))
    }
}
