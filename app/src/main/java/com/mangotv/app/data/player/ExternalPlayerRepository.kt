package com.mangotv.app.data.player

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.ExternalPlayerEventDto
import com.mangotv.app.data.network.PlayerEventsApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tells the server each time someone confirms "Play in external player", so the developer panel can show how often people leave
 * for another player -- and how often that follows a playback error, which would point at the built-in player rather than taste.
 *
 * Fire-and-forget on its own scope (the person is about to leave the app, so the caller's scope may not outlive the call) and
 * best-effort: a report that can't be sent (offline, signed out) is dropped, since it must never get in the way of playing.
 */
class ExternalPlayerRepository(private val authRepository: AuthRepository) {
    private val apiClient = PlayerEventsApiClient(BuildConfig.API_BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(event: ExternalPlayerEventDto) {
        scope.launch {
            try {
                if (!authRepository.ensureFreshSession()) return@launch
                val token = authRepository.getCurrentSession()?.accessToken ?: return@launch
                apiClient.postExternalPlayerEvent(token, event)
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
