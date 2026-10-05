package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Talks to the /user/player-events endpoints: usage reports the developer panel reads. Authenticated; the answer carries nothing. */
class PlayerEventsApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun postExternalPlayerEvent(accessToken: String, dto: ExternalPlayerEventDto) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/player-events/external")
            .header("Authorization", "Bearer $accessToken")
            .post(json.encodeToString(ExternalPlayerEventDto.serializer(), dto).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val message = runCatching { json.decodeFromString(ErrorResponse.serializer(), body).error }.getOrNull()
                throw ApiException(response.code, message ?: "Request failed with HTTP ${response.code}")
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
