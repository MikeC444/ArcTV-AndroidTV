package com.mangotv.app.ui.home

import com.mangotv.app.data.model.Content

private val METAHUB_BACKGROUND = Regex("(/background/)(?:small|medium)(/)", RegexOption.IGNORE_CASE)
private val TMDB_BACKDROP = Regex("(/t/p/)w(?:300|500|780|1280)(/)", RegexOption.IGNORE_CASE)

/**
 * The Home hero is a full-screen picture, so a "medium" background looks soft on a big TV. Addons usually serve the
 * same image in several sizes and say which in the address; this asks for the biggest where the pattern is recognised:
 *
 *   Metahub (Cinemeta)  .../background/medium/tt123/img  ->  .../background/large/tt123/img
 *   TMDB                .../t/p/w780/abc.jpg             ->  .../t/p/original/abc.jpg
 *
 * Anything else comes back unchanged, and a blank address gives null. The caller falls back to the original address
 * if the bigger one fails to load. Same rule as the web app.
 */
fun sharpBackdrop(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return url
        .replace(METAHUB_BACKGROUND, "$1large$2")
        .replace(TMDB_BACKDROP, "$1original$2")
}

/** One picture the hero will show: the [url] to ask for (the sharper address) and the [fallback] if it fails. */
data class HeroImage(val url: String, val fallback: String)

/** The pictures the hero will show, in the order it shows them: each title's backdrop, then its logo, once each. */
fun heroImages(items: List<Content>): List<HeroImage> {
    val seen = HashSet<String>()
    val out = ArrayList<HeroImage>()
    for (item in items) {
        item.backdropUrl?.takeIf { it.isNotBlank() }?.let { original ->
            val image = HeroImage(url = sharpBackdrop(original) ?: original, fallback = original)
            if (seen.add(image.url)) out += image
        }
        item.logoUrl?.takeIf { it.isNotBlank() }?.let { logo ->
            if (seen.add(logo)) out += HeroImage(url = logo, fallback = logo)
        }
    }
    return out
}
