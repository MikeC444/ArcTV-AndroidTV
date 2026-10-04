package com.mangotv.app.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchHistoryTest {
    @Test
    fun `newest first and blanks are ignored`() {
        assertEquals(listOf("star wars", "dune"), withRecentSearch(listOf("dune"), "  star   wars "))
        assertEquals(listOf("dune"), withRecentSearch(listOf("dune"), "   "))
    }

    @Test
    fun `a repeat in any letter case moves to the front instead of doubling`() {
        assertEquals(listOf("dune", "a", "b"), withRecentSearch(listOf("a", "Dune", "b"), "dune"))
    }

    @Test
    fun `only the newest few are kept`() {
        val many = List(RECENT_SEARCH_LIMIT) { "t$it" }
        val next = withRecentSearch(many, "new")
        assertEquals(RECENT_SEARCH_LIMIT, next.size)
        assertEquals("new", next.first())
        assertFalse(next.contains("t${RECENT_SEARCH_LIMIT - 1}"))
    }
}
