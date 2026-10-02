package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

/** One cast member as GET /user/cast answers it (see server/src/services/castService.ts): TMDB's name, the character played and a photo address, either of the last two null when TMDB has none. */
@Serializable
data class CastEntryDto(
    val name: String,
    val character: String? = null,
    val photo: String? = null
)

/** Wire-format response for GET /user/cast (see server/src/routes/cast.ts). Empty means the lookup isn't configured or TMDB has no match -- both collapse to the same thing from here. */
@Serializable
data class CastResponse(val cast: List<CastEntryDto> = emptyList())
