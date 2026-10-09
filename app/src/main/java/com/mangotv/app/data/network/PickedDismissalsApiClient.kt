package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Wire format of /user/picked-dismissals (see server/src/schemas/pickedDismissals.ts): a title removed from "Picked for you", per profile.
// Not taste feedback. No default values on anything the server requires -- the JSON encoder omits defaults, which would drop them.

@Serializable
data class PickedDismissalDto(val profileId: String, val contentId: String, val dismissedAt: String)

@Serializable
data class PickedDismissalListResponse(val items: List<PickedDismissalDto>)

/** Talks to /user/picked-dismissals: the titles this account removed from "Picked for you" in the last 30 days, and saving one more. Every call is authenticated. */
class PickedDismissalsApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(accessToken: String, profileId: String): PickedDismissalListResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/picked-dismissals".toHttpUrl().newBuilder().addQueryParameter("profileId", profileId).build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
        json.decodeFromString(PickedDismissalListResponse.serializer(), execute(request))
    }

    /** Saves one removal; returns the slot's authoritative state (this write, or a later removal of the same title that beat it). */
    suspend fun save(accessToken: String, dto: PickedDismissalDto): PickedDismissalDto = withContext(Dispatchers.IO) {
        val body = json.encodeToString(PickedDismissalDto.serializer(), dto)
        val request = Request.Builder()
            .url("$baseUrl/user/picked-dismissals")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(PickedDismissalDto.serializer(), execute(request))
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { json.decodeFromString(ErrorResponse.serializer(), bodyString).error }.getOrNull()
                throw ApiException(response.code, message ?: "Request failed with HTTP ${response.code}")
            }
            return bodyString
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
