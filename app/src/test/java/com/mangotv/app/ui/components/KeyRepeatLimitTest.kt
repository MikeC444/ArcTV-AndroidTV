package com.mangotv.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRepeatLimitTest {

    @Test
    fun `a fresh press always moves, however soon after the last move`() {
        assertTrue(allowVerticalMove(repeatCount = 0, nowMs = 1_001, lastMoveMs = 1_000))
    }

    @Test
    fun `a held key's repeats are held back until the interval has passed`() {
        assertFalse(allowVerticalMove(repeatCount = 3, nowMs = 1_100, lastMoveMs = 1_000, intervalMs = 220))
        assertFalse(allowVerticalMove(repeatCount = 3, nowMs = 1_219, lastMoveMs = 1_000, intervalMs = 220))
        assertTrue(allowVerticalMove(repeatCount = 3, nowMs = 1_220, lastMoveMs = 1_000, intervalMs = 220))
    }

    @Test
    fun `the first repeat after a long gap moves`() {
        assertTrue(allowVerticalMove(repeatCount = 1, nowMs = 10_000, lastMoveMs = 0))
    }
}
