package com.mangotv.app.data.player

import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchLastSourceTest {
    private fun stream(id: String, release: String, addon: String = "Torrentio", hash: String? = null) = Stream(
        id = id, providerId = "p", providerLabel = addon, resolutionTier = ResolutionTier.values().first(),
        qualityBadge = "1080p", releaseTitle = release, infoHash = hash
    )

    @Test
    fun `the same id wins`() {
        val streams = listOf(stream("a", "Movie 1080p"), stream("b", "Movie 4K"))
        assertEquals("b", matchLastSource(streams, LastSource(streamId = "b", releaseTitle = "Movie 1080p"))?.id)
    }

    @Test
    fun `a changed id is found again by its info hash`() {
        val streams = listOf(stream("x1", "Other", hash = "AAA"), stream("x2", "Movie 1080p", hash = "BBB"))
        assertEquals("x2", matchLastSource(streams, LastSource(streamId = "old", releaseTitle = "Movie 1080p", infoHash = "bbb"))?.id)
    }

    @Test
    fun `without a hash the same release from the same addon is found, then the same release from any addon`() {
        val streams = listOf(stream("c", "Movie 1080p", addon = "Other"), stream("d", "Movie 1080p", addon = "Torrentio"))
        assertEquals("d", matchLastSource(streams, LastSource("old", providerLabel = "Torrentio", releaseTitle = "Movie 1080p"))?.id)
        assertEquals("c", matchLastSource(streams, LastSource("old", providerLabel = "Gone", releaseTitle = "Movie 1080p"))?.id)
    }

    @Test
    fun `nothing is guessed when the source is gone or only its id was kept`() {
        val streams = listOf(stream("e", "Something else"))
        assertNull(matchLastSource(streams, LastSource("old", releaseTitle = "Movie 1080p")))
        assertNull(matchLastSource(streams, LastSource("old")))
    }
}
