package com.mangotv.app.data.provider

import com.mangotv.app.data.addon.StremioAddonClient
import com.mangotv.app.data.addon.StremioMeta
import com.mangotv.app.data.addon.StremioStream
import com.mangotv.app.data.addon.StremioVideo
import com.mangotv.app.data.model.AddonManifest
import com.mangotv.app.data.model.ContentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AnimePlaybackIdsTest {

    @Before
    fun reset() = clearEpisodeIds()

    private fun resources(json: String): List<JsonElement> = (Json.parseToJsonElement(json) as JsonArray).toList()

    private class FakeClient(val streamsAsked: MutableList<Pair<String, String>> = mutableListOf(), val metaAsked: MutableList<Pair<String, String>> = mutableListOf()) : StremioAddonClient() {
        override suspend fun fetchStreams(manifestUrl: String, type: String, id: String): List<StremioStream> {
            streamsAsked += type to id
            return emptyList()
        }

        override suspend fun fetchMeta(manifestUrl: String, type: String, id: String): StremioMeta? {
            metaAsked += type to id
            return StremioMeta(
                id = id, type = type, name = "Show",
                videos = listOf(StremioVideo(id = "$id:1", season = 1, episode = 1), StremioVideo(id = "$id:2", season = 1, episode = 2))
            )
        }
    }

    private fun provider(manifest: AddonManifest, client: FakeClient) = StremioAddonProvider("https://a/manifest.json", manifest, client)

    @Test
    fun `IMDb titles are asked for an episode as id season episode`() {
        assertEquals("tt0944947:2:3", streamRequestId("tt0944947", 2, 3))
        assertEquals("tt0944947", streamRequestId("tt0944947", null, null))
    }

    @Test
    fun `anime id types put only the episode number after the id`() {
        assertEquals("kitsu:7442:3", streamRequestId("kitsu:7442", 1, 3))
        assertEquals("mal:20:5", streamRequestId("mal:20", 2, 5))
        assertEquals("anilist:1:9", streamRequestId("anilist:1", 1, 9))
        assertEquals("anidb:3:1", streamRequestId("anidb:3", 1, 1))
    }

    @Test
    fun `other ids keep id season episode`() {
        assertEquals("tmdb:5:1:3", streamRequestId("tmdb:5", 1, 3))
    }

    @Test
    fun `an episode's own video id from the title's details wins`() = runBlocking {
        val client = FakeClient()
        val p = provider(AddonManifest(id = "a", name = "Addon", types = listOf("series")), client)
        p.getDetails(ContentType.TV_SHOW, "kitsu:99")
        assertEquals("kitsu:99:2", streamRequestId("kitsu:99", 1, 2))
        assertEquals("a title whose details were not read falls back to the rule", "kitsu:50:2", streamRequestId("kitsu:50", 1, 2))
    }

    @Test
    fun `a show is asked of an addon that declares only anime as anime`() = runBlocking {
        val client = FakeClient()
        val p = provider(
            AddonManifest(id = "a", name = "Addon", types = listOf("anime"), resources = resources("""[{"name":"meta","types":["anime"]},{"name":"stream","types":["anime"]}]""")),
            client
        )
        p.getDetails(ContentType.TV_SHOW, "kitsu:1")
        p.getStreamReport(ContentType.TV_SHOW, "kitsu:1", 1, 2)
        assertEquals(listOf("anime" to "kitsu:1"), client.metaAsked)
        assertEquals(listOf("anime" to "kitsu:1:2"), client.streamsAsked)
    }

    @Test
    fun `a show is still asked as a series when the addon declares series`() = runBlocking {
        val client = FakeClient()
        val p = provider(
            AddonManifest(id = "a", name = "Addon", types = listOf("movie", "series", "anime"), resources = resources("""[{"name":"stream","types":["movie","series","anime"]}]""")),
            client
        )
        p.getStreamReport(ContentType.TV_SHOW, "kitsu:1", 1, 2)
        p.getStreamReport(ContentType.MOVIE, "tt1", null, null)
        assertEquals(listOf("series" to "kitsu:1:2", "movie" to "tt1"), client.streamsAsked)
    }
}
