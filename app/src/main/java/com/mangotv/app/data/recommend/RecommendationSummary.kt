package com.mangotv.app.data.recommend

import com.mangotv.app.data.feedback.FeedbackEntry
import com.mangotv.app.data.provider.SavedListItem

/**
 * What the Settings > Recommendations tab says about a profile's taste: how many titles feed it and how many points each kind of title
 * adds. Counted the way the engine counts ([collectInteractions]): a rated title counts once, by its rating, and a title that is only saved
 * or finished counts for the smaller amount. The points are weights, not a score (see [RecommendConfig]).
 */
data class RecommendationSummary(
    val likes: Int,
    val dislikes: Int,
    /** Titles in My List that are finished and not rated. */
    val finished: Int,
    /** Titles in My List that are not finished and not rated. */
    val saved: Int
) {
    val likePoints: Int get() = likes * RecommendConfig.WEIGHT_LIKE.toInt()
    val dislikePoints: Int get() = dislikes * RecommendConfig.WEIGHT_DISLIKE.toInt()
    val listPoints: Int get() = finished * RecommendConfig.WEIGHT_COMPLETED.toInt() + saved * RecommendConfig.WEIGHT_WATCHLIST.toInt()

    /** Titles that give the engine a signal at all. */
    val signals: Int get() = likes + dislikes + finished + saved

    /** More than the engine looks at: only its [RecommendConfig.INTERACTION_DETAIL_FETCH_LIMIT] strongest signals shape the picks. */
    val overLimit: Boolean get() = signals > RecommendConfig.INTERACTION_DETAIL_FETCH_LIMIT

    /** Personal picks start once there are enough signals and at least one is positive; until then Home shows popular titles. */
    val ready: Boolean get() = signals >= RecommendConfig.MIN_INTERACTIONS_FOR_PERSONALISATION && likes + finished + saved > 0
}

fun summarizeRecommendations(feedback: Map<String, FeedbackEntry>, list: List<SavedListItem>): RecommendationSummary {
    val unrated = list.filter { it.id !in feedback }
    return RecommendationSummary(
        likes = feedback.values.count { it.feedback == Feedback.LIKE },
        dislikes = feedback.values.count { it.feedback == Feedback.DISLIKE },
        finished = unrated.count { it.watched },
        saved = unrated.count { !it.watched }
    )
}

/** "+20 points", "−15 points", "0 points", "+1 point". */
fun pointsLabel(points: Int): String {
    val sign = when {
        points > 0 -> "+"
        points < 0 -> "−"
        else -> ""
    }
    val n = kotlin.math.abs(points)
    return "$sign$n ${if (n == 1) "point" else "points"}"
}
