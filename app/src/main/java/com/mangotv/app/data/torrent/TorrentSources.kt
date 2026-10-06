package com.mangotv.app.data.torrent

import java.net.URI
import java.util.Locale

/** Where a torrent for a source comes from. */
sealed interface TorrentRef {
    /** A magnet link, or a bare info hash (with the trackers an addon listed) turned into one. */
    data class Magnet(val link: MagnetLink) : TorrentRef

    /** A .torrent file to fetch ([url] is http(s)) or to read from the device ([url] is a file or content URI the app itself saved). */
    data class TorrentFile(val url: String) : TorrentRef

    /** A source that says it is a torrent but does not hold a usable one; [message] is for the person. */
    data class Invalid(val message: String) : TorrentRef
}

/**
 * Whether a source is a torrent to stream, and which. Everything else (a plain http(s) link, HLS, DASH, a debrid link) returns null and
 * keeps playing exactly as before: an `http(s)` url that is not a .torrent file always wins over an info hash the same source also lists.
 *
 * [allowLocalFiles] is true only for sources the person added themselves (their own .torrent file); an addon's `file:` or `content:` link is
 * never followed.
 */
fun torrentRefOf(
    url: String?,
    infoHash: String?,
    trackers: List<String> = emptyList(),
    displayName: String? = null,
    allowLocalFiles: Boolean = false
): TorrentRef? {
    val link = url?.trim().orEmpty()
    if (link.isNotEmpty()) {
        val scheme = link.substringBefore(':', "").lowercase(Locale.ROOT)
        return when {
            scheme == "magnet" -> MagnetLink.parse(link)?.let { TorrentRef.Magnet(it) }
                ?: TorrentRef.Invalid("That magnet link isn't valid.")
            (scheme == "http" || scheme == "https") && pathEndsWithTorrent(link) -> TorrentRef.TorrentFile(link)
            (scheme == "file" || scheme == "content") && allowLocalFiles -> TorrentRef.TorrentFile(link)
            else -> null
        }
    }
    val hash = MagnetLink.normalizeInfoHash(infoHash) ?: return null
    return TorrentRef.Magnet(MagnetLink(hash, displayName?.takeIf { it.isNotBlank() }?.take(200), trackers.filter { it.isNotBlank() }.take(32)))
}

private fun pathEndsWithTorrent(url: String): Boolean = try {
    URI(url).path.orEmpty().lowercase(Locale.ROOT).endsWith(".torrent")
} catch (_: Exception) {
    url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT).endsWith(".torrent")
}

/** The tracker addresses in a Stremio stream's `sources` list (entries look like `tracker:udp://host:port`; `dht:` entries are not trackers). */
fun trackersFromStremioSources(sources: List<String>?): List<String> =
    sources.orEmpty().mapNotNull { entry ->
        val value = entry.trim().removePrefix("tracker:").takeIf { entry.trim().startsWith("tracker:") || "://" in entry }
        value?.takeIf { it.substringBefore("://", "").lowercase(Locale.ROOT) in setOf("udp", "http", "https", "ws", "wss") }
    }.distinct()

/** What a person typed or pasted as "their own" torrent: a magnet link or the address of a .torrent file. */
sealed interface UserTorrentInput {
    data class Magnet(val link: MagnetLink) : UserTorrentInput
    data class TorrentUrl(val url: String) : UserTorrentInput
    data class Rejected(val message: String) : UserTorrentInput
}

fun parseUserTorrentInput(text: String?): UserTorrentInput {
    val value = text?.trim().orEmpty()
    if (value.isEmpty()) return UserTorrentInput.Rejected("Paste a magnet link or the address of a .torrent file.")
    if (value.startsWith("magnet:", ignoreCase = true)) {
        return MagnetLink.parse(value)?.let { UserTorrentInput.Magnet(it) }
            ?: UserTorrentInput.Rejected("That magnet link isn't valid. It should look like magnet:?xt=urn:btih:…")
    }
    // A bare info hash is accepted as a convenience.
    MagnetLink.normalizeInfoHash(value)?.let { return UserTorrentInput.Magnet(MagnetLink(it, null, emptyList())) }
    val scheme = value.substringBefore(':', "").lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https") {
        return if (pathEndsWithTorrent(value)) UserTorrentInput.TorrentUrl(value)
        else UserTorrentInput.Rejected("That address doesn't end in .torrent. Paste a magnet link or a link to a .torrent file.")
    }
    return UserTorrentInput.Rejected("That doesn't look like a magnet link or a .torrent address.")
}

/** True for the address the torrent engine serves a stream on (loopback only), as opposed to any ordinary link. */
fun isLocalTorrentUrl(url: String?): Boolean = url?.startsWith("http://127.0.0.1:") == true
