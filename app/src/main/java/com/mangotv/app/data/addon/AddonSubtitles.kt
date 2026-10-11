package com.mangotv.app.data.addon

import com.mangotv.app.data.model.AddonManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/** The Stremio protocol's answer to `/subtitles/{type}/{id}.json`: files to load next to the video. */
@Serializable
data class StremioSubtitlesResponse(
    val subtitles: List<StremioSubtitle> = emptyList()
)

@Serializable
data class StremioSubtitle(
    val id: String? = null,
    val url: String? = null,
    // ISO 639-2 ("eng") in the protocol, though addons also send ISO 639-1 ("en") or a name.
    val lang: String? = null
)

/** A subtitle file an addon offers for the video being played. */
data class AddonSubtitle(
    val addonName: String,
    /** The language as the addon wrote it ("eng"). */
    val languageCode: String,
    /** The language as a person reads it ("English"). */
    val languageName: String,
    val url: String
)

/**
 * Whether this manifest offers subtitles for a title of [type] ("movie" or "series") with this [id]. Unlike streams, an addon that
 * doesn't list "subtitles" among its resources isn't asked, so the catalog addons never get a request they can't answer. A resource can
 * be a plain name or an object that narrows it to some types and id prefixes ("tt" for IMDb ids); the manifest's own types and id
 * prefixes apply where the resource says nothing.
 */
fun AddonManifest.offersSubtitles(type: String, id: String): Boolean {
    for (element in resources) {
        val name: String?
        var types: List<String> = emptyList()
        var prefixes: List<String> = emptyList()
        when (element) {
            is JsonPrimitive -> name = element.contentOrNull
            is JsonObject -> {
                name = (element["name"] as? JsonPrimitive)?.contentOrNull
                types = element.strings("types")
                prefixes = element.strings("idPrefixes")
            }
            else -> name = null
        }
        if (name != "subtitles") continue
        val allowedTypes = types.ifEmpty { this.types }
        val allowedPrefixes = prefixes.ifEmpty { idPrefixes }
        if (allowedTypes.isNotEmpty() && type !in allowedTypes) continue
        if (allowedPrefixes.isNotEmpty() && allowedPrefixes.none { id.startsWith(it) }) continue
        return true
    }
    return false
}

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

private val OLD_ISO3 = mapOf(
    "fre" to "fr", "ger" to "de", "dut" to "nl", "chi" to "zh", "cze" to "cs", "gre" to "el", "ice" to "is", "per" to "fa",
    "rum" to "ro", "slo" to "sk", "tib" to "bo", "wel" to "cy", "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my",
    "geo" to "ka", "mac" to "mk", "mao" to "mi", "may" to "ms"
)

private val ISO3_TO_ISO1: Map<String, String> by lazy {
    val map = mutableMapOf<String, String>()
    for (two in Locale.getISOLanguages()) {
        runCatching { map[Locale(two).isO3Language] = two }
    }
    map.putAll(OLD_ISO3)
    map
}

/**
 * The two-letter language code for whatever an addon sent: "eng", "en", "en-US", "pt-BR", "English" all give a code a person's language
 * preference ("en") can be compared with; null when it can't be told.
 */
fun languageKey(raw: String?): String? {
    val text = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (text.isEmpty()) return null
    val base = text.substringBefore('-').substringBefore('_')
    if (base.length == 2) return base
    ISO3_TO_ISO1[base]?.let { return it }
    // A written-out name ("english").
    return Locale.getISOLanguages().firstOrNull { Locale(it).getDisplayLanguage(Locale.ENGLISH).equals(text, ignoreCase = true) }
}

/** "English" for "eng"; the code in capitals when the language isn't known, so the list never shows a blank. */
fun languageDisplayName(raw: String?): String {
    val key = languageKey(raw)
    val name = key?.let { Locale(it).getDisplayLanguage(Locale.ENGLISH) }
    return if (!name.isNullOrBlank() && !name.equals(key, ignoreCase = true)) name else raw?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null } ?: "Unknown"
}

/** Turns one addon's answer into list entries: only files with a web address, one per address, each with a readable language. */
fun List<StremioSubtitle>.toAddonSubtitles(addonName: String): List<AddonSubtitle> =
    mapNotNull { s ->
        val url = s.url?.trim().orEmpty()
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return@mapNotNull null
        AddonSubtitle(addonName = addonName, languageCode = s.lang?.trim().orEmpty(), languageName = languageDisplayName(s.lang), url = url)
    }.distinctBy { it.url }

/**
 * Puts the person's preferred language first, then the rest by language name (keeping each addon's own order within one language), and
 * keeps at most [perLanguage] files per language and [total] overall so the menu stays short.
 */
fun rankSubtitles(found: List<AddonSubtitle>, preferredLanguage: String?, perLanguage: Int = 5, total: Int = 60): List<AddonSubtitle> {
    val preferred = languageKey(preferredLanguage)
    val groups = found.groupBy { languageKey(it.languageCode) ?: it.languageName.lowercase(Locale.ROOT) }
    val ordered = groups.entries.sortedWith(
        compareBy<Map.Entry<String, List<AddonSubtitle>>>({ if (it.key == preferred) 0 else 1 }, { it.value.first().languageName })
    )
    return ordered.flatMap { it.value.take(perLanguage) }.take(total)
}
