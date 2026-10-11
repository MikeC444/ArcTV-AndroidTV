package com.mangotv.app.ui.player

import com.mangotv.app.data.network.PlaybackOutcomeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackHealthTest {
    private var now = 1_000L
    private val reports = mutableListOf<PlaybackOutcomeDto>()
    private fun tracker(tried: Int = 1) = PlaybackHealthTracker("vlc", "AFTMM", "4K", "HEVC", "Dolby Vision", tried, { reports += it }, { now })

    @Test
    fun `playing for a while is ok and records how long the picture took`() {
        val t = tracker()
        now += 2_500; t.onPlaying()
        now += 90_000; t.finish()
        val r = reports.single()
        assertEquals("ok", r.outcome)
        assertEquals(2_500L, r.startMs)
        assertEquals(90_000L, r.watchedMs)
    }

    @Test
    fun `leaving soon after the picture appears is short`() {
        val t = tracker()
        now += 1_000; t.onPlaying()
        now += 8_000; t.finish()
        assertEquals("short", reports.single().outcome)
        assertEquals(8_000L, reports.single().watchedMs)
    }

    @Test
    fun `leaving before any picture, with no error, never started`() {
        val t = tracker()
        now += 12_000; t.finish()
        val r = reports.single()
        assertEquals("never_started", r.outcome)
        assertNull(r.startMs)
        assertEquals(0L, r.watchedMs)
    }

    @Test
    fun `a player error before a real watch is an error, and it keeps the reason`() {
        val t = tracker()
        now += 500; t.onPlaying()
        now += 3_000; t.onError("decoder failed")
        now += 5_000; t.finish()
        val r = reports.single()
        assertEquals("error", r.outcome)
        assertEquals("decoder failed", r.errorKind)
        assertEquals(3_000L, r.watchedMs)
    }

    @Test
    fun `an error after a long watch does not make it a failed viewing`() {
        val t = tracker()
        t.onPlaying()
        now += 120_000; t.onError("network")
        t.finish()
        assertEquals("ok", reports.single().outcome)
    }

    @Test
    fun `pausing stops the clock and the natural end counts as ok`() {
        val t = tracker()
        t.onPlaying()
        now += 10_000; t.onPaused()
        now += 600_000 // paused: not watching
        t.onPlaying()
        now += 5_000; t.onEnded()
        t.finish()
        val r = reports.single()
        assertEquals("ok", r.outcome)
        assertEquals(15_000L, r.watchedMs)
    }

    @Test
    fun `finishing twice reports once`() {
        val t = tracker()
        t.onPlaying(); now += 40_000
        t.finish(); t.finish()
        assertEquals(1, reports.size)
    }

    @Test
    fun `the number of sources tried is passed on`() {
        tracker(tried = 3).finish()
        assertEquals(3, reports.single().sourcesTried)
    }

    @Test
    fun `the HDR label is read from the release name`() {
        assertEquals("Dolby Vision", hdrLabelOf("Movie.2160p.UHD.BluRay.DV.HDR10.HEVC"))
        assertEquals("Dolby Vision", hdrLabelOf("Movie 2160p Dolby Vision"))
        assertEquals("HDR", hdrLabelOf("Movie.2160p.WEB-DL.HDR10+.x265"))
        assertNull(hdrLabelOf("Movie.1080p.BluRay.x264"))
    }

    @Test
    fun `sources tried counts up for the same title and starts again for another or after a quiet spell`() {
        assertEquals(1, SourceAttempts.next("a", 0))
        assertEquals(2, SourceAttempts.next("a", 60_000))
        assertEquals(3, SourceAttempts.next("a", 90_000))
        assertEquals(1, SourceAttempts.next("b", 100_000))
        assertEquals(1, SourceAttempts.next("b", 100_000 + 11 * 60_000))
    }
}
