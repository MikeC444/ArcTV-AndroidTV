package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/feedback (see server/src/schemas/feedback.ts and src/routes/feedback.ts): one record per
// (profile, addon, movie) with the person's Like / Not for me. No default values on anything the server requires -- the
// JSON encoder omits defaults, which would drop them from the request.

@Serializable
data class FeedbackDto(
    val profileId: String,
    val providerId: String,
    val contentId: String,
    val contentType: String,
    val title: String,
    /** "like" or "dislike". */
    val feedback: String,
    val updatedAt: String,
    /** Non-null means the feedback is currently cleared -- only meaningful on a POST/DELETE response. */
    val deletedAt: String? = null
)

@Serializable
data class FeedbackListResponse(val items: List<FeedbackDto>)
