package com.mangotv.app.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Test

/** How long a title removed from Picked for you stays out: 5 days, then the algorithm decides again. */
class PickedRemovalTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000_000_000_000L

    @Test
    fun `a removal hides the title for 5 days, not a moment longer`() {
        val entry = PickedEntry(removedAt = mapOf("tt1" to now - 5 * day + 60_000))
        assertEquals(setOf("tt1"), activeRemovalsOf(entry, now).keys)
        assertEquals(emptySet<String>(), activeRemovalsOf(entry, now + 2 * 60_000).keys)
    }

    @Test
    fun `keeps each removal's own time`() {
        val entry = PickedEntry(removedAt = mapOf("a" to now - day, "b" to now - 6 * day))
        assertEquals(mapOf("a" to now - day), activeRemovalsOf(entry, now))
    }

    @Test
    fun `an older saved list of ids is kept, with its 5 days starting now`() {
        val entry = PickedEntry(dismissed = listOf("old1", "old2"))
        assertEquals(mapOf("old1" to now, "old2" to now), activeRemovalsOf(entry, now))
    }
}
