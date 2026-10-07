package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class FeatureIntroAckDto(val feature: String)

/** Talks to POST /user/feature-intros/ack: tells the server a one-off pop-up was clicked away, for the developer panel. Authenticated; the answer carries nothing. */
class FeatureIntroApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun acknowledge(accessToken: String, feature: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/feature-intros/ack")
            .header("Authorization", "Bearer $accessToken")
            .post(json.encodeToString(FeatureIntroAckDto.serializer(), FeatureIntroAckDto(feature)).toRequestBody(JSON_MEDIA_TYPE))
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
