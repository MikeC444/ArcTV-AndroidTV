package com.mangotv.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleTest {
    @Test
    fun `a saved choice is read back, and anything else is the default`() {
        assertEquals(SubtitleSize.LARGE, SubtitleSize.fromWire("large"))
        assertEquals(SubtitleSize.EXTRA_LARGE, SubtitleSize.fromWire("extra_large"))
        assertEquals(SubtitleSize.MEDIUM, SubtitleSize.fromWire(null))
        assertEquals(SubtitleSize.MEDIUM, SubtitleSize.fromWire("huge"))
        assertEquals(SubtitleColor.YELLOW, SubtitleColor.fromWire("yellow"))
        assertEquals(SubtitleColor.WHITE, SubtitleColor.fromWire(null))
        assertEquals(SubtitleColor.WHITE, SubtitleColor.fromWire("pink"))
    }

    @Test
    fun `every choice round-trips through its wire name`() {
        SubtitleSize.entries.forEach { assertEquals(it, SubtitleSize.fromWire(it.wire)) }
        SubtitleColor.entries.forEach { assertEquals(it, SubtitleColor.fromWire(it.wire)) }
    }

    @Test
    fun `bigger sizes mean a smaller VLC number and a larger share of the picture`() {
        val sizes = SubtitleSize.entries
        for (i in 1 until sizes.size) {
            assertTrue(sizes[i].vlcRelFontSize < sizes[i - 1].vlcRelFontSize)
            assertTrue(sizes[i].fraction > sizes[i - 1].fraction)
        }
    }

    @Test
    fun `VLC is given the size and the colour as plain numbers`() {
        assertEquals(listOf("--freetype-rel-fontsize=16", "--freetype-color=16777215"), subtitleVlcOptions(SubtitleSize.MEDIUM, SubtitleColor.WHITE))
        assertEquals(listOf("--freetype-rel-fontsize=9", "--freetype-color=16776960"), subtitleVlcOptions(SubtitleSize.EXTRA_LARGE, SubtitleColor.YELLOW))
        assertEquals("--freetype-color=65535", subtitleVlcOptions(SubtitleSize.SMALL, SubtitleColor.CYAN)[1])
    }

    @Test
    fun `the built-in player's colour is opaque`() {
        assertEquals(0xFFFFFFFF.toInt(), SubtitleColor.WHITE.argb)
        assertEquals(0xFFFFFF00.toInt(), SubtitleColor.YELLOW.argb)
    }
}
