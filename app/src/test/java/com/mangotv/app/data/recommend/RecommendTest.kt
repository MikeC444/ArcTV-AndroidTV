package com.mangotv.app.data.recommend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the web app's `domain/recommend/recommend.test.ts`: the same fixtures and the same expectations. */
class RecommendTest {

    private fun f(genres: List<String>, directors: List<String> = emptyList(), cast: List<String> = emptyList()) = Features(genres, directors, cast)

    private val meta: Map<String, Features> = mapOf(
        "heist1" to f(listOf("crime", "thriller"), listOf("nolan"), listOf("a", "b")),
        "space1" to f(listOf("sci-fi", "adventure"), listOf("villeneuve"), listOf("c", "d")),
        "rom1" to f(listOf("romance", "comedy"), listOf("ephron"), listOf("e", "f")),
        "heist2" to f(listOf("crime", "thriller"), listOf("nolan"), listOf("a", "g")),
        "heist3" to f(listOf("crime", "action"), listOf("mann"), listOf("h")),
        "space2" to f(listOf("sci-fi", "drama"), listOf("villeneuve"), listOf("c", "i")),
        "space3" to f(listOf("sci-fi", "adventure"), listOf("scott"), listOf("j")),
        "rom2" to f(listOf("romance", "drama"), listOf("ephron"), listOf("e", "k")),
        "rom3" to f(listOf("romance", "comedy"), listOf("curtis"), listOf("l")),
        "nometa" to f(emptyList())
    )
    private val pool: List<Candidate> = listOf("heist2", "heist3", "space2", "space3", "rom2", "rom3", "nometa")
        .map { Candidate(it, it, genres = meta.getValue(it).genres, rating = 7.0) }
    private val loader: FeatureLoader = { refs, _ -> refs.associate { it.id to meta[it.id] } }

    private fun input(id: String, feedback: Feedback? = null, completed: Boolean = false, inWatchlist: Boolean = false) =
        InteractionInput(id, id, feedback, completed, inWatchlist)

    private fun run(inputs: List<InteractionInput>, excludeIds: Set<String> = emptySet(), candidates: List<Candidate> = pool, load: FeatureLoader = loader): EngineResult =
        runBlocking {
            recommend(EngineInput(collectInteractions(inputs), excludeIds, candidates, emptyMap(), load))
        }

    private fun ids(r: EngineResult) = r.items.map { it.id }

    // -- signals ------------------------------------------------------------------------------------------

    @Test
    fun `uses the configured weights, strongest applicable signal per movie`() {
        assertEquals(RecommendConfig.WEIGHT_LIKE, interactionOf(input("x", feedback = Feedback.LIKE))?.weight)
        assertEquals(RecommendConfig.WEIGHT_COMPLETED, interactionOf(input("x", completed = true))?.weight)
        assertEquals(RecommendConfig.WEIGHT_WATCHLIST, interactionOf(input("x", inWatchlist = true))?.weight)
        assertEquals(SignalKind.COMPLETED, interactionOf(input("x", completed = true, inWatchlist = true))?.kind)
        assertNull(interactionOf(input("x")))
    }

    @Test
    fun `explicit feedback overrides finishing and saving`() {
        val i = interactionOf(input("x", feedback = Feedback.DISLIKE, completed = true, inWatchlist = true))!!
        assertEquals(SignalKind.DISLIKE, i.kind)
        assertEquals(-5.0, i.weight, 0.0)
    }

    @Test
    fun `repeated facts cannot inflate a signal`() {
        val once = collectInteractions(listOf(input("heist1", completed = true)))
        val many = collectInteractions(List(10) { input("heist1", completed = true) })
        assertEquals(once, many)
    }

    // -- preferences and score ----------------------------------------------------------------------------

    @Test
    fun `splits a movie's weight equally among its unique features per category`() {
        val prefs = buildPreferences(collectInteractions(listOf(input("heist1", feedback = Feedback.LIKE))), mapOf("heist1" to meta.getValue("heist1")))
        assertEquals(2.5, prefs.genre["crime"]!!, 1e-9)
        assertEquals(5.0, prefs.director["nolan"]!!, 1e-9)
        assertEquals(2.5, prefs.cast["a"]!!, 1e-9)
    }

    @Test
    fun `a long cast list does not outweigh a short one`() {
        val longCast = f(listOf("drama"), emptyList(), listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"))
        val prefs = buildPreferences(collectInteractions(listOf(input("L", feedback = Feedback.LIKE))), mapOf("L" to longCast))
        assertEquals(5.0, prefs.cast.values.sum(), 1e-9)
    }

    @Test
    fun `cosine keeps negative preferences negative`() {
        val prefs = buildPreferences(collectInteractions(listOf(input("heist1", feedback = Feedback.DISLIKE))), mapOf("heist1" to meta.getValue("heist1")))
        assertTrue(scoreCandidate(meta.getValue("heist2"), prefs)!!.score < 0)
        assertEquals(-1.0, cosine(mapOf("x" to 1.0), mapOf("x" to -2.0))!!, 1e-9)
    }

    @Test
    fun `combines categories with the configured weights and renormalises when one is missing`() {
        val prefs = buildPreferences(collectInteractions(listOf(input("heist1", feedback = Feedback.LIKE))), mapOf("heist1" to meta.getValue("heist1")))
        val full = scoreCandidate(meta.getValue("heist2"), prefs)!!
        val expected = (categoryWeight(Category.GENRE) * full.categories.getValue(Category.GENRE) +
            categoryWeight(Category.DIRECTOR) * full.categories.getValue(Category.DIRECTOR) +
            categoryWeight(Category.CAST) * full.categories.getValue(Category.CAST)) /
            (RecommendConfig.CATEGORY_WEIGHT_GENRE + RecommendConfig.CATEGORY_WEIGHT_DIRECTOR + RecommendConfig.CATEGORY_WEIGHT_CAST)
        assertEquals(expected, full.score, 1e-9)
        val genreOnly = scoreCandidate(f(listOf("crime", "thriller")), prefs)!!
        assertEquals(listOf(Category.GENRE), genreOnly.categories.keys.toList())
        assertEquals(genreOnly.categories.getValue(Category.GENRE), genreOnly.score, 1e-9)
    }

    @Test
    fun `handles missing metadata and empty preference vectors`() {
        val prefs = buildPreferences(collectInteractions(listOf(input("heist1", feedback = Feedback.LIKE))), mapOf("heist1" to meta.getValue("heist1")))
        assertNull(scoreCandidate(null, prefs))
        assertNull(scoreCandidate(f(emptyList()), prefs))
        val empty = buildPreferences(collectInteractions(listOf(input("nometa", feedback = Feedback.LIKE))), mapOf("nometa" to meta.getValue("nometa")))
        assertNull(scoreCandidate(meta.getValue("heist2"), empty))
        assertEquals(listOf(Category.DIRECTOR), scoreCandidate(f(emptyList(), listOf("nolan")), prefs)!!.categories.keys.toList())
    }

    @Test
    fun `normalises names`() {
        val features = featuresFromNames(listOf("Sci-Fi", " sci-fi "), listOf("Denis  Villeneuve"), listOf("Zendaya", "Zendaya", "Timothée Chalamet", null))
        assertEquals(Features(listOf("sci-fi"), listOf("denis villeneuve"), listOf("zendaya", "timothée chalamet")), features)
        assertEquals(Features(), featuresFromNames(emptyList(), emptyList(), emptyList()))
    }

    // -- recommend() --------------------------------------------------------------------------------------

    private val sciFiFan3 = listOf(input("space1", Feedback.LIKE), input("space2", Feedback.LIKE), input("heist1", inWatchlist = true))
    private val romFan = listOf(input("rom1", Feedback.LIKE), input("rom2", Feedback.LIKE), input("heist1", inWatchlist = true))

    @Test
    fun `ranks the same catalogue differently for different profiles`() {
        val a = run(sciFiFan3, setOf("space2"))
        val b = run(romFan, setOf("rom2"))
        assertTrue(a is EngineResult.Personal)
        assertTrue(b is EngineResult.Personal)
        assertEquals("space3", ids(a).first())
        assertEquals("rom3", ids(b).first())
        assertNotEquals(ids(a), ids(b))
    }

    @Test
    fun `is deterministic`() {
        assertEquals(ids(run(sciFiFan3, setOf("space2"))), ids(run(sciFiFan3, setOf("space2"))))
    }

    @Test
    fun `never returns excluded titles but still offers watchlisted ones`() {
        val r = run(sciFiFan3, setOf("space3", "heist2"))
        assertFalse("space3" in ids(r))
        assertFalse("heist2" in ids(r))
        assertTrue("rom3" in ids(run(sciFiFan3 + input("rom3", inWatchlist = true))))
    }

    @Test
    fun `a Not for me title pushes down what resembles it`() {
        val liked = run(romFan + input("heist1", Feedback.LIKE))
        val disliked = run(romFan + input("heist1", Feedback.DISLIKE))
        assertTrue(ids(disliked).indexOf("heist2") > ids(liked).indexOf("heist2"))
        assertTrue(disliked.items.first { it.id == "heist2" }.score!! < 0)
    }

    @Test
    fun `explanations come from real positive contributions`() {
        val r = run(sciFiFan3, setOf("space2"))
        assertTrue(Regex("^Because you liked space[12]$").matches(r.items.first().reason!!))
        val prefs = buildPreferences(collectInteractions(sciFiFan3), meta)
        assertNull(explainCandidate(f(listOf("western")), prefs))
    }

    @Test
    fun `cold start gives the labelled popular fallback`() {
        assertEquals(3, RecommendConfig.MIN_INTERACTIONS_FOR_PERSONALISATION)
        val r = run(listOf(input("space1", Feedback.LIKE), input("rom1", inWatchlist = true)))
        assertTrue(r is EngineResult.Popular)
        assertTrue(r.items.all { it.score == null && it.reason == null })
        assertTrue(run(emptyList()) is EngineResult.Popular)
        val onlyNegative = run(listOf(input("space1", Feedback.DISLIKE), input("rom1", Feedback.DISLIKE), input("heist1", Feedback.DISLIKE)))
        assertTrue(onlyNegative is EngineResult.Popular)
    }

    @Test
    fun `falls back when nothing can be scored`() {
        val blind = run(sciFiFan3, candidates = listOf(Candidate("x", "x", genres = emptyList(), rating = 5.0)), load = { _, _ -> emptyMap() })
        assertTrue(blind is EngineResult.Popular)
    }

    @Test
    fun `returns at most 20 and bounds how many candidates get a detail lookup`() {
        val big = List(300) { Candidate("m$it", "m$it", genres = listOf("sci-fi"), rating = 5.0) }
        var looked = 0
        val counting: FeatureLoader = { refs, limit ->
            looked = maxOf(looked, minOf(limit, refs.size))
            refs.associate { it.id to (meta[it.id] ?: f(listOf("sci-fi"))) }
        }
        val r = run(sciFiFan3, candidates = big, load = counting)
        assertTrue(r.items.size <= 20)
        assertTrue(looked <= 60)
    }

    @Test
    fun `popular fallback skips excluded titles and duplicates`() {
        val r = run(emptyList(), setOf("heist2"), pool + pool[0])
        assertFalse("heist2" in ids(r))
        assertEquals(ids(r).size, ids(r).toSet().size)
    }

    // -- explanations reflect the whole profile -----------------------------------------------------------

    private val h: Map<String, Features> = mapOf(
        "mommy" to f(listOf("horror"), listOf("d0"), listOf("x0")),
        "deep" to f(listOf("horror", "thriller", "mystery"), listOf("d1"), listOf("x1", "x2")),
        "ends" to f(listOf("horror", "thriller", "mystery"), listOf("d2"), listOf("x3", "x4")),
        "empty" to f(listOf("horror", "mystery", "drama"), listOf("d3"), listOf("x5", "x6")),
        "oak" to f(listOf("horror", "thriller", "drama"), listOf("d4"), listOf("x7", "x8")),
        "warfare" to f(listOf("war", "action", "drama"), listOf("d5"), listOf("x9")),
        "carry" to f(listOf("action", "thriller", "crime"), listOf("d6"), listOf("x10")),
        "candA" to f(listOf("horror", "mystery", "thriller"), listOf("d9"), listOf("z1")),
        "candB" to f(listOf("horror", "thriller", "drama"), listOf("d9"), listOf("z2")),
        "candC" to f(listOf("action", "thriller", "crime"), listOf("d8"), listOf("z3")),
        "candD" to f(listOf("war", "action", "drama"), listOf("d7"), listOf("z4"))
    )
    private val hLoader: FeatureLoader = { refs, _ -> refs.associate { it.id to h[it.id] } }

    @Test
    fun `builds preferences from every interacted movie, not from one`() {
        val mine = listOf(input("mommy", inWatchlist = true)) + listOf("deep", "ends", "empty", "oak", "warfare", "carry").map { input(it, completed = true) }
        val prefs = buildPreferences(collectInteractions(mine), h)
        val sources = prefs.contributions.values.flatten().map { it.id }.toSet()
        assertEquals(setOf("mommy", "deep", "ends", "empty", "oak", "warfare", "carry"), sources)
        assertTrue(prefs.genre["horror"]!! > 3)
    }

    @Test
    fun `cites the movie that most resembles each pick and different picks can cite different movies`() {
        val mine = listOf(input("mommy", inWatchlist = true)) + listOf("deep", "ends", "empty", "oak", "warfare", "carry").map { input(it, completed = true) }
        val cands = listOf("candA", "candB", "candC", "candD").map { Candidate(it, it, genres = h.getValue(it).genres, rating = 7.0) }
        val r = run(mine, candidates = cands, load = hLoader)
        val reasons = r.items.associate { it.id to it.reason }
        assertNotEquals("Because you saved mommy", reasons["candA"])
        assertTrue(Regex("^Because you watched (deep|ends|empty|oak)$").matches(reasons["candA"]!!))
        assertTrue(Regex("^Because you watched (carry|warfare|deep|ends|oak)$").matches(reasons["candC"]!!))
        assertTrue(reasons.values.toSet().size > 1)
    }

    @Test
    fun `prefers a stronger signal over a weaker one when resemblance is equal`() {
        val tied = listOf(input("deep", inWatchlist = true), input("ends", Feedback.LIKE), input("empty", inWatchlist = true), input("oak", completed = true))
        val loader2: FeatureLoader = { refs, _ -> refs.associate { x -> x.id to (if (x.id == "ends") h.getValue("deep") else h[x.id]) } }
        val cand = Candidate("candA", "candA", genres = h.getValue("candA").genres, rating = 7.0)
        val r = run(tied, candidates = listOf(cand), load = loader2)
        assertEquals("Because you liked ends", r.items.first().reason)
    }

    // -- a movie never picks itself -----------------------------------------------------------------------

    private val self: Map<String, Features> = mapOf(
        "saved" to f(listOf("horror", "mystery"), listOf("dq"), listOf("q1", "q2")),
        "liked" to f(listOf("horror", "thriller"), listOf("dl"), listOf("l1")),
        "a" to f(listOf("horror", "thriller", "mystery"), listOf("da"), listOf("a1")),
        "b" to f(listOf("horror", "thriller", "drama"), listOf("db"), listOf("b1")),
        "c" to f(listOf("horror", "mystery", "drama"), listOf("dc"), listOf("c1")),
        "other" to f(listOf("horror", "mystery"), listOf("dz"), listOf("z1"))
    )
    private val selfLoader: FeatureLoader = { refs, _ -> refs.associate { it.id to self[it.id] } }
    private val selfBase = listOf(input("a", completed = true), input("b", completed = true), input("c", completed = true))
    private fun selfCand(id: String) = Candidate(id, id, genres = self.getValue(id).genres, rating = 7.0)

    @Test
    fun `does not cite the movie itself as the reason for its own pick`() {
        val r = run(selfBase + input("saved", inWatchlist = true), candidates = listOf(selfCand("saved"), selfCand("other")), load = selfLoader)
        val mine = r.items.firstOrNull { it.id == "saved" }
        assertFalse((mine?.reason ?: "").endsWith("saved"))
        for (item in r.items) assertFalse((item.reason ?: "").contains("you liked ${item.id}"))
    }

    @Test
    fun `is scored as if its own signal were not there`() {
        val withSelf = run(selfBase + input("saved", inWatchlist = true) + input("liked", Feedback.LIKE), candidates = listOf(selfCand("saved"), selfCand("liked"), selfCand("other")), load = selfLoader)
        val without = run(selfBase + input("liked", Feedback.LIKE), candidates = listOf(selfCand("saved")), load = selfLoader)
        val a = withSelf.items.first { it.id == "saved" }
        val b = without.items.first { it.id == "saved" }
        assertEquals(b.score!!, a.score!!, 1e-9)
    }

    // -- diversity step -----------------------------------------------------------------------------------

    private fun mk(id: String, genres: List<String>, director: String, cast: List<String>) = id to f(genres, listOf(director), cast)

    private val own: List<Pair<String, Features>> = listOf(
        mk("h1", listOf("horror", "thriller", "mystery"), "dh1", listOf("ah1")),
        mk("h2", listOf("horror", "thriller", "drama"), "dh2", listOf("ah2")),
        mk("h3", listOf("horror", "mystery", "drama"), "dh3", listOf("ah3")),
        mk("h4", listOf("horror", "thriller"), "dh4", listOf("ah4")),
        mk("w1", listOf("war", "action", "drama"), "dw1", listOf("aw1")),
        mk("w2", listOf("action", "adventure", "war"), "dw2", listOf("aw2")),
        mk("w3", listOf("action", "thriller", "crime"), "dw3", listOf("aw3"))
    )
    private val horror = List(30) { i ->
        Triple("H$i", f(if (i % 2 == 1) listOf("horror", "thriller", "mystery") else listOf("horror", "thriller", "drama"), listOf("dx${i % 5}"), listOf("cx$i")), 9.0)
    }
    private val action = List(10) { i ->
        Triple("A$i", f(if (i % 2 == 1) listOf("war", "action", "drama") else listOf("action", "adventure", "war"), listOf("dy${i % 3}"), listOf("cy$i")), 6.0)
    }
    private val all: Map<String, Features> = (own + horror.map { it.first to it.second } + action.map { it.first to it.second }).toMap()
    private val diverseCands = (horror + action).map { (id, ft, rating) -> Candidate(id, id, genres = ft.genres, rating = rating) }
    private val loadAll: FeatureLoader = { refs, _ -> refs.associate { it.id to all[it.id] } }
    private fun goDiverse() = run(own.map { input(it.first, completed = true) }, candidates = diverseCands, load = loadAll)

    @Test
    fun `covers every taste in the list, not just the biggest one`() {
        val picked = ids(goDiverse())
        assertTrue(picked.any { it.startsWith("A") })
        assertTrue(picked.count { it.startsWith("H") } < picked.size)
    }

    @Test
    fun `lets no single movie explain more than the maximum picks, and reasons stay truthful`() {
        val counts = HashMap<String, Int>()
        for (item in goDiverse().items) {
            val m = Regex("^Because you watched (\\w+)$").matchEntire(item.reason ?: "")
            if (m != null) counts[m.groupValues[1]] = (counts[m.groupValues[1]] ?: 0) + 1
        }
        assertTrue(counts.values.max() <= RecommendConfig.MAX_PICKS_PER_SOURCE)
        assertTrue(counts.size >= 5)
    }

    @Test
    fun `still returns the best-scoring picks first and fills up to 20`() {
        val r = goDiverse()
        assertEquals(20, r.items.size)
        assertTrue(r is EngineResult.Personal)
        assertTrue(r.items.first().score!! > 0)
    }

    // -- cache signature ----------------------------------------------------------------------------------

    @Test
    fun `the signature changes exactly when feedback, completion or watchlist change`() {
        val base = listOf(input("a", Feedback.LIKE), input("b", inWatchlist = true))
        val sig = signatureOf(collectInteractions(base))
        assertEquals(sig, signatureOf(collectInteractions(base)))
        assertNotEquals(sig, signatureOf(collectInteractions(listOf(input("a", Feedback.DISLIKE), input("b", inWatchlist = true)))))
        assertNotEquals(sig, signatureOf(collectInteractions(listOf(input("a", Feedback.LIKE)))))
        assertNotEquals(sig, signatureOf(collectInteractions(base + input("c", completed = true))))
        assertEquals(sig, signatureOf(collectInteractions(listOf(input("a", Feedback.LIKE, completed = true), input("b", inWatchlist = true)))))
        assertNotNull(sig)
    }

    // -- TV shows in the same row ---------------------------------------------------------------------------

    @Test
    fun `a show can be picked from a movie taste, and its details are looked up as a show`() {
        val shows = mapOf("show1" to f(listOf("crime", "thriller"), emptyList(), listOf("a", "z")), "show2" to f(listOf("romance", "comedy"), emptyList(), listOf("y")))
        val mixed = pool + listOf(
            Candidate("show1", "show1", genres = shows.getValue("show1").genres, rating = 8.0, type = com.mangotv.app.data.model.ContentType.TV_SHOW),
            Candidate("show2", "show2", genres = shows.getValue("show2").genres, rating = 8.0, type = com.mangotv.app.data.model.ContentType.TV_SHOW)
        )
        val asked = mutableListOf<MovieRef>()
        val load: FeatureLoader = { refs, _ ->
            asked += refs
            refs.associate { it.id to (meta[it.id] ?: shows[it.id]) }
        }
        val result = run(listOf(input("heist1", feedback = Feedback.LIKE), input("heist2", completed = true), input("space1", inWatchlist = true)), candidates = mixed, load = load)
        assertTrue(result is EngineResult.Personal)
        assertTrue("show1" in ids(result))
        assertEquals(com.mangotv.app.data.model.ContentType.TV_SHOW, asked.first { it.id == "show1" }.type)
    }

    @Test
    fun `a liked show shapes the taste like a liked movie`() {
        val shows = mapOf("show1" to f(listOf("crime", "thriller"), emptyList(), listOf("a", "z")))
        val load: FeatureLoader = { refs, _ -> refs.associate { it.id to (meta[it.id] ?: shows[it.id]) } }
        val result = run(
            listOf(input("show1", feedback = Feedback.LIKE), input("heist2", feedback = Feedback.LIKE), input("heist1", completed = true)),
            excludeIds = setOf("show1", "heist2", "heist1"),
            load = load
        )
        assertEquals("heist3", ids(result).first())
    }
}
