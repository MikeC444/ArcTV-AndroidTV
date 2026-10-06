package com.mangotv.app.data.torrent

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * A parsed BitTorrent magnet link. [infoHash] is always the 40-character lowercase hex form (a 32-character base32 hash in the link is
 * converted), so two spellings of the same torrent compare equal.
 */
data class MagnetLink(
    val infoHash: String,
    val displayName: String?,
    val trackers: List<String>,
    /** Peers named in the link itself (`x.pe=host:port`), tried straight away. */
    val peers: List<String> = emptyList()
) {
    /** The link rebuilt in canonical form (what is handed to the torrent engine). */
    fun toUri(): String = buildMagnetUri(infoHash, displayName, trackers, peers)

    companion object {
        private const val MAX_TRACKERS = 32
        private const val MAX_PEERS = 16
        private val PEER_ADDRESS = Regex("[A-Za-z0-9.\\-]{1,253}:[0-9]{1,5}")
        private const val MAX_TRACKER_LENGTH = 512
        private const val MAX_LINK_LENGTH = 8_192

        /** Parses [text] (surrounding whitespace allowed); null when it is not a usable v1 `magnet:?xt=urn:btih:...` link. */
        fun parse(text: String?): MagnetLink? {
            val trimmed = text?.trim().orEmpty()
            if (trimmed.length > MAX_LINK_LENGTH || !trimmed.startsWith("magnet:?", ignoreCase = true)) return null
            var hash: String? = null
            var name: String? = null
            val trackers = LinkedHashSet<String>()
            val peers = LinkedHashSet<String>()
            for (pair in trimmed.substring("magnet:?".length).split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val key = pair.substring(0, eq).lowercase(Locale.ROOT)
                val value = decode(pair.substring(eq + 1)) ?: continue
                when {
                    key == "xt" && hash == null -> hash = btihOf(value)
                    key == "dn" && name == null -> name = value.trim().takeIf { it.isNotEmpty() }?.take(300)
                    key == "x.pe" -> if (peers.size < MAX_PEERS && PEER_ADDRESS.matches(value.trim())) peers += value.trim()
                    key == "tr" || key.startsWith("tr.") -> {
                        val tracker = value.trim()
                        if (tracker.length <= MAX_TRACKER_LENGTH && isTrackerUrl(tracker) && trackers.size < MAX_TRACKERS) trackers += tracker
                    }
                }
            }
            return MagnetLink(infoHash = hash ?: return null, displayName = name, trackers = trackers.toList(), peers = peers.toList())
        }

        /** A bare 40-character hex or 32-character base32 info hash (what Stremio addons send as `infoHash`), or null. */
        fun normalizeInfoHash(raw: String?): String? = raw?.trim()?.let { hexOrBase32ToHex(it) }

        private fun btihOf(xt: String): String? {
            val prefix = "urn:btih:"
            if (!xt.startsWith(prefix, ignoreCase = true)) return null
            return hexOrBase32ToHex(xt.substring(prefix.length).trim())
        }

        private fun hexOrBase32ToHex(value: String): String? = when {
            value.length == 40 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> value.lowercase(Locale.ROOT)
            value.length == 32 -> base32ToHex(value)
            else -> null
        }

        private fun base32ToHex(value: String): String? {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = 0
            var bitCount = 0
            val out = StringBuilder(40)
            for (ch in value.uppercase(Locale.ROOT)) {
                val v = alphabet.indexOf(ch)
                if (v < 0) return null
                bits = (bits shl 5) or v
                bitCount += 5
                while (bitCount >= 8) {
                    bitCount -= 8
                    out.append("%02x".format((bits shr bitCount) and 0xFF))
                }
            }
            return out.toString().takeIf { it.length == 40 }
        }

        private fun isTrackerUrl(url: String): Boolean {
            val scheme = url.substringBefore("://", "").lowercase(Locale.ROOT)
            return scheme in setOf("udp", "http", "https", "ws", "wss") && url.length > scheme.length + 3
        }

        private fun decode(value: String): String? = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** Builds a canonical magnet link from an info hash (40 hex), an optional name and trackers. */
fun buildMagnetUri(infoHash: String, displayName: String?, trackers: List<String>, peers: List<String> = emptyList()): String {
    val sb = StringBuilder("magnet:?xt=urn:btih:").append(infoHash.lowercase(Locale.ROOT))
    if (!displayName.isNullOrBlank()) sb.append("&dn=").append(URLEncoder.encode(displayName, "UTF-8"))
    for (tracker in trackers) sb.append("&tr=").append(URLEncoder.encode(tracker, "UTF-8"))
    for (peer in peers) sb.append("&x.pe=").append(URLEncoder.encode(peer, "UTF-8"))
    return sb.toString()
}
