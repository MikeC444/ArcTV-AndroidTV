package com.mangotv.app.ui.player

import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLogicTest {
    private fun ep(n: Int) = Episode(id = "e$n", seasonNumber = 0, episodeNumber = n, title = "Episode $n", description = "", thumbnailUrl = null, runtimeMinutes = null)
    private val seasons = listOf(
        Season(1, "Season 1", listOf(ep(1), ep(2))),
        Season(2, "Season 2", listOf(ep(1)))
    )

    @Test
    fun `the next episode is the one after in the same season, then the first of the next season`() {
        assertEquals(NextEpisode(1, 2, "Episode 2"), nextEpisodeAfter(seasons, 1, 1))
        assertEquals(NextEpisode(2, 1, "Episode 1"), nextEpisodeAfter(seasons, 1, 2))
    }

    @Test
    fun `there is no next episode after the last one or for a movie or an unknown episode`() {
        assertNull(nextEpisodeAfter(seasons, 2, 1))
        assertNull(nextEpisodeAfter(seasons, null, null))
        assertNull(nextEpisodeAfter(seasons, 1, 9))
        assertNull(nextEpisodeAfter(seasons, 9, 1))
    }

    @Test
    fun `next episode is offered for the last minute only, and never without a next episode`() {
        assertFalse(offerNextEpisode(positionMs = 100_000, durationMs = 2_400_000, hasNext = true))
        assertTrue(offerNextEpisode(positionMs = 2_340_000, durationMs = 2_400_000, hasNext = true))
        assertTrue(offerNextEpisode(positionMs = 2_400_000, durationMs = 2_400_000, hasNext = true))
        assertFalse(offerNextEpisode(positionMs = 2_390_000, durationMs = 2_400_000, hasNext = false))
        assertFalse(offerNextEpisode(positionMs = 0, durationMs = 2_400_000, hasNext = true))
    }

    @Test
    fun `a saved position is offered unless it is almost the end`() {
        assertTrue(shouldOfferResume(1_930_000, 5_400_000))
        assertFalse(shouldOfferResume(5_395_000, 5_400_000))
        assertFalse(shouldOfferResume(null, 5_400_000))
        assertFalse(shouldOfferResume(0, 5_400_000))
        assertFalse(shouldOfferResume(60_000, 0))
    }

    @Test
    fun `the right hand time shows what is left or the total`() {
        assertEquals("−58:20", formatRightTime(positionMs = 1_930_000, durationMs = 5_430_000, showRemaining = true))
        assertEquals("1:30:30", formatRightTime(positionMs = 1_930_000, durationMs = 5_430_000, showRemaining = false))
    }

    private fun src(channels: Int? = null, atmos: Boolean = false, tag: String? = null) = com.mangotv.app.data.model.Stream(
        id = "s", providerId = "p", providerLabel = "P", resolutionTier = com.mangotv.app.data.model.ResolutionTier.FHD_1080P,
        qualityBadge = "1080p", releaseTitle = "r", audioChannels = channels, audioAtmos = atmos, audioTag = tag
    )

    @Test
    fun `surround sources open in the built-in player unless the title or the stereo setting says otherwise`() {
        val vlc = PreferredPlayer.VLC
        val auto = AudioChannelMode.AUTO
        // plain stereo or unknown audio: VLC, as before
        assertTrue(startsInVlc(vlc, null, src(), auto, hasLink = true, vlcUsable = true))
        assertTrue(startsInVlc(vlc, null, src(channels = 2, tag = "AAC"), auto, true, true))
        // surround by channel count, Atmos, or a surround format: the built-in player
        assertFalse(startsInVlc(vlc, null, src(channels = 6), auto, true, true))
        assertFalse(startsInVlc(vlc, null, src(atmos = true), auto, true, true))
        assertFalse(startsInVlc(vlc, null, src(tag = "DTS-HD.MA"), auto, true, true))
        assertFalse(startsInVlc(vlc, null, src(tag = "TrueHD"), auto, true, true))
        assertFalse(startsInVlc(vlc, null, src(tag = "DDP5.1"), auto, true, true))
        // a pick made for this title wins, either way
        assertTrue(startsInVlc(vlc, PreferredPlayer.VLC, src(channels = 6), auto, true, true))
        assertFalse(startsInVlc(vlc, PreferredPlayer.BUILT_IN, src(), auto, true, true))
        // Speakers set to Stereo: VLC's stereo output is fine
        assertTrue(startsInVlc(vlc, null, src(channels = 6), AudioChannelMode.STEREO, true, true))
        // the built-in default stays built-in; no link or no VLC support: never VLC
        assertFalse(startsInVlc(PreferredPlayer.BUILT_IN, null, src(), auto, true, true))
        assertFalse(startsInVlc(vlc, null, src(), auto, hasLink = false, vlcUsable = true))
        assertFalse(startsInVlc(vlc, null, src(), auto, hasLink = true, vlcUsable = false))
    }
}
