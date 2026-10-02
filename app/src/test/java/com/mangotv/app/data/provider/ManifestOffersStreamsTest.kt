package com.mangotv.app.data.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestOffersStreamsTest {

    private fun resources(json: String): List<JsonElement> = Json.parseToJsonElement(json).let { (it as kotlinx.serialization.json.JsonArray).toList() }

    @Test
    fun `a plain stream entry means it provides streams`() {
        assertTrue(manifestOffersStreams(resources("""["catalog","stream"]""")))
    }

    @Test
    fun `a scoped stream entry means it provides streams`() {
        assertTrue(manifestOffersStreams(resources("""[{"name":"stream","types":["movie"],"idPrefixes":["tt"]}]""")))
    }

    @Test
    fun `catalog and meta only does not (Cinemeta)`() {
        assertFalse(manifestOffersStreams(resources("""["catalog","meta",{"name":"addon_catalog"}]""")))
    }

    @Test
    fun `an undeclared list is asked anyway`() {
        assertTrue(manifestOffersStreams(emptyList()))
    }
}
