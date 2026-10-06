package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentFormatTest {
    @Test fun sizes() {
        assertEquals("0 KB", formatBytes(0))
        assertEquals("850 KB", formatBytes(850_000))
        assertEquals("12.4 MB", formatBytes(12_400_000))
        assertEquals("1.3 GB", formatBytes(1_300_000_000))
        assertEquals("1.2 MB/s", formatRate(1_200_000))
    }

    @Test fun progressLines() {
        assertEquals("Starting torrent engine…", describeTorrentProgress(TorrentStreamState.Starting))
        assertEquals("Looking for peers…", describeTorrentProgress(TorrentStreamState.FetchingMetadata(0, 1000)))
        assertEquals("Getting torrent details from 1 peer…", describeTorrentProgress(TorrentStreamState.FetchingMetadata(1, 1000)))
        assertEquals(
            "Buffering 50% · 3 peers · 1.0 MB/s",
            describeTorrentProgress(TorrentStreamState.Buffering(4_000_000, 8_000_000, "a.mkv", TorrentStats(peers = 3, downloadBytesPerSecond = 1_000_000)))
        )
        assertEquals(
            "Buffering 0% · 0 peers",
            describeTorrentProgress(TorrentStreamState.Buffering(0, 8_000_000, "a.mkv", TorrentStats()))
        )
        assertNull(describeTorrentProgress(TorrentStreamState.Closed))
    }

    @Test fun stallNoteOnlyWhileWaiting() {
        assertNull(describeTorrentStall(TorrentStats(peers = 5)))
        assertEquals("Waiting for the torrent · 2 peers · 500 KB/s", describeTorrentStall(TorrentStats(peers = 2, downloadBytesPerSecond = 500_000, stalled = true)))
    }
}
