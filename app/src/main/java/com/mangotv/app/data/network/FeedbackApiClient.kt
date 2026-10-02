package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Talks to /user/feedback -- this account's Like / Not for me on movies. Item-level, like [WatchlistApiClient]; every call is authenticated. */
class FeedbackApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(accessToken: String, profileId: String): FeedbackListResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/feedback".toHttpUrl().newBuilder().addQueryParameter("profileId", profileId).build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
        json.decodeFromString(FeedbackListResponse.serializer(), execute(request))
    }

    /** Sets (or re-sets) one movie's feedback; returns the slot's authoritative state (this write, or a newer one that beat it). */
    suspend fun set(accessToken: String, dto: FeedbackDto): FeedbackDto = withContext(Dispatchers.IO) {
        val body = json.encodeToString(FeedbackDto.serializer(), dto)
        val request = Request.Builder()
            .url("$baseUrl/user/feedback")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(FeedbackDto.serializer(), execute(request))
    }

    /** Clears one movie's feedback. Null when it never reached the server, so there is nothing to reconcile. */
    suspend fun clear(accessToken: String, dto: FeedbackDto): FeedbackDto? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/feedback".toHttpUrl().newBuilder()
            .addQueryParameter("profileId", dto.profileId)
            .addQueryParameter("providerId", dto.providerId)
            .addQueryParameter("contentId", dto.contentId)
            .addQueryParameter("contentType", dto.contentType)
            .addQueryParameter("updatedAt", dto.updatedAt)
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").delete().build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 204) return@withContext null
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw toApiException(response.code, bodyString)
            return@withContext json.decodeFromString(FeedbackDto.serializer(), bodyString)
        }
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw toApiException(response.code, bodyString)
            return bodyString
        }
    }

    private fun toApiException(code: Int, bodyString: String): ApiException {
        val message = runCatching { json.decodeFromString(ErrorResponse.serializer(), bodyString).error }.getOrNull()
        return ApiException(code, message ?: "Request failed with HTTP $code")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
