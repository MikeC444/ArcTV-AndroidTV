package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MagnetLinkTest {
    private val hex = "0123456789abcdef0123456789abcdef01234567"

    @Test fun parsesHexHashNameAndTrackers() {
        val m = MagnetLink.parse(
            "magnet:?xt=urn:btih:${hex.uppercase()}&dn=My+Movie%20%282024%29&tr=udp%3A%2F%2Ftracker.example%3A80%2Fannounce&tr=http%3A%2F%2Fb.example%2Fa"
        )!!
        assertEquals(hex, m.infoHash)
        assertEquals("My Movie (2024)", m.displayName)
        assertEquals(listOf("udp://tracker.example:80/announce", "http://b.example/a"), m.trackers)
    }

    @Test fun convertsBase32Hash() {
        // 20 bytes 0x00..0x13 in base32
        val bytes = ByteArray(20) { it.toByte() }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0; var count = 0; val sb = StringBuilder()
        for (b in bytes) { bits = (bits shl 8) or (b.toInt() and 0xFF); count += 8; while (count >= 5) { count -= 5; sb.append(alphabet[(bits shr count) and 31]) } }
        val m = MagnetLink.parse("magnet:?xt=urn:btih:$sb")!!
        assertEquals(bytes.joinToString("") { "%02x".format(it) }, m.infoHash)
    }

    @Test fun rejectsNonMagnetsAndBadHashes() {
        assertNull(MagnetLink.parse(null))
        assertNull(MagnetLink.parse("https://example.com/a.torrent"))
        assertNull(MagnetLink.parse("magnet:?dn=no+hash"))
        assertNull(MagnetLink.parse("magnet:?xt=urn:btih:abc"))
        assertNull(MagnetLink.parse("magnet:?xt=urn:btih:${"z".repeat(40)}"))
        assertNull(MagnetLink.parse("magnet:?xt=urn:sha1:$hex"))
    }

    @Test fun ignoresJunkTrackersAndLimitsThem() {
        val many = (1..50).joinToString("&") { "tr=udp%3A%2F%2Ft$it.example%3A1" }
        val m = MagnetLink.parse("magnet:?xt=urn:btih:$hex&tr=file%3A%2F%2F%2Fetc%2Fpasswd&tr=javascript%3Aalert(1)&$many")!!
        assertEquals(32, m.trackers.size)
        assert(m.trackers.none { it.startsWith("file") || it.startsWith("javascript") })
    }

    @Test fun keepsPeersAndRoundTrips() {
        val m = MagnetLink.parse("magnet:?xt=urn:btih:$hex&x.pe=127.0.0.1:6881&x.pe=not%20a%20peer")!!
        assertEquals(listOf("127.0.0.1:6881"), m.peers)
        assertEquals(m, MagnetLink.parse(m.toUri()))
    }

    @Test fun trimsWhitespaceAndIsCaseInsensitiveOnScheme() {
        assertEquals(hex, MagnetLink.parse("  MAGNET:?xt=urn:btih:$hex \n")!!.infoHash)
    }

    @Test fun normalizesBareHashes() {
        assertEquals(hex, MagnetLink.normalizeInfoHash(hex.uppercase()))
        assertNull(MagnetLink.normalizeInfoHash("nope"))
    }
}
