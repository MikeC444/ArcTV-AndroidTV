package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

/**
 * Wire format for POST /user/player-events/external (see server/src/schemas/playerEvents.ts): one report each time someone
 * confirms "Play in external player". Never carries the stream address (it can hold a debrid key).
 */
@Serializable
data class ExternalPlayerEventDto(
    val providerId: String,
    val contentId: String,
    val contentType: String,
    val title: String,
    val releaseTitle: String? = null,
    val resolution: String? = null,
    val codec: String? = null,
    /** "button" (the icon beside the timeline) or "error" (the button on the "Unable to play" screen). */
    val trigger: String,
    /** "opened" (an external player took it) or "no_player" (none installed). */
    val outcome: String,
    val errorMessage: String? = null,
    /** "external" (another app on the device) or "vlc" (VLC's engine inside the app). */
    val engine: String = "external"
)

/**
 * Wire format for POST /user/player-events/playback (see server/src/schemas/playbackOutcomes.ts): how one viewing ended, so the developer
 * panel can show how often people leave within seconds or hit an error, by device, player and quality. Never carries a title, a content
 * id or the stream address.
 */
@Serializable
data class PlaybackOutcomeDto(
    /** "vlc" (VLC's engine) or "builtin" (Media3). */
    val engine: String,
    val deviceModel: String? = null,
    val resolution: String? = null,
    val codec: String? = null,
    val hdr: String? = null,
    /** Milliseconds from opening the player to the first picture; null when nothing ever played. */
    val startMs: Long? = null,
    val watchedMs: Long,
    /** "ok", "short", "error" or "never_started" (see [com.mangotv.app.ui.player.PlaybackHealthTracker]). */
    val outcome: String,
    val errorKind: String? = null,
    val sourcesTried: Int = 1
)
