package com.mangotv.app.data.recommend

import com.mangotv.app.data.recommend.RecommendConfig.CANDIDATE_DETAIL_FETCH_LIMIT
import com.mangotv.app.data.recommend.RecommendConfig.INTERACTION_DETAIL_FETCH_LIMIT
import com.mangotv.app.data.recommend.RecommendConfig.MAX_RESULTS
import com.mangotv.app.data.recommend.RecommendConfig.MIN_INTERACTIONS_FOR_PERSONALISATION
import com.mangotv.app.data.recommend.RecommendConfig.SHORTLIST_BY_SCORE
import com.mangotv.app.data.recommend.RecommendConfig.SHORTLIST_GENRE_BUDGET
import com.mangotv.app.data.recommend.RecommendConfig.SHORTLIST_GENRE_MIN
import com.mangotv.app.data.recommend.RecommendConfig.SHORTLIST_SOURCE_MOVIES
import kotlin.math.ceil

/** A catalogue entry that could be recommended. [genres] come from the catalogue listing itself (no extra request). */
data class Candidate(
    val id: String,
    val title: String,
    val providerId: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null
)

data class MovieRef(val id: String, val providerId: String? = null)

/** Fetches (or reads from cache) the features of up to `limit` movies not yet known. Unknown or failed lookups come back as null. Must never throw. */
typealias FeatureLoader = suspend (refs: List<MovieRef>, limit: Int) -> Map<String, Features?>

class EngineInput(
    val interactions: List<Interaction>,
    /** Never recommended: finished, disliked / dismissed, or already in Continue Watching. */
    val excludeIds: Set<String>,
    val pool: List<Candidate>,
    /** providerId for each of the profile's own movies, so their metadata can be looked up. */
    val interactionRefs: Map<String, MovieRef>,
    val loadFeatures: FeatureLoader,
    /** When set, a refresh swaps most of the row (see Rotation.kt): the strongest picks stay, the rest are drawn with this seed. Null = fully deterministic. */
    val seed: Int? = null,
    /** Ids shown by the previous launch; they are less likely to be drawn again. */
    val previousShown: Set<String> = emptySet()
)

data class Picked(
    val id: String,
    /** Internal ranking value; null in the popular fallback. Never shown as a percentage. */
    val score: Double?,
    val reason: String?
)

sealed interface EngineResult {
    val items: List<Picked>

    data class Personal(override val items: List<Picked>) : EngineResult
    data class Popular(override val items: List<Picked>) : EngineResult
}

private val byRatingThenId: Comparator<Candidate> =
    compareByDescending<Candidate> { it.rating ?: -1.0 }.thenBy { it.id }

/** Distinct, eligible candidates, in a deterministic order. */
private fun eligible(pool: List<Candidate>, excludeIds: Set<String>): List<Candidate> {
    val seen = HashSet<String>()
    val out = ArrayList<Candidate>()
    for (candidate in pool) {
        if (candidate.id in excludeIds || !seen.add(candidate.id)) continue
        out += candidate
    }
    return out
}

private fun normaliseGenres(genres: List<String>): List<String> =
    genres.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

/** Cold start / nothing scorable: the best-rated eligible movies, plainly labelled as popular, never as personalised. */
fun popularFallback(pool: List<Candidate>, excludeIds: Set<String>): EngineResult =
    EngineResult.Popular(
        eligible(pool, excludeIds).sortedWith(byRatingThenId).take(MAX_RESULTS).map { Picked(it.id, null, null) }
    )

private class ScoredCandidate(
    override val id: String,
    val candidate: Candidate,
    override val score: Double,
    override val sources: List<Source>,
    override val genre: String?
) : ScoredPick, ComposePick

/**
 * Ranking in four plain steps:
 *  1. look up the profile's own movies' features and turn the stored signals into genre / director / cast preference vectors;
 *  2. shortlist the eligible candidates from their catalogue genres alone: some by overall genre match, the rest round-robin
 *     over each of the profile's own movies (so every taste in the list is represented), within a bounded size;
 *  3. fetch the shortlist's directors and cast (cached, bounded concurrency) and score each with the full weighted cosine;
 *  4. sort by score (ties: rating, then id), then the composition step (Rotation.kt: the strongest picks stay, the other places follow
 *     the profile's genre split and, with a seed, are drawn so a refresh changes most of them), then the separate diversity step keeps the top 20 while stopping any one of the
 *     profile's movies from explaining more than MAX_PICKS_PER_SOURCE of them.
 *
 * Ported from the web app's `domain/recommend/engine.ts`.
 */
suspend fun recommend(input: EngineInput): EngineResult {
    val pool = eligible(input.pool, input.excludeIds)
    val signalled = input.interactions.filter { it.weight != 0.0 }
    if (signalled.size < MIN_INTERACTIONS_FOR_PERSONALISATION || signalled.none { it.weight > 0 }) {
        return popularFallback(pool, input.excludeIds)
    }

    val own = signalled.take(INTERACTION_DETAIL_FETCH_LIMIT).map { input.interactionRefs[it.id] ?: MovieRef(it.id) }
    val ownFeatures = input.loadFeatures(own, INTERACTION_DETAIL_FETCH_LIMIT)
    val prefs = buildPreferences(signalled, ownFeatures)

    // Step 2: shortlist from the catalogue listing alone (its genres). Part by overall genre match; the rest round-robin
    // over the profile's own movies, so every taste in the list gets candidates, not only the biggest cluster.
    class PreScored(val candidate: Candidate, val pre: Double)
    val preScored = pool
        .map { candidate ->
            val genreOnly = Features(genres = normaliseGenres(candidate.genres))
            PreScored(candidate, scoreCandidate(genreOnly, prefs)?.score ?: Double.NEGATIVE_INFINITY)
        }
        .sortedWith(compareByDescending<PreScored> { it.pre }.thenComparator { a, b -> byRatingThenId.compare(a.candidate, b.candidate) })
    val picked = HashSet<String>()
    val shortlist = ArrayList<Candidate>()
    fun take(candidate: Candidate) {
        if (candidate.id in picked || shortlist.size >= CANDIDATE_DETAIL_FETCH_LIMIT) return
        picked += candidate.id
        shortlist += candidate
    }
    preScored.take(SHORTLIST_BY_SCORE).forEach { take(it.candidate) }
    // Every genre the profile likes gets candidates to score, in proportion to its share of the profile's taste, so a minority taste
    // (say 25% of the profile) isn't left with nothing to be picked from just because the biggest taste scores higher on genre alone.
    val shares = tasteShares(signalled, ownFeatures, prefs)
    for ((genre, share) in shares.entries.sortedByDescending { it.value }) {
        val quota = maxOf(SHORTLIST_GENRE_MIN, ceil(share * SHORTLIST_GENRE_BUDGET).toInt())
        preScored
            .filter { primaryGenre(normaliseGenres(it.candidate.genres), prefs) == genre }
            .take(quota)
            .forEach { take(it.candidate) }
    }
    val owners = signalled.filter { it.weight > 0 }.take(SHORTLIST_SOURCE_MOVIES)
    val perOwner: List<List<Candidate>> = owners.map { owner ->
        val ownGenres = (ownFeatures[owner.id]?.genres ?: emptyList()).toSet()
        fun overlap(c: Candidate): Double {
            val genres = normaliseGenres(c.genres)
            val shared = genres.count { it in ownGenres }
            return if (shared == 0) 0.0 else shared.toDouble() / (ownGenres.size + genres.size - shared)
        }
        pool
            .map { it to overlap(it) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Candidate, Double>> { it.second }.thenComparator { a, b -> byRatingThenId.compare(a.first, b.first) })
            .map { it.first }
    }
    var round = 0
    while (shortlist.size < CANDIDATE_DETAIL_FETCH_LIMIT && round < pool.size) {
        var progressed = false
        for (list in perOwner) {
            val next = list.firstOrNull { it.id !in picked }
            if (next != null) {
                take(next)
                progressed = true
            }
        }
        if (!progressed) break
        round++
    }
    // Anything still free is filled by overall match.
    preScored.forEach { take(it.candidate) }

    // Step 3: full features for the shortlist.
    val details = input.loadFeatures(shortlist.map { MovieRef(it.id, it.providerId) }, CANDIDATE_DETAIL_FETCH_LIMIT)

    // A candidate that is itself one of the profile's movies (saved or liked but not finished) is judged against the
    // profile WITHOUT that movie, so it can neither boost its own score nor be named as its own reason.
    val ownIds = signalled.map { it.id }.toSet()
    val withoutSelf = HashMap<String, Preferences>()
    fun prefsFor(id: String): Preferences {
        if (id !in ownIds) return prefs
        return withoutSelf.getOrPut(id) { buildPreferences(signalled.filter { it.id != id }, ownFeatures) }
    }
    val scored = ArrayList<ScoredCandidate>()
    for (candidate in shortlist) {
        val fetched = details[candidate.id]
        val features = Features(
            genres = if (fetched != null && fetched.genres.isNotEmpty()) fetched.genres else normaliseGenres(candidate.genres),
            directors = fetched?.directors ?: emptyList(),
            cast = fetched?.cast ?: emptyList()
        )
        val candidatePrefs = prefsFor(candidate.id)
        val result = scoreCandidate(features, candidatePrefs) ?: continue
        scored += ScoredCandidate(
            candidate.id, candidate, result.score,
            if (result.score > 0) rankSources(features, candidatePrefs) else emptyList(),
            primaryGenre(features.genres, prefs)
        )
    }
    if (scored.isEmpty()) return popularFallback(pool, input.excludeIds)

    // Step 4. The row is composed from the scored candidates: the strongest stay, the other places follow the profile's genre split,
    // and (with a seed) are drawn so a refresh changes most of them. What is not in the row follows in score order, so the source cap
    // can still pull in a next-best pick when it has to skip one. Without a seed the order is fully deterministic.
    val sorted = scored.sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenComparator { a, b -> byRatingThenId.compare(a.candidate, b.candidate) })
    val composed = compose(sorted, MAX_RESULTS, shares = shares, seed = input.seed, previous = input.previousShown)
    val composedIds = composed.map { it.id }.toHashSet()
    val ordered = composed + sorted.filter { it.id !in composedIds }
    val items = diversify(ordered, MAX_RESULTS).map { (pick, source) -> Picked(pick.id, pick.score, source?.let(::reasonFor)) }
    return EngineResult.Personal(items)
}
