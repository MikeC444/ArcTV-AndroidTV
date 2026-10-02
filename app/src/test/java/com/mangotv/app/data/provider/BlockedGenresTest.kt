package com.mangotv.app.data.provider

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.model.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedGenresTest {

    private fun title(id: String, vararg genres: String) = Content(
        id = id,
        type = ContentType.MOVIE,
        title = id,
        description = "",
        posterUrl = null,
        backdropUrl = null,
        genres = genres.map { Genre(it, it) }
    )

    @Test
    fun `the set is trimmed and lower-cased and ignores blanks`() {
        assertEquals(setOf("horror", "sci-fi"), blockedGenreSet(listOf("Horror", " sci-fi ", "", "  ")))
    }

    @Test
    fun `a title in a blocked genre is blocked whatever the casing`() {
        val blocked = blockedGenreSet(listOf("horror"))
        assertTrue(title("a", "Drama", "Horror").isBlockedBy(blocked))
        assertFalse(title("b", "Drama").isBlockedBy(blocked))
    }

    @Test
    fun `a title with no genres is never blocked, and nothing is blocked when the set is empty`() {
        assertFalse(title("a").isBlockedBy(blockedGenreSet(listOf("horror"))))
        assertFalse(title("b", "Horror").isBlockedBy(emptySet()))
    }

    @Test
    fun `blocked titles are dropped from a list`() {
        val items = listOf(title("a", "Horror"), title("b", "Comedy"), title("c", "Drama", "Horror"))
        assertEquals(listOf("b"), items.withoutBlocked(blockedGenreSet(listOf("Horror"))).map { it.id })
        assertEquals(items, items.withoutBlocked(emptySet()))
    }

    @Test
    fun `rows lose blocked titles and a row left empty disappears`() {
        val rows = listOf(
            HomeSection(id = "r1", title = "Popular", items = listOf(title("a", "Horror"), title("b", "Comedy"))),
            HomeSection(id = "r2", title = "Scary", items = listOf(title("c", "Horror")))
        )
        val result = rows.withoutBlocked(blockedGenreSet(listOf("horror")))
        assertEquals(listOf("r1"), result.map { it.id })
        assertEquals(listOf("b"), result.single().items.map { it.id })
    }

    @Test
    fun `genre names are filtered by the same rule`() {
        assertEquals(listOf("Comedy"), listOf("Horror", "Comedy").withoutBlockedNames(blockedGenreSet(listOf("HORROR"))))
    }
}
