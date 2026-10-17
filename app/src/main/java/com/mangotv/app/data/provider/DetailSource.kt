package com.mangotv.app.data.provider

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType

/** Stremio's own Cinemeta addon: it knows every title that has an IMDb id. */
const val CINEMETA_PROVIDER_ID = "com.linvo.cinemeta"

private val IMDB_ID = Regex("^tt\\d+$")

/**
 * Which installed addons to ask for a title's details, in order: Cinemeta first while it is installed and turned on (for a title with an IMDb id,
 * which is all it knows), then the addon the title came from, then every other installed addon in the person's own order. Only addons that are
 * turned on are in [installedIds]. A catalogue-only addon (Streaming Catalogs) lists titles but has no details of its own, so the title has to be
 * looked up elsewhere. Same rule as the web app (`domain/detailSource.ts`).
 */
fun detailOrder(installedIds: List<String>, ownerId: String, contentId: String, metaId: String = CINEMETA_PROVIDER_ID): List<String> {
    val order = mutableListOf<String>()
    if (IMDB_ID.matches(contentId) && metaId in installedIds) order += metaId
    if (ownerId in installedIds && ownerId !in order) order += ownerId
    for (id in installedIds) if (id !in order) order += id
    return order
}

/**
 * A title's details from the first addon in [detailOrder] that has them (a failure or "don't know it" moves on to the next). The result keeps
 * [ownerId] as its addon, so Continue Watching, the last source and My List stay filed under the addon the title came from; only the details
 * come from elsewhere. Null when none of them has the title.
 */
suspend fun loadDetails(providers: List<CatalogProvider>, ownerId: String, type: ContentType, id: String): Content? {
    for (providerId in detailOrder(providers.map { it.id }, ownerId, id)) {
        val provider = providers.find { it.id == providerId } ?: continue
        val detail = runCatching { provider.getDetails(type, id) }.getOrNull()
        if (detail != null) return detail.copy(providerId = ownerId)
    }
    return null
}
