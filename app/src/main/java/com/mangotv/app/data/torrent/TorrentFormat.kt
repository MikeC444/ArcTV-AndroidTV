package com.mangotv.app.data.torrent

import java.util.Locale

/** "850 KB", "12.4 MB", "1.3 GB". */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_000_000L -> "${(bytes.coerceAtLeast(0) + 500) / 1_000} KB"
    bytes < 1_000_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
}

fun formatRate(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"

/** The line shown under the loading screen while a torrent gets going, or null when there is nothing worth saying. */
fun describeTorrentProgress(state: TorrentStreamState): String? = when (state) {
    TorrentStreamState.Starting -> "Starting torrent engine…"
    is TorrentStreamState.FetchingMetadata ->
        if (state.peers == 0) "Looking for peers…" else "Getting torrent details from ${plural(state.peers, "peer")}…"
    is TorrentStreamState.Buffering -> {
        val percent = if (state.targetBytes > 0) (state.bufferedBytes * 100 / state.targetBytes).coerceIn(0, 100) else 0
        buildString {
            append("Buffering $percent%")
            append(" · ").append(plural(state.stats.peers, "peer"))
            if (state.stats.downloadBytesPerSecond > 0) append(" · ").append(formatRate(state.stats.downloadBytesPerSecond))
        }
    }
    is TorrentStreamState.Playing, is TorrentStreamState.Failed, TorrentStreamState.Closed -> null
}

/** The note shown over the picture while playback waits for torrent data, or null when the data is arriving in time. */
fun describeTorrentStall(stats: TorrentStats): String? {
    if (!stats.stalled) return null
    return buildString {
        append("Waiting for the torrent · ").append(plural(stats.peers, "peer"))
        if (stats.downloadBytesPerSecond > 0) append(" · ").append(formatRate(stats.downloadBytesPerSecond))
    }
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"
