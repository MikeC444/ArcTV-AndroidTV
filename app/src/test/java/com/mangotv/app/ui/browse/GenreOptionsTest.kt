package com.mangotv.app.ui.browse

import com.mangotv.app.data.model.AddonCatalogDef
import com.mangotv.app.data.model.AddonCatalogExtra
import com.mangotv.app.data.model.AddonManifest
import com.mangotv.app.data.model.ContentType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GenreOptionsTest {

    // Gradle runs unit tests from the module directory, so the bundled manifest is right there.
    private val bundled: AddonManifest by lazy {
        val raw = File("src/main/assets/cinemeta_manifest.json").readText()
        Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }.decodeFromString(AddonManifest.serializer(), raw)
    }

    @Test
    fun `movies get Cinemeta's own genres, no years`() {
        val movies = genreOptionsFor(bundled, ContentType.MOVIE)
        assertEquals(19, movies.size)
        assertEquals(listOf("Action", "Adventure", "Animation"), movies.take(3))
        assertTrue(movies.containsAll(listOf("Biography", "Sci-Fi", "Sport", "Western")))
        assertFalse(movies.any { genre -> genre.all { it.isDigit() } })
    }

    @Test
    fun `tv shows get the same genres plus three more`() {
        val movies = genreOptionsFor(bundled, ContentType.MOVIE)
        val shows = genreOptionsFor(bundled, ContentType.TV_SHOW)
        assertEquals(movies + listOf("Reality-TV", "Talk-Show", "Game-Show"), shows)
    }

    @Test
    fun `years are dropped and a manifest without a top catalogue gives nothing`() {
        fun manifest(catalogs: List<AddonCatalogDef>) = AddonManifest(id = "x", name = "X", version = "1", catalogs = catalogs)
        val withYears = manifest(listOf(
            AddonCatalogDef("movie", "top", "Popular", listOf(AddonCatalogExtra("genre", options = listOf("Action", "2024", "Drama"))))
        ))
        assertEquals(listOf("Action", "Drama"), genreOptionsFor(withYears, ContentType.MOVIE))
        assertEquals(emptyList<String>(), genreOptionsFor(withYears, ContentType.TV_SHOW))
        assertEquals(emptyList<String>(), genreOptionsFor(manifest(emptyList()), ContentType.MOVIE))
    }

    @Test
    fun `the empty message names the genre when one is chosen`() {
        assertEquals("Nothing to show here right now.", emptyBrowseMessage("Movies", null))
        assertEquals("No Sci-Fi tv shows found right now.", emptyBrowseMessage("TV Shows", "Sci-Fi"))
    }
}
