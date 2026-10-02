package com.mangotv.app.data.recommend

/**
 * The genre a movie "stands for" in this profile: of its own genres, the one the profile likes most overall (ties: alphabetical).
 * Null when none of its genres has a positive weight in the profile. Used on both the profile's own movies and on candidates, so the
 * two are bucketed the same way. Ported from the web app's `domain/recommend/taste.ts`.
 */
fun primaryGenre(genres: List<String>, prefs: Preferences): String? {
    var best: String? = null
    var bestWeight = 0.0
    for (genre in genres.toSet().sorted()) {
        val weight = prefs.genre[genre] ?: 0.0
        if (weight > bestWeight) {
            best = genre
            bestWeight = weight
        }
    }
    return best
}

/**
 * How the profile's taste splits across genres: each of its own movies with a positive signal counts, by its signal weight, towards
 * that movie's primary genre. So a profile that is three quarters horror (by signal) gets a share of about 0.75 for horror and the
 * rest spread over what else it likes. Shares sum to 1; empty when there is nothing positive to go on.
 */
fun tasteShares(signalled: List<Interaction>, ownFeatures: Map<String, Features?>, prefs: Preferences): Map<String, Double> {
    val raw = LinkedHashMap<String, Double>()
    var total = 0.0
    for (interaction in signalled) {
        if (interaction.weight <= 0) continue
        val genre = primaryGenre(ownFeatures[interaction.id]?.genres ?: emptyList(), prefs) ?: continue
        raw[genre] = (raw[genre] ?: 0.0) + interaction.weight
        total += interaction.weight
    }
    val shares = LinkedHashMap<String, Double>()
    if (total <= 0) return shares
    for ((genre, weight) in raw) shares[genre] = weight / total
    return shares
}
