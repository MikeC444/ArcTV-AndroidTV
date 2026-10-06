package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentSourcesTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test fun ordinaryLinksAreNotTorrents() {
        assertNull(torrentRefOf("https://cdn.example/video.mp4", null))
        assertNull(torrentRefOf("https://cdn.example/stream/master.m3u8", null))
        assertNull(torrentRefOf("https://cdn.example/manifest.mpd?token=abc", null))
        assertNull(torrentRefOf("https://debrid.example/d/abc/Movie.mkv", hash)) // a link wins over an info hash the source also lists
        assertNull(torrentRefOf(null, null))
        assertNull(torrentRefOf("", "not a hash"))
    }

    @Test fun infoHashBecomesAMagnetWithItsTrackers() {
        val ref = torrentRefOf(null, hash.uppercase(), listOf("udp://t.example:80/announce"), "Movie.2024.1080p") as TorrentRef.Magnet
        assertEquals(hash, ref.link.infoHash)
        assertEquals(listOf("udp://t.example:80/announce"), ref.link.trackers)
        assertEquals("Movie.2024.1080p", ref.link.displayName)
    }

    @Test fun magnetUrlsAreParsedOrReportedInvalid() {
        assertTrue(torrentRefOf("magnet:?xt=urn:btih:$hash", null) is TorrentRef.Magnet)
        assertTrue(torrentRefOf("magnet:?xt=urn:btih:zzz", null) is TorrentRef.Invalid)
    }

    @Test fun torrentFileLinks() {
        assertEquals(TorrentRef.TorrentFile("https://x.example/a/b.torrent"), torrentRefOf("https://x.example/a/b.torrent", null))
        assertEquals(TorrentRef.TorrentFile("http://x.example/dl/B.TORRENT?key=1"), torrentRefOf("http://x.example/dl/B.TORRENT?key=1", null))
        assertNull(torrentRefOf("https://x.example/download?file=b.torrent", null)) // only the path counts
    }

    @Test fun localFilesOnlyForSourcesThePersonAdded() {
        assertNull(torrentRefOf("file:///data/user/0/app/files/user-torrents/a.torrent", null))
        assertNull(torrentRefOf("content://other.app/doc/1", null))
        assertEquals(
            TorrentRef.TorrentFile("file:///data/user/0/app/files/user-torrents/a.torrent"),
            torrentRefOf("file:///data/user/0/app/files/user-torrents/a.torrent", null, allowLocalFiles = true)
        )
    }

    @Test fun stremioSourcesYieldTrackersOnly() {
        val t = trackersFromStremioSources(listOf("tracker:udp://a.example:1337/announce", "dht:$hash", "tracker:http://b.example/a", "tracker:ftp://c.example", "udp://d.example:80", "garbage"))
        assertEquals(listOf("udp://a.example:1337/announce", "http://b.example/a", "udp://d.example:80"), t)
        assertEquals(emptyList<String>(), trackersFromStremioSources(null))
    }

    @Test fun userInput() {
        assertTrue(parseUserTorrentInput("  magnet:?xt=urn:btih:$hash&dn=x ") is UserTorrentInput.Magnet)
        assertTrue(parseUserTorrentInput(hash) is UserTorrentInput.Magnet)
        assertEquals(UserTorrentInput.TorrentUrl("https://a.example/x.torrent"), parseUserTorrentInput("https://a.example/x.torrent"))
        listOf(null, "", "   ", "magnet:?xt=urn:btih:nope", "https://a.example/video.mp4", "ftp://a.example/x.torrent", "hello").forEach {
            assertTrue("rejects '$it'", parseUserTorrentInput(it) is UserTorrentInput.Rejected)
        }
    }

    @Test fun localAddressDetection() {
        assertTrue(isLocalTorrentUrl("http://127.0.0.1:41234/abc/file.mkv"))
        assertTrue(!isLocalTorrentUrl("http://example.com/127.0.0.1"))
        assertTrue(!isLocalTorrentUrl(null))
    }
}
