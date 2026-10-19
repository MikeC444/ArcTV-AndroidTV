package com.mangotv.app.data.streaming

import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.network.StreamingTitleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class StreamingTitlesRepositoryTest {

    @Test
    fun `the region is the device's country, upper-cased, else the US`() {
        assertEquals("US", regionOf(Locale("en", "us")))
        assertEquals("BR", regionOf(Locale("pt", "BR")))
        assertEquals("US", regionOf(Locale("en")))
        assertEquals("US", regionOf(Locale.ROOT))
    }

    @Test
    fun `a title from the server opens through the addon the page came from`() {
        val dto = StreamingTitleDto(imdbId = "tt0111161", type = "MOVIE", title = "Alpha", year = 2020, rating = 7.5, overview = "A", poster = "https://p/a.jpg", backdrop = null)
        val content = dto.toContent("pw.ers.netflix-catalog")!!
        assertEquals("tt0111161", content.id)
        assertEquals(ContentType.MOVIE, content.type)
        assertEquals("Alpha", content.title)
        assertEquals("A", content.description)
        assertEquals("https://p/a.jpg", content.posterUrl)
        assertEquals(2020, content.year)
        assertEquals("pw.ers.netflix-catalog", content.providerId)
        assertEquals(ContentType.TV_SHOW, dto.copy(type = "TV_SHOW").toContent("x")!!.type)
    }

    @Test
    fun `a title of a kind the app does not know is left out`() {
        assertNull(StreamingTitleDto(imdbId = "tt1", type = "MUSIC", title = "x").toContent("x"))
    }
}
