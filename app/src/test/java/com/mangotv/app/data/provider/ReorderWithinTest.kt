package com.mangotv.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderWithinTest {

    @Test
    fun `a row moves among its page's rows and the other addons' rows keep their places`() {
        // two addons' rows interleaved in Home's order; the page shows a1 a2 a3
        val global = listOf("a1", "b1", "a2", "b2", "a3")
        assertEquals(listOf("a1", "b1", "a3", "b2", "a2"), reorderWithin(global, listOf("a1", "a2", "a3"), "a3", -1))
        assertEquals(listOf("a2", "b1", "a1", "b2", "a3"), reorderWithin(global, listOf("a1", "a2", "a3"), "a1", 1))
    }

    @Test
    fun `a hidden row of the same addon, which the page's list leaves out, keeps its place`() {
        val global = listOf("a1", "hidden", "a2", "a3")
        assertEquals(listOf("a2", "hidden", "a1", "a3"), reorderWithin(global, listOf("a1", "a2", "a3"), "a2", -1))
    }

    @Test
    fun `a move of two places (a row down in the two-across grid) works too`() {
        assertEquals(listOf("b", "c", "a", "d"), reorderWithin(listOf("a", "b", "c", "d"), listOf("a", "b", "c", "d"), "a", 2))
    }

    @Test
    fun `it cannot move past either end, or an id the page does not show`() {
        val ids = listOf("a", "b", "c")
        assertNull(reorderWithin(ids, ids, "a", -1))
        assertNull(reorderWithin(ids, ids, "c", 1))
        assertNull(reorderWithin(ids, ids, "z", 1))
    }

    @Test
    fun `a move that would go past the end stops at the end`() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), reorderWithin(ids, ids, "a", 5))
    }
}
