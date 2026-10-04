package com.mangotv.app.ui.search

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchMergeTest {

    private fun item(id: String, type: ContentType = ContentType.MOVIE) = Content(
        id = id, type = type, title = id, description = "", posterUrl = null, backdropUrl = null
    )

    @Test
    fun `an addon that has not answered yet is skipped, and the others still show`() {
        val (movies, shows) = mergeSearchAnswers(listOf(null, listOf(item("fast"))))
        assertEquals(listOf("fast"), movies.map { it.id })
        assertEquals(emptyList<String>(), shows.map { it.id })
    }

    @Test
    fun `addon order is stable, repeats are dropped and movies are split from shows`() {
        val (movies, shows) = mergeSearchAnswers(
            listOf(
                listOf(item("a"), item("s", ContentType.TV_SHOW)),
                listOf(item("a"), item("b"))
            )
        )
        assertEquals(listOf("a", "b"), movies.map { it.id })
        assertEquals(listOf("s"), shows.map { it.id })
    }

    @Test
    fun `nothing answered gives nothing`() {
        val (movies, shows) = mergeSearchAnswers(listOf(null, null))
        assertEquals(0, movies.size + shows.size)
    }
}
