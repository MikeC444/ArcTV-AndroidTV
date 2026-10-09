package com.mangotv.app.data.recommend

import com.mangotv.app.data.feedback.FeedbackEntry
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.SavedListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Settings > Recommendations tab counts, matching the web app's page. */
class RecommendationSummaryTest {

    private fun rated(value: Feedback, type: ContentType = ContentType.MOVIE) =
        FeedbackEntry(value = value.wire, title = "t", at = "2026-01-01T00:00:00.000Z", providerId = "p", contentType = type.name)

    private fun saved(id: String, watched: Boolean) =
        SavedListItem(id = id, type = ContentType.MOVIE, title = id, posterUrl = null, backdropUrl = null, year = null, rating = null, providerId = "p", watched = watched)

    @Test
    fun `counts likes, dislikes, finished and saved with their points`() {
        val s = summarizeRecommendations(
            feedback = mapOf("a" to rated(Feedback.LIKE), "b" to rated(Feedback.LIKE), "c" to rated(Feedback.DISLIKE, ContentType.TV_SHOW)),
            list = listOf(saved("w", true), saved("l", false), saved("l2", false))
        )
        assertEquals(2, s.likes)
        assertEquals(1, s.dislikes)
        assertEquals(1, s.finished)
        assertEquals(2, s.saved)
        assertEquals(10, s.likePoints)
        assertEquals(-5, s.dislikePoints)
        assertEquals(4, s.listPoints) // 1 finished x 2 + 2 saved x 1
        assertEquals(6, s.signals)
    }

    @Test
    fun `a rated title counts once, by its rating, even when it is also in My List`() {
        val s = summarizeRecommendations(mapOf("a" to rated(Feedback.LIKE)), listOf(saved("a", true), saved("b", false)))
        assertEquals(1, s.likes)
        assertEquals(0, s.finished)
        assertEquals(1, s.saved)
    }

    @Test
    fun `picks need at least three signals and one positive one`() {
        assertFalse(summarizeRecommendations(emptyMap(), emptyList()).ready)
        assertFalse(summarizeRecommendations(mapOf("a" to rated(Feedback.LIKE), "b" to rated(Feedback.DISLIKE)), emptyList()).ready)
        assertFalse(summarizeRecommendations(mapOf("a" to rated(Feedback.DISLIKE), "b" to rated(Feedback.DISLIKE), "c" to rated(Feedback.DISLIKE)), emptyList()).ready)
        assertTrue(summarizeRecommendations(mapOf("a" to rated(Feedback.LIKE), "b" to rated(Feedback.DISLIKE), "c" to rated(Feedback.DISLIKE)), emptyList()).ready)
    }

    @Test
    fun `says when more titles are rated than the engine reads`() {
        val many = (1..61).associate { "t$it" to rated(Feedback.LIKE) }
        assertTrue(summarizeRecommendations(many, emptyList()).overLimit)
        assertFalse(summarizeRecommendations(many.entries.take(60).associate { it.key to it.value }, emptyList()).overLimit)
    }

    @Test
    fun `writes points plainly`() {
        assertEquals("+20 points", pointsLabel(20))
        assertEquals("−15 points", pointsLabel(-15))
        assertEquals("0 points", pointsLabel(0))
        assertEquals("+1 point", pointsLabel(1))
    }
}
