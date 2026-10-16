package com.mangotv.app.data.provider

import com.mangotv.app.data.addon.StremioAddonClient
import com.mangotv.app.data.addon.StremioMetaPreview
import com.mangotv.app.data.addon.toContent
import com.mangotv.app.data.model.AddonCatalogDef
import com.mangotv.app.data.model.AddonCatalogExtra
import com.mangotv.app.data.model.AddonManifest
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.ui.home.navItemsWithAnime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeCatalogsTest {

    private fun extra(name: String, required: Boolean = false, options: List<String>? = null) = AddonCatalogExtra(name, required, options)
    private fun catalog(id: String, type: String, vararg extras: AddonCatalogExtra) = AddonCatalogDef(type = type, id = id, name = id, extra = extras.toList())
    private fun manifest(vararg catalogs: AddonCatalogDef) = AddonManifest(id = "a", name = "Addon", catalogs = catalogs.toList())

    /** Answers every catalogue request with titles named after the request, and records what was asked. */
    private class FakeClient(val asked: MutableList<String> = mutableListOf()) : StremioAddonClient() {
        override suspend fun fetchCatalog(manifestUrl: String, type: String, catalogId: String, extra: Map<String, String>): List<StremioMetaPreview> {
            asked += "$type/$catalogId/${extra.toSortedMap()}"
            return listOf(StremioMetaPreview(id = "kitsu:$catalogId${extra["genre"] ?: ""}", type = type, name = "$catalogId ${extra["genre"].orEmpty()}".trim()))
        }
    }

    private fun provider(m: AddonManifest, client: FakeClient = FakeClient()) = StremioAddonProvider("https://a/manifest.json", m, client)

    @Test
    fun `an addon has anime only when it has an anime catalogue that asks for nothing but, at most, a genre`() {
        assertTrue(provider(manifest(catalog("trending", "anime"))).hasAnime)
        assertFalse(provider(manifest(catalog("top", "movie"), catalog("s", "series"))).hasAnime)
        assertFalse(provider(manifest(catalog("find", "anime", extra("search", required = true)))).hasAnime)
        assertTrue(provider(manifest(catalog("g", "anime", extra("genre", required = true, options = listOf("Isekai"))))).hasAnime)
    }

    @Test
    fun `an anime catalogue is read the older way too (extraSupported and extraRequired)`() {
        val legacy = AddonCatalogDef(type = "anime", id = "old", extraSupported = listOf("genre", "skip"), extraRequired = listOf("genre"))
        assertEquals(listOf(AddonCatalogExtra("genre", true), AddonCatalogExtra("skip", false)), legacy.allExtras)
        assertTrue(provider(manifest(legacy)).hasAnime)
        val needsSearch = AddonCatalogDef(type = "anime", id = "find", extraSupported = listOf("search"), extraRequired = listOf("search"))
        assertFalse(provider(manifest(needsSearch)).hasAnime)
    }

    @Test
    fun `genres are the ones the anime catalogues list`() {
        val p = provider(
            manifest(
                catalog("trending", "anime", extra("genre", options = listOf("Action", "Isekai"))),
                catalog("genres", "anime", extra("genre", required = true, options = listOf("Isekai", "Romance"))),
                catalog("top", "series", extra("genre", options = listOf("Drama")))
            )
        )
        assertEquals(listOf("Action", "Isekai", "Romance"), p.animeGenres())
    }

    @Test
    fun `the tab reads the ranked catalogues, and the genre ones when a genre is chosen`() = runBlocking {
        val client = FakeClient()
        val p = provider(
            manifest(
                catalog("trending", "anime", extra("genre", options = listOf("Action"))),
                catalog("genres", "anime", extra("genre", required = true, options = listOf("Romance"))),
                catalog("top", "series")
            ),
            client
        )
        val all = p.getAnimeItems()
        assertEquals(listOf("trending"), all.map { it.title })
        assertEquals(listOf("anime/trending/{}"), client.asked)
        client.asked.clear()
        val romance = p.getAnimeItems("Romance")
        assertEquals(listOf("genres Romance"), romance.map { it.title })
        assertEquals(listOf("anime/genres/{genre=Romance}"), client.asked)
        client.asked.clear()
        p.getMoreAnimeItems(2, "Romance")
        assertEquals(listOf("anime/genres/{genre=Romance, skip=200}"), client.asked)
    }

    @Test
    fun `a provider with no anime catalogue answers nothing`() = runBlocking {
        val p = provider(manifest(catalog("top", "movie")))
        assertTrue(p.getAnimeItems().isEmpty())
        assertTrue(p.getMoreAnimeItems(1).isEmpty())
    }

    @Test
    fun `anime titles are shows`() {
        assertEquals(ContentType.TV_SHOW, StremioMetaPreview(id = "kitsu:1", type = "anime", name = "X").toContent("a").type)
        assertEquals(ContentType.TV_SHOW, StremioMetaPreview(id = "tt1", type = "series", name = "X").toContent("a").type)
        assertEquals(ContentType.MOVIE, StremioMetaPreview(id = "tt2", type = "movie", name = "X").toContent("a").type)
    }

    @Test
    fun `the Anime tab sits after TV Shows, only while there is an anime addon`() {
        val tabs = listOf("Home", "Movies", "TV Shows", "Search", "My List", "Settings")
        assertEquals(tabs, navItemsWithAnime(tabs, hasAnime = false))
        assertEquals(listOf("Home", "Movies", "TV Shows", "Anime", "Search", "My List", "Settings"), navItemsWithAnime(tabs, hasAnime = true))
        assertEquals("not added twice", navItemsWithAnime(navItemsWithAnime(tabs, true), true), navItemsWithAnime(tabs, true))
    }
}
