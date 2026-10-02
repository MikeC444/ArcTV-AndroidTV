package com.mangotv.app.data.network

import com.mangotv.app.data.profile.Profile
import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/profiles (see server/src/routes/profiles.ts).

@Serializable
data class ProfilesResponse(val profiles: List<Profile> = emptyList())

@Serializable
data class ProfileCreateRequest(
    val name: String,
    val avatar: String,
    val kind: String,
    /** Absent (not null) when the profile isn't locked. */
    val pin: String? = null
)

@Serializable
data class VerifyPinRequest(val pin: String)
