package com.mangotv.app.data.recommend

import com.mangotv.app.data.recommend.RecommendConfig.ROTATION_ANCHORS
import com.mangotv.app.data.recommend.RecommendConfig.ROTATION_FLOOR
import com.mangotv.app.data.recommend.RecommendConfig.ROTATION_REPEAT_WEIGHT
import kotlin.math.pow

/**
 * Small seeded generator (mulberry32): the same seed always gives the same sequence, so one launch is stable and the step is testable.
 * The same arithmetic as the web app's `seededRandom`, so a seed gives the same numbers on both.
 */
fun seededRandom(seed: Int): () -> Double {
    var a = seed
    return {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

interface ComposePick {
    val id: String
    val score: Double

    /** The genre the pick stands for in the profile (see Taste.kt); null when it matches none of the profile's genres. */
    val genre: String?
}

/**
 * Composition step: applied after scoring and before the diversity step; it only CHOOSES and ORDERS picks that were genuinely scored,
 * it never scores. Ported from the web app's `domain/recommend/rotation.ts`, step for step:
 *
 *  1. The [anchors] best-scoring picks overall are always kept: a strong recommendation is there on every refresh.
 *  2. Every other place is given to a genre by the profile's own taste split (Sainte-Lague proportional apportionment: a place goes to
 *     the genre with the highest share / (2 x places it already holds + 1)), so a profile that is 75% horror and 25% other gets about
 *     three quarters horror and a quarter the other genres it likes, instead of the biggest cluster filling every place.
 *  3. Within a genre, the pick is drawn with a seeded weighted draw over that genre's other scored picks (score > 0 and at least
 *     [floor] of that genre's best), higher scores likelier, and a pick shown in the previous row [repeatWeight] times as likely, so a
 *     refresh swaps most of the row. With no [seed], the genre's best-scoring pick is taken.
 *  4. If a genre runs out, its place goes to the next genre; if all run out, the best remaining scores fill the row.
 *
 * Scores and reasons are never touched and nothing that wasn't scored is added.
 */
fun <T : ComposePick> compose(
    sortedByScore: List<T>,
    limit: Int,
    shares: Map<String, Double> = emptyMap(),
    anchors: Int = ROTATION_ANCHORS,
    floor: Double = ROTATION_FLOOR,
    repeatWeight: Double = ROTATION_REPEAT_WEIGHT,
    previous: Set<String> = emptySet(),
    seed: Int? = null
): List<T> {
    if (sortedByScore.size <= minOf(anchors, limit)) return sortedByScore.take(limit)
    val random = seed?.let { seededRandom(it) }

    val row = ArrayList<T>(sortedByScore.take(minOf(anchors, limit)))
    val used = row.map { it.id }.toHashSet()
    val count = HashMap<String, Int>()
    for (p in row) p.genre?.let { count[it] = (count[it] ?: 0) + 1 }

    // Each genre's remaining scored picks, best first (null-genre picks only ever fill at the end).
    val buckets = LinkedHashMap<String, MutableList<T>>()
    for (p in sortedByScore) {
        val genre = p.genre
        if (p.id in used || genre == null || p.score <= 0) continue
        buckets.getOrPut(genre) { ArrayList() } += p
    }

    // The floor is measured against each genre's best scored pick overall, not against whatever is left of it.
    val genreBest = HashMap<String, Double>()
    for (p in sortedByScore) {
        val genre = p.genre
        if (genre != null && p.score > 0 && genre !in genreBest) genreBest[genre] = p.score
    }

    fun drawFrom(bucket: List<T>): T? {
        val first = bucket.firstOrNull() ?: return null
        val best = genreBest[first.genre ?: ""] ?: first.score
        val eligible = bucket.filter { it.score >= best * floor }
        if (eligible.isEmpty()) return null
        if (random == null) return eligible[0]
        // Weighted draw of one: Efraimidis-Spirakis key = u^(1/weight), largest wins.
        var winner = eligible[0]
        var winnerKey = -1.0
        for (p in eligible) {
            val weight = maxOf(1e-6, (p.score / best).pow(2) * (if (p.id in previous) repeatWeight else 1.0))
            val key = random().pow(1.0 / weight)
            if (key > winnerKey) {
                winner = p
                winnerKey = key
            }
        }
        return winner
    }

    while (row.size < limit) {
        // Genres in order of Sainte-Lague priority, share / (2 x places already held + 1): a proportional split of the places that
        // also mixes the genres through the row (a minority taste gets its places early, not after the biggest taste has used up
        // the first half).
        val wanted = buckets.entries
            .filter { it.value.isNotEmpty() }
            .map { it.key to ((shares[it.key] ?: 0.0) / (2 * (count[it.key] ?: 0) + 1)) }
            .sortedWith(
                compareByDescending<Pair<String, Double>> { it.second }
                    .thenByDescending { shares[it.first] ?: 0.0 }
                    .thenBy { it.first }
            )
        var found: T? = null
        for ((genre, _) in wanted) {
            found = drawFrom(buckets.getValue(genre))
            if (found != null) break
        }
        val pick = found ?: break
        row += pick
        used += pick.id
        pick.genre?.let { genre ->
            count[genre] = (count[genre] ?: 0) + 1
            buckets[genre] = buckets.getValue(genre).filter { it.id != pick.id }.toMutableList()
        }
    }
    // Nothing left that clears the bar: the best remaining scores fill the row.
    for (p in sortedByScore) {
        if (row.size >= limit) break
        if (p.id !in used) {
            row += p
            used += p.id
        }
    }
    return row
}
