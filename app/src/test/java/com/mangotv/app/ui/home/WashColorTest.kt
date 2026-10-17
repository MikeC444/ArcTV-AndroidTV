package com.mangotv.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WashColorTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun fill(n: Int, r: Int, g: Int, b: Int, a: Int = 255) = IntArray(n) { argb(r, g, b, a) }

    @Test
    fun `a coloured picture gives its colour, lifted to a clear level`() {
        val c = washColor(fill(100, 100, 20, 20))!!
        assertEquals(200f / 255f, c.red, 0.01f)
        assertTrue(c.green < 0.25f)
        assertTrue(c.blue < 0.25f)
    }

    @Test
    fun `black and white parts of the picture do not pull the colour off`() {
        val c = washColor(fill(50, 0, 0, 0) + fill(50, 255, 255, 255) + fill(20, 20, 60, 160))!!
        assertTrue(c.blue > c.red)
        assertEquals(200f / 255f, c.blue, 0.01f)
    }

    @Test
    fun `a grey or dark picture gives nothing`() {
        assertNull(washColor(fill(100, 120, 120, 120)))
        assertNull(washColor(fill(100, 10, 10, 10)))
        assertNull(washColor(IntArray(0)))
    }

    @Test
    fun `see-through pixels are ignored`() {
        assertNull(washColor(fill(10, 200, 30, 30, a = 0)))
        assertNotNull(washColor(fill(10, 200, 30, 30)))
    }

    @Test
    fun `the colour is an opaque colour`() {
        assertEquals(1f, washColor(fill(10, 30, 120, 200))!!.alpha, 0f)
    }
}
