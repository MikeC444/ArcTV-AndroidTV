package com.mangotv.app.data.torrent

import java.io.InputStream

/** The largest .torrent file read: real ones are tens of kilobytes (a few megabytes for a torrent of tens of thousands of files). */
const val MAX_TORRENT_FILE_BYTES = 8L * 1024 * 1024

/**
 * Reads a .torrent file from [input], refusing anything over [maxBytes] or that is clearly not one (a .torrent file is a bencoded
 * dictionary: it starts with `d` and ends with `e`), so a web page or an error body is never handed to the torrent engine.
 */
fun readTorrentFileBytes(input: InputStream, maxBytes: Long = MAX_TORRENT_FILE_BYTES): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > maxBytes) throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "That .torrent file is too large to be real.")
        out.write(buffer, 0, n)
    }
    val bytes = out.toByteArray()
    if (!looksLikeTorrentFile(bytes)) throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "That isn't a valid .torrent file.")
    return bytes
}

fun looksLikeTorrentFile(bytes: ByteArray): Boolean {
    val trimmedEnd = bytes.indexOfLast { it != '\n'.code.toByte() && it != '\r'.code.toByte() }
    return bytes.size > 16 && bytes[0] == 'd'.code.toByte() && trimmedEnd >= 0 && bytes[trimmedEnd] == 'e'.code.toByte()
}

/** Wraps I/O failures while fetching a .torrent file in a message for the person. */
fun torrentFetchError(cause: Throwable): TorrentStreamException =
    cause as? TorrentStreamException
        ?: TorrentStreamException(
            TorrentErrorKind.INVALID_SOURCE,
            if (cause is java.net.SocketTimeoutException) "The .torrent file took too long to download." else "Couldn't get the .torrent file.",
            cause
        )

