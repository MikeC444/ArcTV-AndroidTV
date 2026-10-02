package com.mangotv.app.data.recommend

typealias Vector = Map<String, Double>

/** A movie of the profile's that pushed a feature, for honest explanations. */
data class Contribution(val id: String, val title: String, val kind: SignalKind, val amount: Double)

class Preferences(
    val genre: MutableMap<String, Double> = LinkedHashMap(),
    val director: MutableMap<String, Double> = LinkedHashMap(),
    val cast: MutableMap<String, Double> = LinkedHashMap(),
    /** Which movies pushed each feature ("genre|drama" -> [...]). */
    val contributions: MutableMap<String, MutableList<Contribution>> = LinkedHashMap()
) {
    fun vector(category: Category): MutableMap<String, Double> = when (category) {
        Category.GENRE -> genre
        Category.DIRECTOR -> director
        Category.CAST -> cast
    }

    /** True when the profile has any nonzero preference in this category. */
    fun has(category: Category): Boolean = vector(category).values.any { it != 0.0 }
}

fun contributionKey(category: Category, feature: String): String = "${category.name.lowercase()}|$feature"

/**
 * One preference map per category. A movie's signal weight is split equally among its unique features within each
 * category, so a film with a long cast list doesn't count for more than one with a short one. Movies without metadata
 * for a category add nothing there.
 */
fun buildPreferences(interactions: List<Interaction>, featuresById: Map<String, Features?>): Preferences {
    val prefs = Preferences()
    for (interaction in interactions) {
        val features = featuresById[interaction.id] ?: continue
        for (category in CATEGORIES) {
            val list = features.list(category)
            if (list.isEmpty()) continue
            val share = interaction.weight / list.size
            val vector = prefs.vector(category)
            for (feature in list) {
                vector[feature] = (vector[feature] ?: 0.0) + share
                prefs.contributions.getOrPut(contributionKey(category, feature)) { mutableListOf() }
                    .add(Contribution(interaction.id, interaction.title, interaction.kind, share))
            }
        }
    }
    return prefs
}
