package com.mangotv.app.data.addon

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentStreamMappingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test fun torrentioStyleStreamKeepsFileIndexTrackersAndFilename() {
        val stream = json.decodeFromString<StremioStreamResponse>(
            """{"streams":[{"name":"Torrentio\n1080p","title":"Show.S02E03.1080p.WEB-DL.x265\n👤 120 💾 1.4 GB",
               "infoHash":"$hash","fileIdx":4,"sources":["tracker:udp://t.example:80/announce","dht:$hash"],
               "behaviorHints":{"bingeGroup":"x","filename":"Show.S02E03.1080p.WEB-DL.x265.mkv"}}]}"""
        ).streams.single().toStream("p", "Torrentio")
        assertEquals(hash, stream.infoHash)
        assertEquals(4, stream.fileIdx)
        assertEquals(listOf("udp://t.example:80/announce"), stream.trackers)
        assertEquals("Show.S02E03.1080p.WEB-DL.x265.mkv", stream.torrentFilename)
        assertNull(stream.url)
    }

    @Test fun plainStreamsAreUnchanged() {
        val stream = json.decodeFromString<StremioStreamResponse>("""{"streams":[{"url":"https://cdn.example/a.m3u8","title":"HLS"}]}""")
            .streams.single().toStream("p", "Addon")
        assertEquals("https://cdn.example/a.m3u8", stream.url)
        assertNull(stream.fileIdx)
        assertEquals(emptyList<String>(), stream.trackers)
    }

    @Test fun sameTorrentOfferedPerFileGetsDistinctIds() {
        val a = StremioStream(infoHash = hash, fileIdx = 1, title = "a").toStream("p", "x")
        val b = StremioStream(infoHash = hash, fileIdx = 2, title = "b").toStream("p", "x")
        val c = StremioStream(infoHash = hash, title = "c").toStream("p", "x")
        assertNotEquals(a.id, b.id)
        assertNotEquals(a.id, c.id)
        assertEquals(c.id, StremioStream(infoHash = hash, title = "again").toStream("p", "x").id) // ids without a file index are unchanged
    }

    @Test fun negativeFileIndexIsIgnored() {
        assertNull(StremioStream(infoHash = hash, fileIdx = -1).toStream("p", "x").fileIdx)
    }
}
