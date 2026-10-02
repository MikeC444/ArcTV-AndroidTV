package com.mangotv.app.data.recommend

import com.mangotv.app.data.feedback.FeedbackEntry
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.provider.SavedListItem
import com.mangotv.app.data.recommend.RecommendConfig.MAX_RESULTS
import com.mangotv.app.data.recommend.RecommendConfig.ROTATION_ANCHORS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the web app's `rotation.test.ts` (and the exclusion / dismissal rules), so both apps behave the same. */
class RotationTest {

    private data class P(override val id: String, override val score: Double, override val genre: String?) : ComposePick

    private fun ids(xs: List<ComposePick>) = xs.map { it.id }

    // 30 horror picks scoring high, 10 comedy and 10 action picks scoring lower: what a horror-heavy profile looks like.
    private val horror = List(30) { i -> P("h$i", 0.95 - i * 0.005, "horror") }
    private val comedy = List(10) { i -> P("c$i", 0.45 - i * 0.01, "comedy") }
    private val action = List(10) { i -> P("a$i", 0.4 - i * 0.01, "action") }
    private val all = (horror + comedy + action).sortedByDescending { it.score }
    private val shares = mapOf("horror" to 0.75, "comedy" to 0.15, "action" to 0.1)
    private fun count(row: List<P>, genre: String) = row.count { it.genre == genre }

    @Test
    fun givesTheRowTheProfilesGenreSplitInsteadOfTheBiggestTasteFillingEveryPlace() {
        val row = compose(all, MAX_RESULTS, shares = shares)
        assertEquals(MAX_RESULTS, row.size)
        assertEquals(15, count(row, "horror")) // 75% of 20
        assertEquals(3, count(row, "comedy")) // 15%
        assertEquals(2, count(row, "action")) // 10%
    }

    @Test
    fun aProfileWithOneTasteGetsAOneGenreRow() {
        val row = compose(horror, MAX_RESULTS, shares = mapOf("horror" to 1.0))
        assertEquals(MAX_RESULTS, count(row, "horror"))
    }

    @Test
    fun alwaysKeepsTheStrongestPicksMixesGenresThroughTheRowAndNeverAltersAScore() {
        for (seed in 0 until 100) {
            val row = compose(all, MAX_RESULTS, shares = shares, seed = seed)
            for (i in 0 until ROTATION_ANCHORS) assertTrue(ids(row).contains("h$i"))
            for (p in row) assertEquals(all.first { it.id == p.id }.score, p.score, 0.0)
            assertEquals(row.size, ids(row).toSet().size)
            // a minority genre shows up in the first half of the row, not only at the end
            assertTrue(row.take(12).any { it.genre != "horror" })
        }
    }

    @Test
    fun isRepeatableForASeedAndDifferentAcrossSeeds() {
        assertEquals(ids(compose(all, MAX_RESULTS, shares = shares, seed = 7)), ids(compose(all, MAX_RESULTS, shares = shares, seed = 7)))
        val rows = HashSet<String>()
        for (seed in 0 until 30) rows += ids(compose(all, MAX_RESULTS, shares = shares, seed = seed)).joinToString(",")
        assertTrue("rows: ${rows.size}", rows.size > 20)
    }

    @Test
    fun swapsMostOfTheRowOnTheNextLaunchWhenToldWhatTheLastOneShowedKeepingTheGenreSplit() {
        var previous = ids(compose(all, MAX_RESULTS, shares = shares, seed = 1)).toSet()
        var kept = 0
        var loads = 0
        for (seed in 2 until 40) {
            val row = compose(all, MAX_RESULTS, shares = shares, seed = seed, previous = previous)
            kept += row.count { it.id in previous }
            loads++
            previous = ids(row).toSet()
            assertEquals(15, count(row, "horror"))
        }
        val average = kept.toDouble() / loads
        assertTrue("kept on average: $average", average < MAX_RESULTS * 0.7)
        assertTrue("kept on average: $average", average >= ROTATION_ANCHORS)
    }

    @Test
    fun neverDrawsInAPickThatScoredZeroOrBelowOrFarBelowTheBestOfItsGenre() {
        val list = horror.take(10) + listOf(P("c-ok", 0.4, "comedy"), P("c-weak", 0.05, "comedy"), P("c-zero", 0.0, "comedy"), P("c-neg", -0.3, "comedy"))
        for (seed in 0 until 100) {
            val row = ids(compose(list, 8, shares = mapOf("horror" to 0.6, "comedy" to 0.4), seed = seed))
            assertFalse(row.contains("c-zero"))
            assertFalse(row.contains("c-neg"))
            assertFalse(row.contains("c-weak"))
        }
    }

    @Test
    fun handsAGenresPlacesToTheNextGenreWhenItHasTooFewAndFillsWithTheBestScoresIfEverythingRunsShort() {
        val row = compose(horror.take(6) + P("c0", 0.4, "comedy"), 10, shares = shares)
        assertEquals(7, row.size) // only seven picks exist
        assertTrue(ids(row).contains("c0"))
    }

    @Test
    fun seededRandomMatchesTheWebAppsNumbersAndStaysInRange() {
        // Values produced by the web app's seededRandom (domain/recommend/rotation.ts) for the same seeds.
        val expected = mapOf(
            1 to listOf(0.627073940588, 0.002735721180, 0.527447039960, 0.981050967472),
            7 to listOf(0.011704753153, 0.061958257575, 0.976907632779, 0.699028705712),
            -1 to listOf(0.896422614111, 0.189478256740, 0.715652678162, 0.944059909321) // 4294967295 as the web writes it
        )
        for ((seed, values) in expected) {
            val random = seededRandom(seed)
            for (value in values) assertEquals("seed $seed", value, random(), 1e-9)
        }
        val a = seededRandom(7)
        val b = seededRandom(7)
        repeat(20) {
            val x = a()
            assertEquals(x, b(), 0.0)
            assertTrue(x >= 0.0 && x < 1.0)
        }
    }

    // ── taste shares ──────────────────────────────────────────────────────────

    @Test
    fun tasteSharesFollowTheSignalWeightOfEachMoviesPrimaryGenre() {
        val own = mapOf(
            "lh1" to Features(genres = listOf("horror", "thriller")),
            "lh2" to Features(genres = listOf("horror", "thriller")),
            "lh3" to Features(genres = listOf("horror", "thriller")),
            "lc1" to Features(genres = listOf("comedy"))
        )
        val interactions = own.keys.map { Interaction(it, it, SignalKind.LIKE, 5.0) }
        val prefs = buildPreferences(interactions, own)
        val shares = tasteShares(interactions, own, prefs)
        assertEquals(1.0, shares.values.sum(), 1e-9)
        assertEquals(0.75, shares["horror"]!!, 1e-9)
        assertEquals(0.25, shares["comedy"]!!, 1e-9)
        assertEquals("comedy", primaryGenre(listOf("comedy", "romance"), prefs))
        assertNull(primaryGenre(listOf("western"), prefs))
        assertTrue(tasteShares(emptyList(), own, prefs).isEmpty())
    }

    // ── the engine: a mostly-horror profile still gets its other tastes ──────────

    private val meta = HashMap<String, Features>()
    private val pool = ArrayList<Candidate>()

    private fun addCandidate(id: String, genres: List<String>, director: String, cast: String) {
        meta[id] = Features(genres = genres, directors = listOf(director), cast = listOf(cast))
        pool += Candidate(id, id, genres = genres, rating = 7.0)
    }

    private fun setUpPool() {
        for (i in 0 until 30) addCandidate("hor$i", if (i % 2 == 0) listOf("horror") else listOf("horror", "thriller"), "hd${i % 6}", "hc${i % 6}")
        for (i in 0 until 12) addCandidate("com$i", if (i % 2 == 0) listOf("comedy") else listOf("comedy", "romance"), "cd${i % 4}", "cc${i % 4}")
        for (id in listOf("lh1", "lh2", "lh3")) meta[id] = Features(genres = listOf("horror", "thriller"), directors = listOf("hd1"), cast = listOf("hc1"))
        meta["lc1"] = Features(genres = listOf("comedy"), directors = listOf("cd1"), cast = listOf("cc1"))
    }

    private fun go(seed: Int? = null, previous: Set<String> = emptySet()): EngineResult = runBlocking {
        val liked = listOf("lh1", "lh2", "lh3", "lc1").map { InteractionInput(it, it, feedback = Feedback.LIKE) }
        recommend(
            EngineInput(
                interactions = collectInteractions(liked),
                excludeIds = emptySet(),
                pool = pool,
                interactionRefs = emptyMap(),
                loadFeatures = { refs, _ -> refs.associate { it.id to meta[it.id] } },
                seed = seed,
                previousShown = previous
            )
        )
    }

    @Test
    fun aMostlyHorrorProfileStillGetsItsComedyTasteWithRealScoresAndReasons() {
        setUpPool()
        for (seed in listOf(null, 1, 2, 3)) {
            val result = go(seed)
            assertTrue(result is EngineResult.Personal)
            val comedyPicks = result.items.filter { it.id.startsWith("com") }
            assertTrue("comedy picks: ${comedyPicks.size}", comedyPicks.size >= 3) // about a quarter of 20
            assertTrue(comedyPicks.size < result.items.size / 2)
            for (item in result.items) {
                assertTrue(item.score!! > 0)
                assertTrue(item.reason!!.startsWith("Because you liked "))
            }
            for (c in comedyPicks) assertEquals("Because you liked lc1", c.reason)
        }
    }

    @Test
    fun withoutASeedItIsFullyDeterministicAndKeepsTheStrongestPicksWithASeed() {
        setUpPool()
        val base = go()
        assertEquals(base.items.map { it.id }, go().items.map { it.id })
        for (seed in listOf(1, 2, 3, 4)) {
            val ids = go(seed).items.map { it.id }
            for (top in base.items.take(ROTATION_ANCHORS)) assertTrue(ids.contains(top.id))
        }
    }

    @Test
    fun aRefreshSwapsMostOfTheRowOnARealisticProfile() {
        // A profile of 12 liked movies (mostly horror) against 210 candidates. On a tiny pool (a handful of liked movies, a few dozen
        // candidates) there is simply no room to rotate, because one liked movie can explain at most MAX_PICKS_PER_SOURCE picks. The
        // web app's engine on this same data keeps about 6 of the 20 between launches, so most of the row changes.
        val features = HashMap<String, Features>()
        val candidates = ArrayList<Candidate>()
        fun add(id: String, genres: List<String>, director: String, cast: String) {
            features[id] = Features(genres = genres, directors = listOf(director), cast = listOf(cast))
            candidates += Candidate(id, id, genres = genres, rating = (6 + id.length % 3).toDouble())
        }
        for (i in 0 until 150) {
            val genres = when (i % 3) {
                0 -> listOf("horror")
                1 -> listOf("horror", "thriller")
                else -> listOf("horror", "mystery")
            }
            add("hor$i", genres, "hd${i % 17}", "hc${i % 23}")
        }
        for (i in 0 until 60) add("com$i", if (i % 2 == 0) listOf("comedy") else listOf("comedy", "romance"), "cd${i % 11}", "cc${i % 13}")
        val own = ArrayList<String>()
        for (i in 0 until 9) {
            val id = "lh$i"
            own += id
            features[id] = Features(genres = if (i % 2 == 1) listOf("horror", "thriller") else listOf("horror"), directors = listOf("hd${i % 17}"), cast = listOf("hc${i % 23}"))
        }
        for (i in 0 until 3) {
            val id = "lc$i"
            own += id
            features[id] = Features(genres = listOf("comedy"), directors = listOf("cd${i % 11}"), cast = listOf("cc${i % 13}"))
        }

        fun launch(seed: Int, previous: Set<String>): List<String> = runBlocking {
            recommend(
                EngineInput(
                    interactions = collectInteractions(own.map { InteractionInput(it, it, feedback = Feedback.LIKE) }),
                    excludeIds = emptySet(),
                    pool = candidates,
                    interactionRefs = emptyMap(),
                    loadFeatures = { refs, _ -> refs.associate { it.id to features[it.id] } },
                    seed = seed,
                    previousShown = previous
                )
            ).items.map { it.id }
        }

        var previous = launch(1, emptySet()).toSet()
        var kept = 0
        for (seed in 2 until 12) {
            val ids = launch(seed, previous)
            kept += ids.count { it in previous }
            previous = ids.toSet()
        }
        val average = kept / 10.0
        assertTrue("kept on average: $average of $MAX_RESULTS", average < 10.0) // web engine: about 6
        assertTrue("the strongest picks stay: $average", average >= ROTATION_ANCHORS)
    }

    // ── liked titles are not offered; hand-removed titles stay out ───────────────

    private fun saved(id: String, watched: Boolean = false) =
        SavedListItem(id = id, type = ContentType.MOVIE, title = id, posterUrl = null, backdropUrl = null, year = null, rating = null, providerId = "p", watched = watched)

    private fun entry(value: Feedback) = FeedbackEntry(value = value.wire, title = "t", at = "2025-01-01T00:00:00.000Z", providerId = "p", synced = true)

    private fun movie(id: String) = Content(id = id, type = ContentType.MOVIE, title = id, description = "", posterUrl = null, backdropUrl = null, genres = listOf(Genre("g", "g")))

    @Test
    fun aLikedTitleIsNeverOfferedAgainAndNeitherIsANotForMeOrAFinishedOrAHandRemovedOne() {
        val excluded = excludedFromPicks(
            list = listOf(saved("done", watched = true), saved("saved")),
            feedback = mapOf("liked" to entry(Feedback.LIKE), "no" to entry(Feedback.DISLIKE)),
            continueWatching = emptyList(),
            dismissed = setOf("removed")
        )
        assertEquals(setOf("done", "liked", "no", "removed"), excluded)
        assertFalse("a merely saved title can still be picked", "saved" in excluded)
    }

    @Test
    fun theRowDropsATitleTheMomentItIsLikedDislikedOrRemovedAndMarksTheRest() {
        val movies = listOf("a", "b", "c", "d").map(::movie)
        val result = EngineResult.Personal(movies.map { Picked(it.id, 1.0, "Because you liked x") })
        val section = pickedSection(result, movies, feedback = mapOf("b" to entry(Feedback.LIKE), "c" to entry(Feedback.DISLIKE)), dismissed = setOf("d"))
        assertNotNull(section)
        assertEquals(listOf("a"), section!!.items.map { it.id })
        assertTrue(section.items.all { it.pickedForYou })
        assertNull(pickedSection(result, movies, emptyMap(), setOf("a", "b", "c", "d")))
    }

    @Test
    fun continueWatchingTitlesStayOut() {
        val cw = ContinueWatchingEntry(
            providerId = "p", contentId = "cw1", contentType = ContentType.MOVIE, seasonNumber = null, episodeNumber = null, episodeTitle = null,
            title = "cw1", posterUrl = null, backdropUrl = null, positionMs = 1000, durationMs = 10000, lastWatchedAt = "2025-01-01T00:00:00.000Z"
        )
        assertTrue("cw1" in excludedFromPicks(emptyList(), emptyMap(), listOf(cw)))
    }
}
