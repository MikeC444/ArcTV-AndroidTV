package com.mangotv.app.data.provider

import com.mangotv.app.data.addon.StremioAddonClient
import com.mangotv.app.data.addon.StremioMetaPreview
import com.mangotv.app.data.model.AddonManifest
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.ui.home.dedupeSections
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Home rows follow the web app's rules (domain/homeVariety.ts, homeRows.ts): same layout, same daily rotation, same row order. */
class HomeVarietyTest {
    private val items = (0 until 100).toList()

    @Test fun steadyForTheSameSeedAndRow() {
        assertEquals(varyOrder(items, "u1|2026-10-09", "base"), varyOrder(items, "u1|2026-10-09", "base"))
        assertEquals(pickPage("u1|2026-10-09", "base", listOf(5, 3, 2)), pickPage("u1|2026-10-09", "base", listOf(5, 3, 2)))
    }

    @Test fun changesFromDayToDayAndPersonToPerson() {
        val today = varyOrder(items, "u1|2026-10-09", "base")
        assertNotEquals(today, varyOrder(items, "u1|2026-10-10", "base"))
        assertNotEquals(today, varyOrder(items, "u2|2026-10-09", "base"))
    }

    @Test fun keepsEveryTitle() {
        assertEquals(items, varyOrder(items, "u1|2026-10-09", "toprated").sorted())
    }

    @Test fun favoursBetterRankedTitlesNearTheTop() {
        var topHalf = 0
        for (day in 1..60) topHalf += varyOrder(items, "u1|2026-10-$day", "base").take(10).count { it < 50 }
        assertTrue("got ${topHalf / 600.0}", topHalf / 600.0 > 0.6)
    }

    @Test fun readsMostlyTheFirstPageButSometimesDeeper() {
        val counts = IntArray(3)
        for (day in 1..400) counts[pickPage("u|$day", "base", listOf(5, 3, 2))]++
        assertTrue(counts[0] > counts[1] && counts[1] > counts[2] && counts[2] > 0)
    }

    @Test fun curatedGenresOnlyAndInOrder() {
        assertEquals(listOf("Action", "Horror", "sci-fi"), homeGenresFor(listOf("Western", "Horror", "Action", "sci-fi", "2026")))
        assertEquals(emptyList<String>(), homeGenresFor(listOf("Western", "War")))
        assertEquals("2026-10-09", dayStamp(LocalDate.of(2026, 10, 9)))
    }

    // ── row order for someone who already chose their rows ────────────────────────────────────────────────────────────────

    private fun row(id: String, title: String) = HomeSection(id = id, title = title, items = emptyList())
    private val all = listOf(row("a_base", "Popular"), row("a_Horror", "Horror"), row("a_new", "New"), row("a_toprated", "Top rated"), row("a_Comedy", "Comedy"))
    private fun titles(order: List<String>) = HomeRowPreferences(order = order).applyOrder(all).map { it.title }

    @Test fun newAndTopRatedGoAboveChosenRowsUntilPlaced() {
        assertEquals(listOf("Popular", "New", "Top rated", "Horror", "Comedy"), titles(listOf("a_base", "a_Horror")))
        assertEquals(listOf("New", "Top rated", "Horror", "Popular", "Comedy"), titles(listOf("a_Horror")))
    }

    @Test fun aRowStaysWherePlaced() {
        assertEquals(listOf("Popular", "Top rated", "Horror", "New", "Comedy"), titles(listOf("a_base", "a_Horror", "a_new")))
        assertEquals(listOf("Horror", "New", "Top rated", "Popular", "Comedy"), titles(listOf("a_Horror", "a_new", "a_toprated", "a_base")))
    }

    @Test fun unchangedForSomeoneWhoNeverCustomised() {
        assertEquals(listOf("Popular", "New", "Top rated", "Comedy", "Horror"), titles(emptyList()))
    }

    // ── Cinemeta's rows ──────────────────────────────────────────────────────────────────────────────────────────────────────

    private class FakeClient(val urls: MutableList<String>) : StremioAddonClient() {
        override suspend fun fetchCatalog(manifestUrl: String, type: String, catalogId: String, extra: Map<String, String>): List<StremioMetaPreview> {
            urls += "$type/$catalogId/" + extra.entries.joinToString("&") { "${it.key}=${it.value}" }
            return (0 until 40).map { StremioMetaPreview(id = "$type$it", type = type, name = "$type $it") }
        }
    }

    private val cinemeta = Json { ignoreUnknownKeys = true }.decodeFromString(
        AddonManifest.serializer(), File("src/main/assets/cinemeta_manifest.json").readText()
    )

    private fun homeRows(urls: MutableList<String>): List<HomeSection> = runBlocking {
        StremioAddonProvider("https://c.example/manifest.json", cinemeta, FakeClient(urls)).getHomeSections().toList().flatten()
    }

    @Test fun cinemetaHomeIsPopularNewTopRatedThenNineGenres() {
        HomeVariety.seed = "u1|2026-10-09"
        val urls = mutableListOf<String>()
        val rows = homeRows(urls)
        assertEquals(listOf("Popular", "New", "Top rated", "Action", "Comedy", "Drama", "Thriller", "Horror", "Sci-Fi", "Crime", "Animation", "Documentary"), rows.map { it.title })
        assertTrue(urls.any { Regex("movie/year/genre=\\d{4}").containsMatchIn(it) })
        assertFalse(urls.any { it.contains("/year/") && !it.contains("genre=") })
        val deduped = dedupeSections(rows)
        assertTrue(deduped[0].items.any { it.type == ContentType.MOVIE } && deduped[0].items.any { it.type == ContentType.TV_SHOW })
        val ids = deduped.flatMap { section -> section.items.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun differentPageAndOrderOnDifferentDays_sameAllDay() {
        HomeVariety.seed = "u1|2026-10-09"
        val first = homeRows(mutableListOf())[0].items.map { it.id }
        assertEquals(first, homeRows(mutableListOf())[0].items.map { it.id })
        val pages = HashSet<String>()
        var orderChanged = false
        for (day in 10..40) {
            HomeVariety.seed = "u1|2026-10-$day"
            val urls = mutableListOf<String>()
            val rows = homeRows(urls)
            urls.filter { it.startsWith("movie/top/") }.forEach { pages += Regex("skip=(\\d+)").find(it)?.groupValues?.get(1) ?: "0" }
            if (rows[0].items.map { it.id } != first) orderChanged = true
        }
        assertTrue(pages.size > 1)
        assertTrue(orderChanged)
    }
}
