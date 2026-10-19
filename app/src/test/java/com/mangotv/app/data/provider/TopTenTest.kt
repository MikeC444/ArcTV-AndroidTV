package com.mangotv.app.data.provider

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.streaming.pickUnblocked
import com.mangotv.app.data.streaming.rankedPool
import com.mangotv.app.data.streaming.topTen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TopTenTest {
    private fun title(id: String, type: ContentType = ContentType.MOVIE, vararg genres: String) =
        Content(id = id, type = type, title = id, description = "", posterUrl = null, backdropUrl = null, genres = genres.map { Genre("0", it) })

    private fun movies(vararg ids: String) = ids.map { title(it, ContentType.MOVIE) }
    private fun shows(vararg ids: String) = ids.map { title(it, ContentType.TV_SHOW) }

    @Test
    fun `series and movies are taken in turn, a series first, and ten are kept`() {
        val ten = topTen(movies("m1", "m2", "m3", "m4", "m5", "m6", "m7"), shows("s1", "s2", "s3", "s4", "s5", "s6", "s7"))
        assertEquals(listOf("s1", "m1", "s2", "m2", "s3", "m3", "s4", "m4", "s5", "m5"), ten.map { it.id })
    }

    @Test
    fun `a title in both lists shows once, and a shorter list is carried on by the longer one`() {
        assertEquals(listOf("s1", "m1", "m2", "m3"), rankedPool(movies("m1", "m2", "m3"), shows("s1", "m1"), 10).map { it.id })
        assertEquals(emptyList<String>(), rankedPool(emptyList(), emptyList(), 10).map { it.id })
    }

    @Test
    fun `titles with a blocked genre are left out and the next ones take their place`() = runBlocking {
        val pool = (1..12).map { title("t$it") }
        val genres = mapOf("t2" to listOf(Genre("0", "Horror")), "t5" to listOf(Genre("0", "Horror")))
        val ten = pickUnblocked(pool, setOf("horror"), { genres[it.id] ?: listOf(Genre("0", "Drama")) }, 10)
        assertEquals(listOf("t1", "t3", "t4", "t6", "t7", "t8", "t9", "t10", "t11", "t12"), ten.map { it.id })
    }

    @Test
    fun `a title whose genres cannot be found is kept`() = runBlocking {
        val pool = listOf(title("a"), title("b"), title("c"))
        val ten = pickUnblocked(pool, setOf("horror"), { if (it.id == "b") throw IllegalStateException("no details") else listOf(Genre("0", "Horror")) }, 3)
        assertEquals(listOf("b"), ten.map { it.id })
    }
}
