package com.mangotv.app.data.recommend

enum class Feedback(val wire: String) {
    LIKE("like"),
    DISLIKE("dislike");

    companion object {
        fun fromWire(value: String): Feedback? = values().firstOrNull { it.wire == value }
    }
}

enum class SignalKind { LIKE, DISLIKE, COMPLETED, WATCHLIST }

/** What is currently stored about one movie for one profile. Built from the stores each time, never accumulated: edits and removals therefore just work. */
data class InteractionInput(
    val id: String,
    val title: String,
    val feedback: Feedback? = null,
    /** Finished, by the app's existing completion rule (My List entry with watched = true). */
    val completed: Boolean = false,
    val inWatchlist: Boolean = false
)

data class Interaction(val id: String, val title: String, val kind: SignalKind, val weight: Double)

/**
 * The single strongest applicable signal for a movie, or null when it carries none. Explicit feedback comes first, so a
 * disliked movie stays negative even if it was finished or saved. Opening a page, starting playback, failures and
 * interrupted sessions are not inputs here at all.
 */
fun interactionOf(input: InteractionInput): Interaction? = when {
    input.feedback == Feedback.LIKE -> Interaction(input.id, input.title, SignalKind.LIKE, RecommendConfig.WEIGHT_LIKE)
    input.feedback == Feedback.DISLIKE -> Interaction(input.id, input.title, SignalKind.DISLIKE, RecommendConfig.WEIGHT_DISLIKE)
    input.completed -> Interaction(input.id, input.title, SignalKind.COMPLETED, RecommendConfig.WEIGHT_COMPLETED)
    input.inWatchlist -> Interaction(input.id, input.title, SignalKind.WATCHLIST, RecommendConfig.WEIGHT_WATCHLIST)
    else -> null
}

/** Stored facts -> one Interaction per movie, in a stable order (strongest first, then id). */
fun collectInteractions(inputs: List<InteractionInput>): List<Interaction> {
    val byId = LinkedHashMap<String, InteractionInput>()
    for (input in inputs) {
        val previous = byId[input.id]
        byId[input.id] = if (previous == null) {
            input
        } else {
            input.copy(
                feedback = input.feedback ?: previous.feedback,
                completed = input.completed || previous.completed,
                inWatchlist = input.inWatchlist || previous.inWatchlist
            )
        }
    }
    return byId.values.mapNotNull(::interactionOf)
        .sortedWith(compareByDescending<Interaction> { kotlin.math.abs(it.weight) }.thenBy { it.id })
}

/** A short fingerprint of the preference data: it changes exactly when a recommendation could. Used to invalidate cached results. */
fun signatureOf(interactions: List<Interaction>): String =
    interactions.map { "${it.id}:${it.kind.name.lowercase()}" }.sorted().joinToString("|")
