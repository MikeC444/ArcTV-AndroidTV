package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Talks to /user/cast -- a server-side TMDB lookup (see server/src/services/castService.ts), authenticated the same as every other endpoint under /user. */
class CastApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCast(accessToken: String, imdbId: String, isTvShow: Boolean): CastResponse =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/user/cast".toHttpUrl().newBuilder()
                .addQueryParameter("imdbId", imdbId)
                .addQueryParameter("type", if (isTvShow) "TV_SHOW" else "MOVIE")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw toApiException(response.code, bodyString)
                json.decodeFromString(CastResponse.serializer(), bodyString)
            }
        }

    private fun toApiException(code: Int, bodyString: String): ApiException {
        val message = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), bodyString).error
        }.getOrNull()
        return ApiException(code, message ?: "Request failed with HTTP $code")
    }
}
