package com.mangotv.app.ui.browse

import com.mangotv.app.data.model.AddonManifest
import android.content.res.AssetManager
import com.mangotv.app.data.model.ContentType
import kotlinx.serialization.json.Json

/**
 * The genres of the Movies / TV Shows drop-down: exactly the genres Cinemeta's "top" catalogue lists for that type, in
 * its order, with no years (TV Shows has three more than Movies: Reality-TV, Talk-Show, Game-Show). Same list the web
 * app uses. Empty when the manifest has no such catalogue, which hides the drop-down.
 */
fun genreOptionsFor(manifest: AddonManifest, type: ContentType): List<String> {
    val stremioType = if (type == ContentType.TV_SHOW) "series" else "movie"
    return manifest.catalogs
        .firstOrNull { it.id == "top" && it.type == stremioType }
        ?.extra?.firstOrNull { it.name == "genre" }
        ?.options.orEmpty()
        .filterNot { option -> option.all { it.isDigit() } }
}

/** What an empty Movies / TV Shows page says, with or without a genre chosen. */
fun emptyBrowseMessage(title: String, genre: String?): String =
    if (genre != null) "No $genre ${title.lowercase()} found right now." else "Nothing to show here right now."

private val manifestJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** [genreOptionsFor] read from the Cinemeta manifest bundled with the app; empty if it can't be read, which hides the drop-down. */
fun bundledGenreOptions(assets: AssetManager, type: ContentType): List<String> = runCatching {
    val raw = assets.open("cinemeta_manifest.json").bufferedReader().use { it.readText() }
    genreOptionsFor(manifestJson.decodeFromString(AddonManifest.serializer(), raw), type)
}.getOrDefault(emptyList())
