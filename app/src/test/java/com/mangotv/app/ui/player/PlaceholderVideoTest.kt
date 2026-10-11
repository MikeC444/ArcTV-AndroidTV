package com.mangotv.app.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderVideoTest {
    @Test
    fun `a few seconds for a 47 minute episode is a placeholder`() {
        assertTrue(isPlaceholderVideo(9_000, 47))
        assertTrue(isPlaceholderVideo(90_000, 120))
    }

    @Test
    fun `a real file is not`() {
        assertFalse(isPlaceholderVideo(47 * 60_000L, 47))
        assertFalse(isPlaceholderVideo(40 * 60_000L, 47))
    }

    @Test
    fun `nothing is assumed without a known runtime, or for a genuinely short title`() {
        assertFalse(isPlaceholderVideo(9_000, null))
        assertFalse(isPlaceholderVideo(9_000, 8)) // an 8 minute short is below the 10 minute floor
        assertFalse(isPlaceholderVideo(0, 47)) // duration not known yet
        assertFalse(isPlaceholderVideo(-1, 47))
    }

    @Test
    fun `it must be both under two minutes and under a tenth of the runtime`() {
        assertFalse(isPlaceholderVideo(130_000, 600)) // over two minutes
        assertFalse(isPlaceholderVideo(70_000, 11)) // under two minutes, but over a tenth of 11 minutes (66 s)
        assertTrue(isPlaceholderVideo(60_000, 11))
    }
}
