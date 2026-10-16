package com.mangotv.app.ui.search

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeSearchMergeTest {
    private fun show(id: String) = Content(id = id, type = ContentType.TV_SHOW, title = id, description = "", posterUrl = null, backdropUrl = null)

    @Test
    fun `anime answers are merged in addon order with no repeats, whatever has answered so far`() {
        assertEquals(emptyList<Content>(), mergeAnimeAnswers(listOf(null, null)))
        assertEquals(listOf("a1", "b1", "a2"), mergeAnimeAnswers(listOf(listOf(show("a1"), show("a2")), listOf(show("b1"), show("a1")))).map { it.id })
        assertEquals(listOf("b1"), mergeAnimeAnswers(listOf(null, listOf(show("b1")))).map { it.id })
    }
}
