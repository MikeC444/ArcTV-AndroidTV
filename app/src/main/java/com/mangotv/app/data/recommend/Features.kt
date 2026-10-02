package com.mangotv.app.data.recommend

import com.mangotv.app.data.model.Content

enum class Category { GENRE, DIRECTOR, CAST }

/** What the addon tells us about a movie, normalised: lower-cased, trimmed, unique. Any list may be empty (metadata is often partial). */
data class Features(
    val genres: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList()
) {
    fun list(category: Category): List<String> = when (category) {
        Category.GENRE -> genres
        Category.DIRECTOR -> directors
        Category.CAST -> cast
    }

    val hasAny: Boolean get() = genres.isNotEmpty() || directors.isNotEmpty() || cast.isNotEmpty()
}

val CATEGORIES: List<Category> = listOf(Category.GENRE, Category.DIRECTOR, Category.CAST)

fun categoryWeight(category: Category): Double = when (category) {
    Category.GENRE -> RecommendConfig.CATEGORY_WEIGHT_GENRE
    Category.DIRECTOR -> RecommendConfig.CATEGORY_WEIGHT_DIRECTOR
    Category.CAST -> RecommendConfig.CATEGORY_WEIGHT_CAST
}

private val WHITESPACE = Regex("\\s+")

fun normaliseName(name: String): String = name.trim().replace(WHITESPACE, " ").lowercase()

private fun unique(names: List<String?>): List<String> {
    val out = LinkedHashSet<String>()
    for (name in names) {
        if (name == null) continue
        val n = normaliseName(name)
        if (n.isNotEmpty()) out += n
    }
    return out.toList()
}

/** Features from a title's full details (what the addon's meta call returns): genres, director(s) and cast names. */
fun featuresFromContent(content: Content): Features = Features(
    genres = unique(content.genres.map { it.name }),
    // The addon sends directors as one string, "A, B".
    directors = unique(content.director?.split(',', '/', '&').orEmpty()),
    cast = unique(content.cast.map { it.name }).take(RecommendConfig.CAST_FEATURE_LIMIT)
)

/** Features from raw lists (the shape the Stremio meta uses), kept separate so the normalisation is testable on its own. */
fun featuresFromNames(genres: List<String?>, directors: List<String?>, cast: List<String?>): Features = Features(
    genres = unique(genres),
    directors = unique(directors),
    cast = unique(cast).take(RecommendConfig.CAST_FEATURE_LIMIT)
)
