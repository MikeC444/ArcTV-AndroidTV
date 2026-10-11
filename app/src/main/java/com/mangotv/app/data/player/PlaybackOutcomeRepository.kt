package com.mangotv.app.data.player

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.PlaybackOutcomeDto
import com.mangotv.app.data.network.PlayerEventsApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tells the server how each viewing ended (played for a while, left within seconds, hit an error, never started), so the developer panel
 * can show whether people abandon playback on one device, player or picture quality.
 *
 * Fire-and-forget on its own scope (the report is sent as the player closes, so the caller's scope may not outlive the call) and
 * best-effort: a report that can't be sent (offline, signed out) is dropped, since it must never get in the way of playing.
 */
class PlaybackOutcomeRepository(private val authRepository: AuthRepository) {
    private val apiClient = PlayerEventsApiClient(BuildConfig.API_BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(outcome: PlaybackOutcomeDto) {
        scope.launch {
            try {
                if (!authRepository.ensureFreshSession()) return@launch
                val token = authRepository.getCurrentSession()?.accessToken ?: return@launch
                apiClient.postPlaybackOutcome(token, outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // Not worth retrying.
            } catch (e: Exception) {
                // Offline or unexpected: dropped on purpose.
            }
        }
    }
}
