package com.mangotv.app.ui.home

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.model.RowStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomeRowLogicTest {

    private fun item(id: String) = Content(
        id = id, type = ContentType.MOVIE, title = id, description = "", posterUrl = null, backdropUrl = null
    )

    private fun row(id: String, vararg ids: String, style: RowStyle = RowStyle.STANDARD) =
        HomeSection(id = id, title = id, items = ids.map(::item), style = style)

    private fun ids(rows: List<HomeSection>) = rows.map { r -> r.items.map { it.id } }

    @Test
    fun `a title stays in the first row that has it and is removed from later rows`() {
        val out = dedupeSections(listOf(row("popular", "a", "b", "c"), row("action", "b", "d"), row("comedy", "a", "c", "e")))
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d"), listOf("e")), ids(out))
    }

    @Test
    fun `a row left empty is dropped and untouched rows are returned as they were`() {
        val input = listOf(row("popular", "a", "b"), row("again", "b", "a"), row("other", "z"))
        val out = dedupeSections(input)
        assertEquals(listOf("popular", "other"), out.map { it.id })
        assertSame(input[0], out[0])
        assertEquals(2, input[1].items.size)
    }

    @Test
    fun `the first row shown wins`() {
        val rows = listOf(row("action", "x", "y"), row("popular", "x", "z"))
        assertEquals(listOf(listOf("x", "y"), listOf("z")), ids(dedupeSections(rows)))
        assertEquals(listOf(listOf("x", "z"), listOf("y")), ids(dedupeSections(rows.reversed())))
    }

    @Test
    fun `focus returns to the same poster by id even when rows moved`() {
        val rows = listOf(row("new", "x"), row("popular", "a", "b", "c"))
        assertEquals(FocusRestoreTarget(rowIndex = 1, itemIndex = 2), findFocusRestoreTarget(rows, "popular", "c"))
    }

    @Test
    fun `no restore when nothing was remembered or the title or row is gone`() {
        val rows = listOf(row("popular", "a", "b"))
        assertNull(findFocusRestoreTarget(rows, null, null))
        assertNull(findFocusRestoreTarget(rows, "popular", null))
        assertNull(findFocusRestoreTarget(rows, "popular", "zzz"))
        assertNull(findFocusRestoreTarget(rows, "gone", "a"))
    }
}
