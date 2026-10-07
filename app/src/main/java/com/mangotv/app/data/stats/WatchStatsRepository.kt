package com.mangotv.app.data.stats

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.PlaybackProgressApiClient
import java.io.IOException
import java.time.Instant

/** What reading the watch history for Your stats gave: the stats, and whether a very long history was cut short (the newest part is used). */
data class LoadedStats(val stats: WatchStats, val truncated: Boolean)

/** Reads the signed-in profile's whole watch history, a page at a time (GET /user/history, newest first), and works out the stats. */
class WatchStatsRepository(private val authRepository: AuthRepository) {
    private val client = PlaybackProgressApiClient(BuildConfig.API_BASE_URL)

    /** Throws [IOException] (or an ApiException) when it can't be read. */
    suspend fun load(): LoadedStats {
        if (!authRepository.ensureFreshSession()) throw IOException("Not signed in")
        val token = authRepository.getCurrentSession()?.accessToken ?: throw IOException("Not signed in")
        val items = ArrayList<HistoryItem>()
        var before: String? = null
        var truncated = true
        for (page in 0 until MAX_PAGES) {
            val result = client.getHistory(token, PAGE, before).items
            result.forEach { entry ->
                val type = entry.contentType
                if (type != "MOVIE" && type != "TV_SHOW") return@forEach
                val at = runCatching { Instant.parse(entry.watchedAt).toEpochMilli() }.getOrNull() ?: return@forEach
                items.add(HistoryItem(entry.providerId, entry.contentId, type == "MOVIE", entry.positionMs, entry.durationMs, entry.completed, at))
            }
            if (result.size < PAGE) { truncated = false; break }
            before = result.last().watchedAt
        }
        return LoadedStats(computeStats(items), truncated)
    }

    private companion object {
        const val PAGE = 200
        /** A very long history is read up to here (newest first) rather than without end. */
        const val MAX_PAGES = 25
    }
}
