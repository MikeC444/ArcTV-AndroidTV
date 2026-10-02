package com.mangotv.app.data.recommend

import kotlin.math.sqrt

/** Cosine similarity of two sparse vectors; negative preferences stay negative. null when either vector has no magnitude. */
fun cosine(a: Vector, b: Vector): Double? {
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for ((key, value) in a) {
        na += value * value
        val other = b[key]
        if (other != null) dot += value * other
    }
    for (value in b.values) nb += value * value
    if (na == 0.0 || nb == 0.0) return null
    return dot / (sqrt(na) * sqrt(nb))
}

data class Score(
    /** Internal ranking value in [-1, 1]. Not a probability and not a rating. */
    val score: Double,
    /** The per-category cosine similarities that went into it (only the categories that could be compared). */
    val categories: Map<Category, Double>
)

/** A candidate as a 0/1 feature vector per category (each unique feature counts once). */
private fun candidateVector(features: Features, category: Category): Vector = features.list(category).associateWith { 1.0 }

/**
 * Weighted mean of the category similarities. A category with no candidate metadata, or where the profile has no nonzero
 * preference, is omitted and the remaining weights are renormalised; if nothing can be compared the candidate is
 * unscored (null).
 */
fun scoreCandidate(features: Features?, prefs: Preferences): Score? {
    if (features == null) return null
    val categories = LinkedHashMap<Category, Double>()
    var weighted = 0.0
    var total = 0.0
    for (category in CATEGORIES) {
        if (features.list(category).isEmpty() || !prefs.has(category)) continue
        val similarity = cosine(candidateVector(features, category), prefs.vector(category)) ?: continue
        categories[category] = similarity
        weighted += categoryWeight(category) * similarity
        total += categoryWeight(category)
    }
    if (total == 0.0) return null
    return Score(weighted / total, categories)
}
