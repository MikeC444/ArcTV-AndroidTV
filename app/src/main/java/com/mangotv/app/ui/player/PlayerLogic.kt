package com.mangotv.app.ui.player

import android.content.Context
import com.mangotv.app.data.model.Season

/** The episode that follows the one playing, in the same show (the web app's `nextEpisodeAfter`). */
data class NextEpisode(val season: Int, val episode: Int, val title: String)

fun nextEpisodeAfter(seasons: List<Season>, season: Int?, episode: Int?): NextEpisode? {
    if (season == null || episode == null) return null
    val s = seasons.indexOfFirst { it.seasonNumber == season }
    if (s < 0) return null
    val episodes = seasons[s].episodes
    val e = episodes.indexOfFirst { it.episodeNumber == episode }
    if (e < 0) return null
    episodes.getOrNull(e + 1)?.let { return NextEpisode(season, it.episodeNumber, it.title) }
    val first = seasons.getOrNull(s + 1)?.episodes?.firstOrNull() ?: return null
    return NextEpisode(seasons[s + 1].seasonNumber, first.episodeNumber, first.title)
}

/** "Next episode" appears in the corner for the last minute of an episode (or once it has ended), so it can be taken without waiting. */
const val NEXT_EPISODE_OFFER_S = 60

fun offerNextEpisode(positionMs: Long, durationMs: Long, hasNext: Boolean): Boolean {
    if (!hasNext || durationMs <= 0 || positionMs <= 0) return false
    return (durationMs - positionMs) / 1000 <= NEXT_EPISODE_OFFER_S
}

/** A saved position is offered as "Resume from ..." unless it is (almost) the end of the file. */
fun shouldOfferResume(resumeMs: Long?, durationMs: Long): Boolean =
    resumeMs != null && resumeMs > 0 && durationMs > 0 && durationMs - resumeMs > 10_000

/** The right-hand time: "-12:34" (time left) or the total length. */
fun formatRightTime(positionMs: Long, durationMs: Long, showRemaining: Boolean): String =
    if (showRemaining) "−" + formatTimestamp((durationMs - positionMs).coerceAtLeast(0)) else formatTimestamp(durationMs.coerceAtLeast(0))

/**
 * What the player remembers between titles on this device only (not synced: a TV and a phone want different settings): the playback
 * speed, and whether the right-hand time shows what is left. (Volume is the TV's own, so it is not kept here.)
 */
object DevicePlayerPrefs {
    private const val FILE = "arctv_device_player"
    private const val SPEED = "speed"
    private const val SHOW_REMAINING = "show_remaining"

    fun speed(context: Context): Float {
        val value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(SPEED, 1f)
        return if (value in 0.25f..4f) value else 1f
    }

    fun setSpeed(context: Context, speed: Float) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat(SPEED, speed).apply()
    }

    fun showRemaining(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SHOW_REMAINING, true)

    fun setShowRemaining(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SHOW_REMAINING, value).apply()
    }
}
