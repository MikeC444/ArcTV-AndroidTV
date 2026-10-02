package com.mangotv.app.data.recommend

interface ScoredPick {
    val id: String
    val score: Double

    /** Movies of the profile's that contributed to this pick, best first (see [rankSources]). */
    val sources: List<Source>
}

data class DiversifiedPick<T : ScoredPick>(
    val pick: T,
    /** The source movie this pick is explained by (null when none contributed positively). */
    val source: Source?
)

/**
 * Diversity step -- separate from scoring and applied after it. Walks the picks best score first and gives each the
 * strongest of its contributing movies that is not yet "used up" (a movie can explain at most [maxPerSource] picks); a
 * pick whose contributors are all used up waits and only fills spare places at the end. The order is therefore still
 * score order, but one cluster of the profile's taste can't crowd out the rest, and every stated reason names a movie
 * that really contributed to that pick.
 */
fun <T : ScoredPick> diversify(
    sortedByScore: List<T>,
    limit: Int = RecommendConfig.MAX_RESULTS,
    maxPerSource: Int = RecommendConfig.MAX_PICKS_PER_SOURCE
): List<DiversifiedPick<T>> {
    val used = HashMap<String, Int>()
    val chosen = ArrayList<DiversifiedPick<T>>()
    val waiting = ArrayList<T>()
    for (pick in sortedByScore) {
        if (chosen.size >= limit) break
        if (pick.sources.isEmpty()) {
            chosen += DiversifiedPick(pick, null)
            continue
        }
        val source = pick.sources.firstOrNull { (used[it.id] ?: 0) < maxPerSource }
        if (source == null) {
            waiting += pick
            continue
        }
        used[source.id] = (used[source.id] ?: 0) + 1
        chosen += DiversifiedPick(pick, source)
    }
    for (pick in waiting) {
        if (chosen.size >= limit) break
        chosen += DiversifiedPick(pick, pick.sources.firstOrNull())
    }
    return chosen
}
