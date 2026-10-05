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
    private const val AUDIO_PASSTHROUGH = "audio_passthrough"
    private const val AUDIO_CHANNELS = "audio_channels"
    private const val VLC_HARDWARE = "vlc_hardware"

    fun speed(context: Context): Float {
        val value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(SPEED, 1f)
        return if (value in 0.25f..4f) value else 1f
    }

    fun setSpeed(context: Context, speed: Float) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat(SPEED, speed).apply()
    }

    fun showRemaining(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SHOW_REMAINING, true)

    /** Whether surround audio (Dolby / DTS) may be sent untouched to the TV or receiver; off decodes it to plain stereo/5.1 sound in the app. On by default. */
    fun audioPassthrough(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(AUDIO_PASSTHROUGH, true)

    fun setAudioPassthrough(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(AUDIO_PASSTHROUGH, value).apply()
    }

    /** The most speakers' worth of sound to send out (Settings > Audio); Auto leaves it to what the TV supports. */
    fun audioChannelMode(context: Context): AudioChannelMode =
        AudioChannelMode.fromWire(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(AUDIO_CHANNELS, null))

    fun setAudioChannelMode(context: Context, mode: AudioChannelMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(AUDIO_CHANNELS, mode.wire).apply()
    }

    /** Whether VLC's engine may use the device's hardware video decoder; off (software) by default. */
    fun vlcHardwareDecoding(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(VLC_HARDWARE, false)

    fun setVlcHardwareDecoding(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(VLC_HARDWARE, value).apply()
    }

    fun setShowRemaining(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SHOW_REMAINING, value).apply()
    }
}
