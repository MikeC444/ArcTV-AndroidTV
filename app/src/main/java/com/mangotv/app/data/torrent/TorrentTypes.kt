package com.mangotv.app.data.torrent

import java.io.IOException

private const val MB = 1024L * 1024L

/**
 * How one torrent stream behaves. [readAheadBytes] is how far ahead of the picture pieces are fetched (the buffer; memory use does not grow
 * with it, it is the amount of not-yet-played data kept on disk), [startBufferBytes] how much must have arrived before playback starts, and
 * [maxStorageBytes] the most temporary data kept on disk (older data is dropped to stay under it).
 */
data class TorrentStreamConfig(
    val readAheadBytes: Long = 64 * MB,
    val startBufferBytes: Long = 8 * MB,
    val maxStorageBytes: Long = 2048 * MB,
    val metadataTimeoutMs: Long = 90_000,
    val startTimeoutMs: Long = 120_000,
    val stallTimeoutMs: Long = 60_000,
    val connectionsLimit: Int = 60,
    val idlePauseMs: Long = 5 * 60_000
) {
    init {
        require(readAheadBytes > 0 && startBufferBytes > 0 && maxStorageBytes > 0) { "sizes must be positive" }
    }

    /** The start buffer can never be bigger than the read-ahead (it is the first part of it). */
    val effectiveStartBytes: Long get() = minOf(startBufferBytes, readAheadBytes)
}

/** What to play: a magnet link or the bytes of a .torrent file, plus what is known about which file inside it is wanted. */
class TorrentRequest(
    val magnet: MagnetLink?,
    val torrentFile: ByteArray?,
    val hint: FileHint = FileHint(),
    /** Extra peers to try straight away, as `host:port` (a magnet link's own `x.pe` peers are used too). */
    val peers: List<String> = emptyList()
) {
    init {
        require((magnet != null) != (torrentFile != null)) { "exactly one of magnet and torrentFile" }
    }
}

/** Live numbers shown while buffering and as the "stalled" state during playback. */
data class TorrentStats(
    val peers: Int = 0,
    val seeds: Int = 0,
    val downloadBytesPerSecond: Long = 0,
    val bufferedAheadBytes: Long = 0,
    /** True while the player is waiting on a piece that has not arrived yet. */
    val stalled: Boolean = false
)

/** A subtitle file from the torrent, served by the local server beside the video. */
data class TorrentSubtitle(val url: String, val label: String, val language: String?, val mimeType: String)

sealed interface TorrentStreamState {
    data object Starting : TorrentStreamState
    data class FetchingMetadata(val peers: Int, val elapsedMs: Long) : TorrentStreamState
    data class Buffering(val bufferedBytes: Long, val targetBytes: Long, val fileName: String, val stats: TorrentStats) : TorrentStreamState
    data class Playing(
        val url: String,
        val fileName: String,
        val fileSize: Long,
        val stats: TorrentStats,
        val subtitles: List<TorrentSubtitle> = emptyList()
    ) : TorrentStreamState
    data class Failed(val error: TorrentStreamException) : TorrentStreamState
    data object Closed : TorrentStreamState
}

enum class TorrentErrorKind {
    INVALID_SOURCE, ENGINE_UNAVAILABLE, METADATA_TIMEOUT, NO_VIDEO, EPISODE_NOT_FOUND, NO_SPACE, STORAGE, NO_PEERS, STALLED, FAILED
}

/** A failure with a message written for the person watching (never an address, path or stack detail). */
class TorrentStreamException(val kind: TorrentErrorKind, override val message: String, cause: Throwable? = null) : IOException(message, cause)

/** How much temporary space a stream may use, decided before anything is downloaded. */
sealed interface StoragePlan {
    /** Go ahead; at most [capBytes] of data is kept on disk at once. */
    data class Ok(val capBytes: Long) : StoragePlan
    /** Not enough free space even for the read-ahead; [neededBytes] is the least that would do. */
    data class NotEnough(val neededBytes: Long, val availableBytes: Long) : StoragePlan
}

/**
 * The storage cap for one stream: the configured limit, lowered to what the device has free (minus [SAFETY_MARGIN_BYTES]), never above the
 * file itself (a small file needs no more than its own size). Fails only when even the read-ahead plus start buffer would not fit.
 */
fun planStorage(fileSize: Long, config: TorrentStreamConfig, usableSpaceBytes: Long): StoragePlan {
    val windowNeed = minOf(config.readAheadBytes + config.effectiveStartBytes, fileSize)
    val room = usableSpaceBytes - SAFETY_MARGIN_BYTES
    if (room < windowNeed) return StoragePlan.NotEnough(windowNeed + SAFETY_MARGIN_BYTES, usableSpaceBytes)
    // The configured limit, lowered to the free space and to the file's own size, but never below the window itself (below that it
    // would drop data it had just fetched).
    val cap = minOf(config.maxStorageBytes, room, fileSize + 2 * 1024 * 1024)
    return StoragePlan.Ok(maxOf(cap, windowNeed))
}

const val SAFETY_MARGIN_BYTES: Long = 64 * 1024 * 1024

fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "avi", "divx" -> "video/x-msvideo"
    "mov" -> "video/quicktime"
    "ts", "m2ts", "mts" -> "video/mp2t"
    "mpg", "mpeg", "vob" -> "video/mpeg"
    "wmv" -> "video/x-ms-wmv"
    "flv" -> "video/x-flv"
    "ogv" -> "video/ogg"
    "3gp" -> "video/3gpp"
    else -> "application/octet-stream"
}

/** Deletes a folder and everything in it, best effort: it may be racing another deletion of the same folder, and a failure is not worth reporting. */
fun deleteTorrentFolder(folder: java.io.File) {
    try { folder.deleteRecursively() } catch (_: Throwable) {}
}

/**
 * Long-running public UDP trackers, added only to a magnet link that names none (a bare info hash, or a link pasted without `tr=`), so peers
 * are found in a second or two instead of waiting for the DHT. A link or addon that lists its own trackers is never given these.
 */
val DEFAULT_TRACKERS: List<String> = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://open.demonii.com:1337/announce"
)
