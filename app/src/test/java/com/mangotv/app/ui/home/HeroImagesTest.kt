package com.mangotv.app.ui.home

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroImagesTest {

    @Test
    fun `asks Metahub and TMDB for their biggest size`() {
        assertEquals("https://images.metahub.space/background/large/tt0111161/img", sharpBackdrop("https://images.metahub.space/background/medium/tt0111161/img"))
        assertEquals("https://images.metahub.space/background/large/tt0111161/img", sharpBackdrop("https://images.metahub.space/background/small/tt0111161/img"))
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg", sharpBackdrop("https://image.tmdb.org/t/p/w780/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg", sharpBackdrop("https://image.tmdb.org/t/p/w1280/abc.jpg"))
    }

    @Test
    fun `leaves everything else alone`() {
        assertEquals("https://images.metahub.space/background/large/tt1/img", sharpBackdrop("https://images.metahub.space/background/large/tt1/img"))
        assertEquals("https://image.tmdb.org/t/p/original/abc.jpg", sharpBackdrop("https://image.tmdb.org/t/p/original/abc.jpg"))
        assertEquals("https://example.com/poster/medium/x.jpg", sharpBackdrop("https://example.com/poster/medium/x.jpg"))
        assertNull(sharpBackdrop(null))
        assertNull(sharpBackdrop(""))
        assertNull(sharpBackdrop("   "))
    }

    private fun content(id: String, backdrop: String?, logo: String? = null) = Content(
        id = id, type = ContentType.MOVIE, title = id, description = "", posterUrl = null, backdropUrl = backdrop, logoUrl = logo
    )

    @Test
    fun `lists each title's backdrop then its logo, in order, once each`() {
        val list = heroImages(listOf(
            content("a", "https://images.metahub.space/background/medium/tt1/img", "https://images.metahub.space/logo/medium/tt1/img"),
            content("a-again", "https://images.metahub.space/background/medium/tt1/img"),
            content("none", null, null),
            content("b", "https://example.com/b.jpg")
        ))
        assertEquals(
            listOf(
                HeroImage("https://images.metahub.space/background/large/tt1/img", "https://images.metahub.space/background/medium/tt1/img"),
                HeroImage("https://images.metahub.space/logo/medium/tt1/img", "https://images.metahub.space/logo/medium/tt1/img"),
                HeroImage("https://example.com/b.jpg", "https://example.com/b.jpg")
            ),
            list
        )
    }
}
