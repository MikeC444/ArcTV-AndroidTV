package com.mangotv.app.data.model

/** What one addon answered when asked for the streams of a title, so a missing source is never a mystery. */
sealed interface StreamLookup {
    /** It answered with [count] sources. */
    data class Ok(val count: Int) : StreamLookup

    /** It answered, with no sources for this title. */
    data object None : StreamLookup

    /** Its manifest says it doesn't provide streams (e.g. Cinemeta: catalog and meta only), so it wasn't asked. */
    data object Unsupported : StreamLookup

    /** It couldn't be asked or didn't answer; [reason] reads as the end of a sentence about the addon. */
    data class Failed(val reason: String) : StreamLookup
}

data class StreamReport(
    val addonName: String,
    val streams: List<Stream>,
    val lookup: StreamLookup
)
