package com.mangotv.app.data.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class TorrentFileReaderTest {
    private val valid = "d8:announce20:http://t.example/a/xx4:infod6:lengthi5e4:name1:aee".toByteArray()

    @Test fun acceptsABencodedDictionary() {
        assertArrayEquals(valid, readTorrentFileBytes(ByteArrayInputStream(valid)))
        assertTrue(looksLikeTorrentFile(valid + "\n".toByteArray()))
    }

    @Test fun rejectsHtmlJsonAndEmptyBodies() {
        listOf("<html><body>Not found, sorry about that</body></html>", "{\"error\":\"nope nope nope nope\"}", "", "d").forEach { body ->
            try {
                readTorrentFileBytes(ByteArrayInputStream(body.toByteArray()))
                fail("accepted: $body")
            } catch (e: TorrentStreamException) {
                assertEquals(TorrentErrorKind.INVALID_SOURCE, e.kind)
            }
        }
    }

    @Test fun rejectsOversizedInput() {
        val big = ByteArray(2_000) { 'd'.code.toByte() }
        try {
            readTorrentFileBytes(ByteArrayInputStream(big), maxBytes = 1_000)
            fail("accepted")
        } catch (e: TorrentStreamException) {
            assertTrue(e.message.contains("too large"))
        }
    }
}
