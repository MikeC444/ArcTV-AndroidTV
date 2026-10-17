package com.mangotv.app.data.provider

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.model.Stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailSourceTest {

    private class FakeProvider(override val id: String, private val details: (String) -> Content?) : CatalogProvider {
        override val name: String = id
        val asked = mutableListOf<String>()
        override fun getHomeSections(): Flow<List<HomeSection>> = emptyFlow()
        override suspend fun getDetails(type: ContentType, id: String): Content? {
            asked += id
            return details(id)
        }
        override suspend fun getStreams(type: ContentType, id: String, season: Int?, episode: Int?): List<Stream> = emptyList()
        override suspend fun getSectionsByType(type: ContentType, genre: String?): List<HomeSection> = emptyList()
        override suspend fun getAvailableGenres(): List<String> = emptyList()
        override suspend fun getGenreSection(genre: String): HomeSection? = null
        override suspend fun search(query: String, onPartial: ((List<Content>) -> Unit)?): List<Content> = emptyList()
        override suspend fun getMoreItemsByType(type: ContentType, page: Int, genre: String?): List<Content> = emptyList()
        override suspend fun getMoreGenreItems(genre: String, page: Int): List<Content> = emptyList()
    }

    private fun content(title: String, providerId: String) = Content(id = "tt1", type = ContentType.MOVIE, title = title, description = "", posterUrl = null, backdropUrl = null, providerId = providerId)

    @Test
    fun `Cinemeta is asked first for a title with an IMDb id, then the owner, then the rest`() {
        val installed = listOf("other", "owner", CINEMETA_PROVIDER_ID, "last")
        assertEquals(listOf(CINEMETA_PROVIDER_ID, "owner", "other", "last"), detailOrder(installed, "owner", "tt0111161"))
    }

    @Test
    fun `a title without an IMDb id goes to its owner first, and Cinemeta is not asked first`() {
        val installed = listOf(CINEMETA_PROVIDER_ID, "owner")
        assertEquals(listOf("owner", CINEMETA_PROVIDER_ID), detailOrder(installed, "owner", "kitsu:1"))
    }

    @Test
    fun `without Cinemeta installed the owner is first, and an owner that is not installed is skipped`() {
        assertEquals(listOf("owner", "other"), detailOrder(listOf("other", "owner"), "owner", "tt1"))
        assertEquals(listOf("other"), detailOrder(listOf("other"), "gone", "tt1"))
    }

    @Test
    fun `the owner being Cinemeta is not listed twice`() {
        assertEquals(listOf(CINEMETA_PROVIDER_ID, "other"), detailOrder(listOf("other", CINEMETA_PROVIDER_ID), CINEMETA_PROVIDER_ID, "tt1"))
    }

    @Test
    fun `a catalogue-only owner has no details, so the next addon answers, and the result stays filed under the owner`() = runBlocking {
        val owner = FakeProvider("catalog-only") { null }
        val cinemeta = FakeProvider(CINEMETA_PROVIDER_ID) { content("Found", CINEMETA_PROVIDER_ID) }
        val result = loadDetails(listOf(owner, cinemeta), "catalog-only", ContentType.MOVIE, "tt1")
        assertEquals("Found", result?.title)
        assertEquals("catalog-only", result?.providerId)
    }

    @Test
    fun `an addon that throws is skipped`() = runBlocking {
        val broken = FakeProvider(CINEMETA_PROVIDER_ID) { throw IllegalStateException("down") }
        val owner = FakeProvider("owner") { content("From owner", "owner") }
        assertEquals("From owner", loadDetails(listOf(broken, owner), "owner", ContentType.MOVIE, "tt1")?.title)
    }

    @Test
    fun `the first addon to answer ends the search, and none answering gives null`() = runBlocking {
        val first = FakeProvider(CINEMETA_PROVIDER_ID) { content("First", CINEMETA_PROVIDER_ID) }
        val second = FakeProvider("owner") { content("Second", "owner") }
        loadDetails(listOf(first, second), "owner", ContentType.MOVIE, "tt1")
        assertEquals(emptyList<String>(), second.asked)
        assertNull(loadDetails(listOf(FakeProvider("a") { null }), "a", ContentType.MOVIE, "tt1"))
        assertNull(loadDetails(emptyList(), "a", ContentType.MOVIE, "tt1"))
    }
}
