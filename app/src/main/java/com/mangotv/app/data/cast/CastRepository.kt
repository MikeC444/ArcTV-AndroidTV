package com.mangotv.app.data.cast

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.model.CastMember
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.network.CastApiClient
import kotlinx.coroutines.CancellationException

/**
 * Cast photos and characters from the backend's /user/cast endpoint -- a server-side TMDB lookup, cached there (see
 * server/src/services/castService.ts). The Stremio base protocol only gives cast names, so this upgrades them on the
 * Detail page. Read-only and one-shot, same shape as ReleaseDateRepository: no local persistence, and a failed lookup
 * just means the page keeps the plain names.
 */
class CastRepository(private val authRepository: AuthRepository) {
    private val apiClient = CastApiClient(BuildConfig.API_BASE_URL)

    /**
     * [current] with photos and characters filled in where TMDB has them. Returns [current] unchanged whenever the
     * lookup isn't possible or fails -- signed out, an id that isn't an IMDb id, a network or server error, or TMDB
     * having nothing -- all of which mean "keep showing what the addon sent".
     */
    suspend fun withTmdbDetails(contentId: String, type: ContentType, current: List<CastMember>): List<CastMember> {
        if (!IMDB_ID.matches(contentId)) return current
        if (current.isNotEmpty() && current.all { it.photoUrl != null && it.role != null }) return current
        val token = freshAccessTokenOrNull() ?: return current
        return try {
            mergeCast(current, apiClient.getCast(token, contentId, isTvShow = type == ContentType.TV_SHOW).cast)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            current
        }
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }

    private companion object {
        val IMDB_ID = Regex("^tt\\d{1,10}$")
    }
}
