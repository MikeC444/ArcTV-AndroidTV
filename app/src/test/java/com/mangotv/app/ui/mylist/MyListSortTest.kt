package com.mangotv.app.ui.mylist

import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.SavedListItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MyListSortTest {

    private fun item(id: String, title: String = id, year: Int? = null, rating: Double? = null) = SavedListItem(
        id = id, type = ContentType.MOVIE, title = title, posterUrl = null, backdropUrl = null,
        year = year, rating = rating, providerId = "p"
    )

    private fun ids(items: List<SavedListItem>) = items.map { it.id }

    // The repository keeps oldest-added first, so "c" was added last.
    private val stored = listOf(
        item("a", title = "banana", year = 2001, rating = 7.0),
        item("b", title = "Apple", year = 2020, rating = 7.0),
        item("c", title = "cherry", year = null, rating = null)
    )

    @Test
    fun `recently added is newest first`() {
        assertEquals(listOf("c", "b", "a"), ids(sortSavedItems(stored, MyListSort.RECENT)))
    }

    @Test
    fun `a to z ignores case`() {
        assertEquals(listOf("b", "a", "c"), ids(sortSavedItems(stored, MyListSort.TITLE)))
    }

    @Test
    fun `highest rated puts unrated last and keeps recently added order among ties`() {
        assertEquals(listOf("b", "a", "c"), ids(sortSavedItems(stored, MyListSort.HIGHEST_RATED)))
    }

    @Test
    fun `newest puts the latest year first and a missing year last`() {
        assertEquals(listOf("b", "a", "c"), ids(sortSavedItems(stored, MyListSort.NEWEST)))
    }

    @Test
    fun `sorting never changes the stored list`() {
        val before = ids(stored)
        MyListSort.entries.forEach { sortSavedItems(stored, it) }
        assertEquals(before, ids(stored))
    }
}
