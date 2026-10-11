package com.mangotv.app.data.addon

import com.mangotv.app.data.model.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks the person's addons that offer subtitles (in the Stremio protocol, the "subtitles" resource) which files they have for the video
 * being played. Best-effort: an addon that is slow, down or answers badly just adds nothing, and the whole lookup never throws, since a
 * missing subtitle list must never get in the way of the video.
 */
class SubtitleFinder(
    private val addons: AddonRepository,
    private val client: StremioAddonClient = StremioAddonClient()
) {
    suspend fun find(type: ContentType, id: String, season: Int?, episode: Int?, filename: String?, preferredLanguage: String?): List<AddonSubtitle> {
        val stremioType = if (type == ContentType.TV_SHOW) "series" else "movie"
        val requestId = if (season != null && episode != null) "$id:$season:$episode" else id
        val asked = addons.installedAddons.value.filter { it.enabled && it.manifest.offersSubtitles(stremioType, requestId) }
        if (asked.isEmpty()) return emptyList()
        val found = try {
            coroutineScope {
                asked.map { addon ->
                    async {
                        try {
                            withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                                client.fetchSubtitles(addon.manifestUrl, stremioType, requestId, filename).toAddonSubtitles(addon.manifest.name)
                            }.orEmpty()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        return rankSubtitles(found, preferredLanguage)
    }

    private companion object {
        const val ADDON_TIMEOUT_MS = 8_000L
    }
}
