package com.mangotv.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleLanguageOptionsTest {
    @Test
    fun `System Default is first, English second, and the rest are A to Z`() {
        assertNull(SubtitleLanguageOptions[0].code)
        assertEquals("en", SubtitleLanguageOptions[1].code)
        val rest = SubtitleLanguageOptions.drop(2).map { it.label }
        assertEquals(rest.sorted(), rest)
        assertEquals("Arabic", rest.first())
    }

    @Test
    fun `no language is listed twice`() {
        assertEquals(SubtitleLanguageOptions.size, SubtitleLanguageOptions.map { it.code }.toSet().size)
    }
}
