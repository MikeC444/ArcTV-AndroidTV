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
        if (item.type != ContentType.MOVIE) continue
        inputs[item.id] = InteractionInput(item.id, item.title, completed = item.watched, inWatchlist = true)
    }
    for ((id, entry) in feedback) {
        val base = inputs[id] ?: InteractionInput(id, entry.title)
        inputs[id] = base.copy(feedback = entry.feedback)
    }
    return inputs.values.toList()
}

/** Never recommended: finished movies, "Not for me", and anything already in Continue Watching. */
fun excludedFromPicks(list: List<SavedListItem>, feedback: Map<String, FeedbackEntry>, continueWatching: List<ContinueWatchingEntry>): Set<String> {
    val ids = HashSet<String>()
    for (item in list) if (item.watched) ids += item.id
    for ((id, entry) in feedback) if (entry.feedback == Feedback.DISLIKE) ids += id
    for (entry in continueWatching) ids += entry.contentId
    return ids
}

fun Content.toCandidate(): Candidate = Candidate(id, title, providerId, genres.map { it.name }, rating)

/** Looks a movie's features up through the addon that listed it (one bounded, cached request). */
suspend fun fetchMovieFeatures(providers: List<CatalogProvider>, ref: MovieRef): Features? {
    val provider = (ref.providerId?.let { id -> providers.firstOrNull { it.id == id } }) ?: providers.firstOrNull() ?: return null
    return provider.getDetails(ContentType.MOVIE, ref.id)?.let(::featuresFromContent)
}

/**
 * The "Picked for you" row, ready to place on Home: null when there is nothing to show. "Not for me" titles are
 * dropped at once (before any recompute) so they disappear the moment they are marked.
 */
fun pickedSection(result: EngineResult?, movies: List<Content>, feedback: Map<String, FeedbackEntry>): HomeSection? {
    if (result == null || result.items.isEmpty()) return null
    val byId = movies.associateBy { it.id }
    val personal = result is EngineResult.Personal
    val items = result.items.mapNotNull { pick ->
        val movie = byId[pick.id] ?: return@mapNotNull null
        if (feedback[movie.id]?.feedback == Feedback.DISLIKE) return@mapNotNull null
        movie.copy(recommendReason = if (personal) pick.reason else null)
    }
    if (items.isEmpty()) return null
    return HomeSection(
        id = RecommendConfig.PICKED_ROW_ID,
        title = if (personal) RecommendConfig.PICKED_ROW_TITLE else RecommendConfig.POPULAR_ROW_TITLE,
        items = items.take(RecommendConfig.MAX_RESULTS),
        style = RowStyle.STANDARD
    )
}
