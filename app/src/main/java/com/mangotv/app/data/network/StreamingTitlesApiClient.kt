package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** One title as GET /user/streaming-titles answers it (see server/src/services/streamingTitlesService.ts): TMDB's data, with the IMDb id the apps open titles by. */
@Serializable
data class StreamingTitleDto(
    val imdbId: String,
    val type: String,
    val title: String,
    val year: Int? = null,
    val rating: Double? = null,
    val overview: String = "",
    val poster: String? = null,
    val backdrop: String? = null
)

/** Wire-format response for GET /user/streaming-titles. `available` false means the server has no TMDB source for the service (or no TMDB key). */
@Serializable
data class StreamingTitlesResponse(
    val available: Boolean = false,
    val items: List<StreamingTitleDto> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 0
)

/** Talks to /user/streaming-titles -- more of a streaming service's titles than its addon lists, from TMDB through the backend; authenticated like every other /user endpoint. */
class StreamingTitlesApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getTitles(accessToken: String, service: String, isTvShow: Boolean, page: Int, region: String): StreamingTitlesResponse =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/user/streaming-titles".toHttpUrl().newBuilder()
                .addQueryParameter("service", service)
                .addQueryParameter("type", if (isTvShow) "TV_SHOW" else "MOVIE")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("region", region)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, "Request failed with HTTP ${response.code}")
                json.decodeFromString(StreamingTitlesResponse.serializer(), bodyString)
            }
        }
}
