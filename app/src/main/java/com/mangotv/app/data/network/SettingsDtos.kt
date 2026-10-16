package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/settings (see server/src/schemas/settings.ts
// and src/routes/settings.ts) -- the cloud mirror of HomeRowPreferences +
// PlayerPreferences, combined into the one request/response shape the
// backend's single user_settings row uses.

@Serializable
data class SettingsRequest(
    val homeRowOrder: List<String>,
    val hiddenRowIds: List<String>,
    val autoplayNextEpisode: Boolean,
    val skipIntroEnabled: Boolean,
    val subtitlesEnabled: Boolean,
    val defaultSubtitleLanguage: String?,
    /** No default value on purpose: a null must be sent (it clears the account's choice), and the encoder drops defaults. */
    val defaultAudioLanguage: String?,
    /** Genres hidden everywhere; always sent, so the account's list follows the latest push. */
    val blockedGenres: List<String> = emptyList(),
    /** When Your stats was last reset (ISO), only sent once it was: absent leaves the account's as it is, and the account only moves it forward. */
    val statsResetAt: String? = null,
    val updatedAt: String
)

@Serializable
data class SettingsResponse(
    val homeRowOrder: List<String>,
    val hiddenRowIds: List<String>,
    val autoplayNextEpisode: Boolean,
    val skipIntroEnabled: Boolean,
    val subtitlesEnabled: Boolean,
    val defaultSubtitleLanguage: String?,
    /** [AUDIO_LANGUAGE_ABSENT] when the backend predates it, so an older server never wipes the local choice; null means "no preference". */
    val defaultAudioLanguage: String? = AUDIO_LANGUAGE_ABSENT,
    /** null when the backend predates blocked genres, so an older server never wipes the local list. */
    val blockedGenres: List<String>? = null,
    /** When Your stats was last reset, or null (also from a backend that predates it). */
    val statsResetAt: String? = null,
    /** null only for an account that has never pushed settings from any device. */
    val updatedAt: String? = null
)

/** Stands in for "the server didn't send defaultAudioLanguage at all" (distinct from an explicit null, which means no preference). */
const val AUDIO_LANGUAGE_ABSENT = "\u0000absent"
