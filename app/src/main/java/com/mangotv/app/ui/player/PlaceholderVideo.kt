package com.mangotv.app.ui.player

/** A "video" shorter than this, for a title that runs much longer, is not the title. */
const val PLACEHOLDER_MAX_MS = 120_000L

const val PLACEHOLDER_MESSAGE =
    "This source isn't ready yet. Its debrid service may still be preparing the file. Try again in a few minutes, or choose another source."

/**
 * Whether a source that turned out to be [durationMs] long is really a placeholder: some debrid addons answer
 * a title that isn't cached yet with a short video that says "Not ready yet", which plays like any other video. A film or episode with a
 * known runtime of at least 10 minutes that comes back as under two minutes, and under a tenth of that runtime, is such a slate. Without a
 * known runtime nothing is assumed, so a genuinely short film is never turned away.
 */
fun isPlaceholderVideo(durationMs: Long, runtimeMinutes: Int?): Boolean {
    val minutes = runtimeMinutes ?: return false
    if (minutes < 10 || durationMs <= 0) return false
    return durationMs < PLACEHOLDER_MAX_MS && durationMs < minutes * 60_000L / 10
}
