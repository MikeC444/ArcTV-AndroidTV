package com.mangotv.app.data.history

import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Season
import com.mangotv.app.data.network.WatchHistoryEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeStatusesTest {
    private fun row(
        season: Int?, episode: Int?, pos: Long = 0, dur: Long = 2_700_000, done: Boolean = false,
        provider: String = "p", id: String = "tt1", type: String = "TV_SHOW"
    ) = WatchHistoryEntryDto(
        providerId = provider, contentId = id, contentType = type, seasonNumber = season, episodeNumber = episode, title = "t",
        positionMs = pos, durationMs = dur, completed = done, watchedAt = "2026-10-11T10:00:00.000Z"
    )

    private fun ep(season: Int, number: Int) = Episode("tt1:$season:$number", season, number, "T$number", "", null, null)

    @Test
    fun `finished episodes are watched and part-way ones have a fraction`() {
        val result = episodeStatuses(listOf(row(1, 1, done = true), row(1, 2, pos = 900_000), row(1, 3, pos = 0)), "p", "tt1")
        assertEquals(EpisodeStatus(true, 1f), result["1:1"])
        assertEquals(false, result["1:2"]?.watched)
        assertEquals(1f / 3f, result["1:2"]!!.fraction, 0.001f)
        assertNull(result["1:3"]) // never started, or marked not watched
    }

    @Test
    fun `other titles, other providers and rows without an episode are ignored`() {
        val result = episodeStatuses(
            listOf(row(1, 1, done = true, id = "tt2"), row(1, 1, done = true, provider = "q"), row(null, null, done = true)), "p", "tt1"
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `the next episode follows the furthest one watched`() {
        val seasons = listOf(Season(1, "S1", listOf(ep(1, 1), ep(1, 2), ep(1, 3))), Season(2, "S2", listOf(ep(2, 1), ep(2, 2))))
        val watched = EpisodeStatus(true, 1f)
        assertNull(nextEpisodeAfterWatched(seasons, emptyMap()))
        assertEquals(2, nextEpisodeAfterWatched(seasons, mapOf("1:1" to watched))?.episodeNumber)
        // A gap is not filled: after E1 and E3 the next is the one after E3.
        assertEquals("tt1:2:1", nextEpisodeAfterWatched(seasons, mapOf("1:1" to watched, "1:3" to watched))?.id)
        // A season caught up moves on to the next season.
        assertEquals(2, nextEpisodeAfterWatched(seasons, mapOf("1:1" to watched, "1:2" to watched, "1:3" to watched))?.seasonNumber)
        // The last episode being the furthest leaves nothing next.
        assertNull(nextEpisodeAfterWatched(seasons, mapOf("2:2" to watched)))
    }

    @Test
    fun `part-way episodes do not move the next episode on`() {
        val seasons = listOf(Season(1, "S1", listOf(ep(1, 1), ep(1, 2))))
        assertNull(nextEpisodeAfterWatched(seasons, mapOf("1:1" to EpisodeStatus(false, 0.5f))))
    }
}
