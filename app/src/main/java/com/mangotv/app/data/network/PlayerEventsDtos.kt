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
    val errorMessage: String? = null
)
