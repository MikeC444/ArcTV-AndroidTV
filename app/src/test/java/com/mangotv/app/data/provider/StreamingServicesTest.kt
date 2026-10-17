package com.mangotv.app.data.provider

import com.mangotv.app.data.addon.StremioAddonClient
import com.mangotv.app.data.addon.StremioMetaPreview
import com.mangotv.app.data.model.AddonCatalogDef
import com.mangotv.app.data.model.AddonCatalogExtra
import com.mangotv.app.data.model.AddonManifest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingServicesTest {

    private fun catalog(id: String, type: String = "movie", name: String? = id, vararg extras: AddonCatalogExtra) = AddonCatalogDef(type = type, id = id, name = name, extra = extras.toList())

    private class FakeClient : StremioAddonClient() {
        val asked = mutableListOf<String>()
        override suspend fun fetchCatalog(manifestUrl: String, type: String, catalogId: String, extra: Map<String, String>): List<StremioMetaPreview> {
            asked += "$type/$catalogId"
            return listOf(StremioMetaPreview(id = "$type-$catalogId", type = type, name = catalogId))
        }
    }

    private fun rows(client: FakeClient, vararg catalogs: AddonCatalogDef) =
        runBlocking { StremioAddonProvider("https://a/manifest.json", AddonManifest(id = "a", name = "Addon", catalogs = catalogs.toList()), client).getHomeSections().toList().flatten() }

    @Test
    fun `a catalogue is matched to its service by name, then id, and the longest word wins`() {
        assertEquals("netflix", serviceOfCatalog("Netflix", "nfx"))
        assertEquals("disney", serviceOfCatalog(null, "dnp"))
        assertEquals("netflixkids", serviceOfCatalog("Netflix Kids", "nfk"))
        assertEquals("skyshowtime", serviceOfCatalog("Sky Showtime", "sst"))
        assertNull(serviceOfCatalog("Popular", "top"))
    }

    @Test
    fun `an addon made of three or more services gives no rows and asks nothing`() {
        val client = FakeClient()
        assertEquals(emptyList<Any>(), rows(client, catalog("nfx", name = "Netflix"), catalog("dnp", name = "Disney+"), catalog("hlu", name = "Hulu")))
        assertEquals(emptyList<String>(), client.asked)
    }

    @Test
    fun `one catalogue that merely mentions a service keeps its row`() {
        val client = FakeClient()
        assertEquals(1, rows(client, catalog("top", name = "Popular"), catalog("nfx", name = "Netflix Originals")).size)
        assertEquals(2, client.asked.size)
    }

    @Test
    fun `streamingServiceCatalogs is empty for fewer than three services`() {
        assertEquals(0, streamingServiceCatalogs(listOf(catalog("nfx", name = "Netflix"), catalog("dnp", name = "Disney+"))).size)
        assertEquals(3, streamingServiceCatalogs(listOf(catalog("nfx", name = "Netflix"), catalog("dnp", name = "Disney+"), catalog("hlu", name = "Hulu"))).size)
    }

    // ── the rules Home and Home Rows follow (the web app's `streamingServices.ts`) ──

    @Test
    fun `the default tiles are the ten best known, in order, and every other service is off until turned on`() {
        val on = STREAMING_SERVICES.filter { !it.off }.map { it.id }
        assertEquals(listOf("netflix", "prime", "apple", "max", "disney", "paramount", "hulu", "peacock", "iplayer", "shudder"), on)
        assertEquals(36, STREAMING_SERVICES.size)
        assertEquals("Prime Video", streamingServiceById("prime")?.name)
        assertNull(streamingServiceById("nope"))
    }

    @Test
    fun `a default-on service shows until hidden, and a default-off one only once turned on`() {
        assertEquals(true, isServiceVisible("netflix", emptySet()))
        assertEquals(false, isServiceVisible("netflix", setOf("streaming:netflix")))
        assertEquals(false, isServiceVisible("crunchyroll", emptySet()))
        assertEquals(true, isServiceVisible("crunchyroll", setOf("streaming-on:crunchyroll")))
        assertEquals(false, isServiceVisible("crunchyroll", setOf("streaming-on:crunchyroll", "streaming:crunchyroll")))
    }

    @Test
    fun `turning a service on or off writes the ids the web app writes, and keeps the others`() {
        assertEquals(setOf("x", "streaming:netflix"), withServiceVisible(setOf("x"), "netflix", visible = false))
        assertEquals(setOf("x"), withServiceVisible(setOf("x", "streaming:netflix"), "netflix", visible = true))
        assertEquals(setOf("streaming-on:hayu"), withServiceVisible(emptySet(), "hayu", visible = true))
        assertEquals(setOf("streaming:hayu"), withServiceVisible(setOf("streaming-on:hayu"), "hayu", visible = false))
    }

    @Test
    fun `services follow the persons order, then the default order, so a service an addon brings later joins at the end`() {
        val services = listOf("netflix", "prime", "disney", "hulu")
        assertEquals(services, inUserOrder(services, emptyList()))
        assertEquals(listOf("hulu", "netflix", "prime", "disney"), inUserOrder(services, listOf("Popular_base", "streaming:hulu", "streaming:netflix", "streaming:gone")))
        assertEquals(listOf("disney", "netflix", "prime", "hulu"), inUserOrder(services, listOf("streaming:disney")))
    }

    @Test
    fun `visible services leave out the hidden ones and the unswitched default-off ones`() {
        assertEquals(listOf("prime"), visibleServices(listOf("netflix", "prime", "crunchyroll"), setOf("streaming:netflix")))
    }

    @Test
    fun `orderServices keeps the default order, each service once, and drops unknown ids`() {
        assertEquals(listOf("netflix", "disney", "hulu"), orderServices(listOf("hulu", "nope", "disney", "netflix", "hulu")))
    }

    @Test
    fun `moveService moves one place, and not past either end`() {
        val current = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moveService(current, "b", -1))
        assertEquals(listOf("a", "c", "b"), moveService(current, "b", 1))
        assertNull(moveService(current, "a", -1))
        assertNull(moveService(current, "c", 1))
        assertNull(moveService(current, "z", 1))
    }

    @Test
    fun `the services part of the order list is replaced and the rows' entries are kept`() {
        assertEquals(listOf("Popular_base", "streaming:b", "streaming:a"), withServiceOrder(listOf("Popular_base", "streaming:a", "streaming:b"), listOf("b", "a")))
    }

    @Test
    fun `reordering rows keeps the streaming entries that Settings does not show among them`() {
        assertEquals(listOf("r2", "r1", "streaming:b", "streaming:a"), orderKeepingServices(listOf("r2", "r1"), listOf("r1", "streaming:b", "r2", "streaming:a")))
    }

    // ── what an addon offers ──

    @Test
    fun `an addon offers the services it has catalogues for, and reads one service's movies and series together`() = runBlocking {
        val client = FakeClient()
        val provider = StremioAddonProvider(
            "https://a/manifest.json",
            AddonManifest(id = "a", name = "Addon", catalogs = listOf(catalog("nfx", "movie", "Netflix"), catalog("nfx", "series", "Netflix"), catalog("dnp", "movie", "Disney+"), catalog("hlu", "movie", "Hulu"))),
            client
        )
        assertEquals(listOf("netflix", "disney", "hulu"), provider.streamingServices())
        val items = provider.getServiceItems("netflix")
        assertEquals(listOf("nfx", "nfx"), items.map { it.title })
        assertEquals(listOf("movie/nfx", "series/nfx"), client.asked.sorted())
        assertEquals(emptyList<Any>(), provider.getServiceItems("peacock"))
    }

    @Test
    fun `more pages come only from a catalogue that declares skip`() = runBlocking {
        val client = FakeClient()
        val paged = catalog("nfx", "movie", "Netflix", AddonCatalogExtra("skip"))
        val provider = StremioAddonProvider("https://a/manifest.json", AddonManifest(id = "a", name = "Addon", catalogs = listOf(paged, catalog("dnp", "movie", "Disney+"), catalog("hlu", "movie", "Hulu"))), client)
        assertEquals(emptyList<Any>(), provider.getMoreServiceItems(1, "disney"))
        assertEquals(emptyList<String>(), client.asked)
        assertEquals(1, provider.getMoreServiceItems(1, "netflix").size)
        assertEquals(listOf("movie/nfx"), client.asked)
    }
}
