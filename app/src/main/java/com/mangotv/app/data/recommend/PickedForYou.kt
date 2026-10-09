package com.mangotv.app.data.recommend

import com.mangotv.app.data.feedback.FeedbackEntry
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.model.RowStyle
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.SavedListItem

/** What the profile's stored data says, as engine inputs. Rebuilt from the current stores every time, so edits and removals are always reflected. */
fun interactionInputs(list: List<SavedListItem>, feedback: Map<String, FeedbackEntry>): List<InteractionInput> {
    val inputs = LinkedHashMap<String, InteractionInput>()
    for (item in list) {
        inputs[item.id] = InteractionInput(item.id, item.title, completed = item.watched, inWatchlist = true)
    }
    for ((id, entry) in feedback) {
        val base = inputs[id] ?: InteractionInput(id, entry.title)
        inputs[id] = base.copy(feedback = entry.feedback)
    }
    return inputs.values.toList()
}

/**
 * Never recommended: finished movies, anything rated Like or Not for me (a liked title is one the person already knows, so it informs
 * the picks but is never picked itself), anything already in Continue Watching, and titles taken out of the row by hand
 * ([dismissed]: kept out, but not a taste signal).
 */
fun excludedFromPicks(
    list: List<SavedListItem>,
    feedback: Map<String, FeedbackEntry>,
    continueWatching: List<ContinueWatchingEntry>,
    dismissed: Set<String> = emptySet()
): Set<String> {
    val ids = HashSet<String>()
    for (item in list) if (item.watched) ids += item.id
    ids += feedback.keys
    for (entry in continueWatching) ids += entry.contentId
    ids += dismissed
    return ids
}

fun Content.toCandidate(): Candidate = Candidate(id, title, providerId, genres.map { it.name }, rating, type)

/** Looks a movie's or TV show's features up through the addon that listed it (one bounded, cached request). */
suspend fun fetchMovieFeatures(providers: List<CatalogProvider>, ref: MovieRef): Features? {
    val provider = (ref.providerId?.let { id -> providers.firstOrNull { it.id == id } }) ?: providers.firstOrNull() ?: return null
    return provider.getDetails(ref.type ?: ContentType.MOVIE, ref.id)?.let(::featuresFromContent)
}

/**
 * The "Picked for you" row, ready to place on Home: null when there is nothing to show. Titles rated Like or Not for me, and
 * titles removed by hand, are dropped at once (before any recompute) so they disappear the moment they are marked.
 */
fun pickedSection(result: EngineResult?, movies: List<Content>, /* movies and TV shows */ feedback: Map<String, FeedbackEntry>, dismissed: Set<String> = emptySet()): HomeSection? {
    if (result == null || result.items.isEmpty()) return null
    val byId = movies.associateBy { it.id }
    val personal = result is EngineResult.Personal
    val items = result.items.mapNotNull { pick ->
        val movie = byId[pick.id] ?: return@mapNotNull null
        // A title you rate or remove disappears at once, before any recompute.
        if (movie.id in feedback || movie.id in dismissed) return@mapNotNull null
        movie.copy(recommendReason = if (personal) pick.reason else null, pickedForYou = true)
    }
    if (items.isEmpty()) return null
    return HomeSection(
        id = RecommendConfig.PICKED_ROW_ID,
        title = if (personal) RecommendConfig.PICKED_ROW_TITLE else RecommendConfig.POPULAR_ROW_TITLE,
        items = items.take(RecommendConfig.MAX_RESULTS),
        style = RowStyle.STANDARD
    )
}
