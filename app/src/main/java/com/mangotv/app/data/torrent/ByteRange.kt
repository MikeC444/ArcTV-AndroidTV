package com.mangotv.app.data.torrent

import java.util.Locale

/** What an HTTP `Range` header asks for, against a resource of known length. */
sealed interface RangeRequest {
    /** No usable range: send the whole resource (200). Also used for a malformed or multi-range header, which a server may ignore (RFC 9110). */
    data object Full : RangeRequest
    /** A single satisfiable range, both ends inclusive and inside the resource (206). */
    data class Partial(val start: Long, val endInclusive: Long) : RangeRequest {
        val length: Long get() = endInclusive - start + 1
    }
    /** A well-formed range that lies outside the resource (416). */
    data object Unsatisfiable : RangeRequest
}

/** Parses a `Range` header value against a resource of [length] bytes (`bytes=a-b`, `bytes=a-`, `bytes=-n`). */
fun parseRangeHeader(header: String?, length: Long): RangeRequest {
    val value = header?.trim().orEmpty()
    if (value.isEmpty() || length <= 0) return RangeRequest.Full
    if (!value.lowercase(Locale.ROOT).startsWith("bytes=")) return RangeRequest.Full
    val spec = value.substring(6).trim()
    if (spec.contains(',')) return RangeRequest.Full
    val dash = spec.indexOf('-')
    if (dash < 0) return RangeRequest.Full
    val first = spec.substring(0, dash).trim()
    val last = spec.substring(dash + 1).trim()
    if (first.isEmpty()) {
        val suffix = last.toLongOrNull()?.takeIf { it >= 0 && last.all(Char::isDigit) } ?: return RangeRequest.Full
        if (suffix == 0L) return RangeRequest.Unsatisfiable
        return RangeRequest.Partial((length - suffix).coerceAtLeast(0), length - 1)
    }
    if (!first.all(Char::isDigit)) return RangeRequest.Full
    val start = first.toLongOrNull() ?: return RangeRequest.Unsatisfiable // too large for a Long: certainly past the end
    val end = if (last.isEmpty()) length - 1 else {
        if (!last.all(Char::isDigit)) return RangeRequest.Full
        last.toLongOrNull() ?: (length - 1)
    }
    if (last.isNotEmpty() && end < start) return RangeRequest.Full // invalid: ignored
    if (start >= length) return RangeRequest.Unsatisfiable
    return RangeRequest.Partial(start, minOf(end, length - 1))
}
