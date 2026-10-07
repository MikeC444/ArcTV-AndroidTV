package com.mangotv.app.ui.player

import android.content.Context
import com.mangotv.app.data.model.Season
import com.mangotv.app.data.torrent.TorrentBuffer
import com.mangotv.app.data.torrent.TorrentStorageLimit

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

/** The two players Arc TV can play a title with (another app is a one-off pick, never remembered). */
enum class PreferredPlayer(val wire: String) {
    BUILT_IN("builtin"),
    VLC("vlc");

    companion object {
        fun fromWire(value: String?): PreferredPlayer? = entries.firstOrNull { it.wire == value }
    }
}

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
    private const val DEFAULT_PLAYER = "default_player"
    private const val TITLE_PLAYERS_FILE = "arctv_title_players"
    private const val TORRENT_BUFFER = "torrent_buffer"
    private const val TORRENT_STORAGE = "torrent_storage"
    private const val SMART_PICKING = "smart_source_picking"

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

    /** The player used for a title that has no pick of its own (Settings > Player): VLC's engine unless the person changed it. */
    fun defaultPlayer(context: Context): PreferredPlayer =
        PreferredPlayer.fromWire(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(DEFAULT_PLAYER, null)) ?: PreferredPlayer.VLC

    fun setDefaultPlayer(context: Context, player: PreferredPlayer) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(DEFAULT_PLAYER, player.wire).apply()
    }

    /** The player the person picked for this title (from the player's Choose player card), or null if they never did. */
    fun titlePlayer(context: Context, titleKey: String): PreferredPlayer? =
        PreferredPlayer.fromWire(context.getSharedPreferences(TITLE_PLAYERS_FILE, Context.MODE_PRIVATE).getString(titleKey, null))

    fun setTitlePlayer(context: Context, titleKey: String, player: PreferredPlayer) {
        context.getSharedPreferences(TITLE_PLAYERS_FILE, Context.MODE_PRIVATE).edit().putString(titleKey, player.wire).apply()
    }

    /** The player to start a title with: the one picked for it, else the default. */
    fun playerFor(context: Context, titleKey: String): PreferredPlayer = titlePlayer(context, titleKey) ?: defaultPlayer(context)

    /** How far ahead of the picture a torrent is fetched (Settings > Player > Torrent buffer). */
    fun torrentBuffer(context: Context): TorrentBuffer =
        TorrentBuffer.fromWire(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(TORRENT_BUFFER, null))

    fun setTorrentBuffer(context: Context, value: TorrentBuffer) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(TORRENT_BUFFER, value.wire).apply()
    }

    /** The most temporary storage one torrent may use (Settings > Player > Torrent storage limit). */
    fun torrentStorageLimit(context: Context): TorrentStorageLimit =
        TorrentStorageLimit.fromWire(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(TORRENT_STORAGE, null))

    /** Arc TV Plus: skip Select a Source and play the best source (Settings > Arc TV Plus). Off until turned on; kept on this device. */
    fun smartSourcePicking(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SMART_PICKING, false)

    fun setSmartSourcePicking(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SMART_PICKING, value).apply()
    }

    fun setTorrentStorageLimit(context: Context, value: TorrentStorageLimit) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(TORRENT_STORAGE, value.wire).apply()
    }

    fun setShowRemaining(context: Context, value: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SHOW_REMAINING, value).apply()
    }
}
