package com.mangotv.app.data.streaming

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.provider.isBlockedBy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** How many titles the Top 10 row shows. */
const val TOP_TEN_COUNT = 10

/** How many candidates are looked at when some genres are blocked (the ten shown come from the first few that are left). */
const val TOP_TEN_POOL = 30

/** The service whose page opens with a Top 10 row, wide pictures and the red N (the web app does this for Netflix only). */
const val TOP_TEN_SERVICE = "netflix"

/** What the Top 10 row shows: grey posters while the list is on its way, the ten titles, or nothing (no list for this service). */
sealed interface TopTenState {
    data object Loading : TopTenState
    data class Ready(val items: List<Content>) : TopTenState
    data object None : TopTenState
}

/**
 * Series and movies taken in turn (each list is most popular first), so the ranking mixes both the way a streaming service's own chart does, without
 * repeats. Same as the web app's `rankedPool` in `TopTenRow.tsx`.
 */
fun rankedPool(movies: List<Content>, shows: List<Content>, limit: Int): List<Content> {
    val out = ArrayList<Content>()
    val seen = HashSet<String>()
    var i = 0
    while (out.size < limit && (i < movies.size || i < shows.size)) {
        for (c in listOfNotNull(shows.getOrNull(i), movies.getOrNull(i))) {
            if (out.size < limit && seen.add(c.id)) out += c
        }
        i++
    }
    return out
}

fun topTen(movies: List<Content>, shows: List<Content>): List<Content> = rankedPool(movies, shows, TOP_TEN_COUNT)

/**
 * The first [count] of [pool] (in order) that carry none of the [blocked] genres. TMDB's list has no genres, so each candidate's are looked up through
 * [genresOf] (a chunk at a time, [count] at once); a title whose genres can't be found is kept, as everywhere else a title with no genres is.
 */
suspend fun pickUnblocked(pool: List<Content>, blocked: Set<String>, genresOf: suspend (Content) -> List<Genre>?, count: Int): List<Content> {
    val out = ArrayList<Content>()
    var at = 0
    while (at < pool.size && out.size < count) {
        val chunk = pool.subList(at, minOf(at + count, pool.size))
        val genres = coroutineScope {
            chunk.map { c -> async { runCatching { genresOf(c) }.getOrNull() } }.awaitAll()
        }
        chunk.forEachIndexed { i, c ->
            if (out.size < count && !c.copy(genres = genres[i] ?: c.genres).isBlockedBy(blocked)) out += c
        }
        at += count
    }
    return out
}
