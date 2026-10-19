package com.mangotv.app.data.streaming

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.network.StreamingTitleDto
import com.mangotv.app.data.network.StreamingTitlesApiClient
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** One page of a service's titles beyond what its addon lists. */
class ServiceTitlesPage(
    /** False when there is no TMDB source for this service (or the server has no TMDB key, or the ask failed): the page keeps to the addon's own titles. */
    val available: Boolean,
    val items: List<Content>,
    val totalPages: Int
)

/**
 * More of a streaming service's titles than its addon lists (the Streaming Catalogs addon gives about 100 a catalogue and has no paging), read from the
 * backend's /user/streaming-titles (TMDB, most popular first, for the viewer's country). Same job as the web app's `state/streamingTitles.ts`. Never
 * throws: signed out, offline or a server error all mean "not available", and the service page simply stops at the addon's titles.
 */
class StreamingTitlesRepository(private val authRepository: AuthRepository) {
    private val apiClient = StreamingTitlesApiClient(BuildConfig.API_BASE_URL)

    /** [providerId] is the addon the service page came from: the titles open through it, like the addon's own. */
    suspend fun page(service: String, type: ContentType, page: Int, providerId: String): ServiceTitlesPage {
        if (!authRepository.ensureFreshSession()) return NOT_AVAILABLE
        val token = authRepository.getCurrentSession()?.accessToken ?: return NOT_AVAILABLE
        return try {
            val answer = apiClient.getTitles(token, service, isTvShow = type == ContentType.TV_SHOW, page = page, region = regionOf(Locale.getDefault()))
            ServiceTitlesPage(answer.available, answer.items.mapNotNull { it.toContent(providerId) }, answer.totalPages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NOT_AVAILABLE
        }
    }

    private companion object {
        val NOT_AVAILABLE = ServiceTitlesPage(available = false, items = emptyList(), totalPages = 0)
    }
}

/** The viewer's country as TMDB wants it (what a service carries differs by country): the device's, else the US (as the web app). */
fun regionOf(locale: Locale): String = locale.country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"

internal fun StreamingTitleDto.toContent(providerId: String): Content? {
    val contentType = when (type) {
        "MOVIE" -> ContentType.MOVIE
        "TV_SHOW" -> ContentType.TV_SHOW
        else -> return null
    }
    return Content(
        id = imdbId,
        type = contentType,
        title = title,
        description = overview,
        posterUrl = poster,
        backdropUrl = backdrop,
        year = year,
        rating = rating,
        providerId = providerId
    )
}
