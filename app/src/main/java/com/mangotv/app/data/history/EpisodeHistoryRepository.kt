package com.mangotv.app.data.history

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.model.Season
import com.mangotv.app.data.network.PlaybackProgressApiClient
import com.mangotv.app.data.network.WatchHistoryEntryDto
import java.io.IOException

/** The key an episode is tracked under: "season:episode". */
fun episodeKey(season: Int, episode: Int): String = "$season:$episode"

/** What the history says about one episode: finished, or how far in it got (0 to 1). */
data class EpisodeStatus(val watched: Boolean, val fraction: Float)

/**
 * The finished and part-way episodes of one show, from watch-history rows (one row per episode): a finished episode is watched, otherwise the
 * fraction is how far in it got, and an episode that was never started (or was marked not watched) has no entry.
 */
fun episodeStatuses(rows: List<WatchHistoryEntryDto>, providerId: String, contentId: String): Map<String, EpisodeStatus> {
    val result = HashMap<String, EpisodeStatus>()
    for (row in rows) {
        val season = row.seasonNumber
        val episode = row.episodeNumber
        if (row.providerId != providerId || row.contentId != contentId || season == null || episode == null) continue
        val key = episodeKey(season, episode)
        if (row.completed) {
            result[key] = EpisodeStatus(watched = true, fraction = 1f)
        } else if (row.durationMs > 0 && row.positionMs > 0) {
            result[key] = EpisodeStatus(watched = false, fraction = (row.positionMs.toFloat() / row.durationMs).coerceIn(0f, 1f))
        }
    }
    return result
}

/**
 * The episode after the furthest one watched (a season caught up moves on to the next season), or null when nothing is watched or the last
 * episode is the furthest, so the caller falls back to the first. Season 0 (specials) is left out, as on the title page.
 */
fun nextEpisodeAfterWatched(seasons: List<Season>, statuses: Map<String, EpisodeStatus>): com.mangotv.app.data.model.Episode? {
    val ordered = seasons.filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }.flatMap { season -> season.episodes.sortedBy { it.episodeNumber } }
    val last = ordered.indexOfLast { statuses[episodeKey(it.seasonNumber, it.episodeNumber)]?.watched == true }
    return if (last >= 0) ordered.getOrNull(last + 1) else null
}

/**
 * Which episodes of a show the signed-in profile has finished or is part-way through, from the account's watch history (GET /user/history,
 * newest first). Read when a show's page opens and again when it comes back into view (an episode may have just finished); the page keeps its
 * own changes on top of that.
 */
class EpisodeHistoryRepository(private val authRepository: AuthRepository) {
    private val client = PlaybackProgressApiClient(BuildConfig.API_BASE_URL)

    /** Throws [IOException] (or an ApiException) when the history can't be read. */
    suspend fun statuses(providerId: String, contentId: String): Map<String, EpisodeStatus> {
        if (!authRepository.ensureFreshSession()) throw IOException("Not signed in")
        val token = authRepository.getCurrentSession()?.accessToken ?: throw IOException("Not signed in")
        val rows = ArrayList<WatchHistoryEntryDto>()
        var before: String? = null
        for (page in 0 until MAX_PAGES) {
            val result = client.getHistory(token, PAGE, before).items
            rows.addAll(result)
            if (result.size < PAGE) break
            before = result.last().watchedAt
        }
        return episodeStatuses(rows, providerId, contentId)
    }

    private companion object {
        const val PAGE = 200
        /** A very long history is read up to here (newest first) rather than without end. */
        const val MAX_PAGES = 25
    }
}
