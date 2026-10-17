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
            return listOf(StremioMetaPreview(id = "tt$catalogId", type = type, name = catalogId))
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
}
