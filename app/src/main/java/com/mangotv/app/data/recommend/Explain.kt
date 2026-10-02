package com.mangotv.app.data.recommend

private fun verb(kind: SignalKind): String = when (kind) {
    SignalKind.LIKE -> "liked"
    SignalKind.COMPLETED -> "watched"
    SignalKind.WATCHLIST -> "saved"
    SignalKind.DISLIKE -> ""
}

private fun strength(kind: SignalKind): Int = when (kind) {
    SignalKind.LIKE -> 3
    SignalKind.COMPLETED -> 2
    SignalKind.WATCHLIST -> 1
    SignalKind.DISLIKE -> 0
}

/** One of the profile's movies and how much of a candidate's score it accounts for. */
data class Source(
    val id: String,
    val title: String,
    val kind: SignalKind,
    /** Weighted sum over the candidate's genres / directors / cast of what this movie added to each feature. */
    var total: Double = 0.0,
    /** The part of [total] that came through directors. */
    var director: Double = 0.0
)

/**
 * Every movie the profile has a positive signal for, credited with the part of this candidate's score it contributed
 * (weighted over the candidate's genres, directors and cast), biggest first; ties go to the stronger signal (like,
 * finished, saved), then id. Disliked movies are never sources.
 */
fun rankSources(features: Features?, prefs: Preferences): List<Source> {
    if (features == null) return emptyList()
    val credit = LinkedHashMap<String, Source>()
    for (category in CATEGORIES) {
        for (feature in features.list(category)) {
            for (c in prefs.contributions[contributionKey(category, feature)].orEmpty()) {
                if (c.amount <= 0 || c.kind == SignalKind.DISLIKE) continue
                val entry = credit.getOrPut(c.id) { Source(c.id, c.title, c.kind) }
                val part = categoryWeight(category) * c.amount
                entry.total += part
                if (category == Category.DIRECTOR) entry.director += part
            }
        }
    }
    return credit.values.sortedWith(
        compareByDescending<Source> { it.total }.thenByDescending { strength(it.kind) }.thenBy { it.id }
    )
}

/** The reason line for a source: "Because you liked X", or "More from directors you enjoy" when it mostly matches through a shared director. */
fun reasonFor(source: Source): String =
    if (source.director > source.total / 2) "More from directors you enjoy" else "Because you ${verb(source.kind)} ${source.title}"

/** The reason for the single best source, or null when nothing positive matched (no reason is ever invented). */
fun explainCandidate(features: Features?, prefs: Preferences): String? = rankSources(features, prefs).firstOrNull()?.let(::reasonFor)
