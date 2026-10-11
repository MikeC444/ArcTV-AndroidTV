package com.mangotv.app.data.player

import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamHandoffTest {
    private fun stream(id: String) = Stream(id = id, providerId = "p", providerLabel = "Addon", resolutionTier = ResolutionTier.FHD_1080P, qualityBadge = "1080p", releaseTitle = "Movie.1080p")

    @Test
    fun `the source just picked is found again by its id, also on a retry`() {
        StreamHandoff.put(stream("a"))
        assertEquals("a", StreamHandoff.get("a")?.id)
        assertEquals("a", StreamHandoff.get("a")?.id)
    }

    @Test
    fun `a different source or title never gets it`() {
        StreamHandoff.put(stream("a"))
        assertNull(StreamHandoff.get("b"))
        assertNull(StreamHandoff.get(""))
    }

    @Test
    fun `the latest pick replaces the earlier one`() {
        StreamHandoff.put(stream("a"))
        StreamHandoff.put(stream("b"))
        assertNull(StreamHandoff.get("a"))
        assertEquals("b", StreamHandoff.get("b")?.id)
    }
}
