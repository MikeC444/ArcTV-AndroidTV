package com.mangotv.app.data.torrent.platform

import android.content.Context
import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.FeatureIntroApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Remembers, on this device, who gets the "Addons now support torrents" pop-up and who has clicked it away. The app has no account-creation
 * date to compare, so "already has an account" means: signed in the first time this version was opened. That user (and only that one) is
 * eligible, a guest or an account that signs in later is not, and once they click it away it is never shown to them again, even after signing
 * out and back in.
 */
class TorrentIntroStore(context: Context, private val authRepository: AuthRepository) {
    private val prefs = context.applicationContext.getSharedPreferences("arctv_intros", Context.MODE_PRIVATE)
    private val apiClient = FeatureIntroApiClient(BuildConfig.API_BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var shownThisSession = false
        private set

    /** Call once the stored session has been read. The first call ever fixes who was already signed in; later calls change nothing. */
    @Synchronized
    fun ensureBaseline(signedInUserId: String?) {
        if (prefs.getBoolean(BASELINE_DONE, false)) return
        prefs.edit()
            .putBoolean(BASELINE_DONE, true)
            .putStringSet(ELIGIBLE, setOfNotNull(signedInUserId))
            .apply()
    }

    fun isEligible(userId: String?): Boolean = userId != null && prefs.getStringSet(ELIGIBLE, emptySet())?.contains(userId) == true

    fun hasSeen(userId: String?): Boolean = userId != null && prefs.getStringSet(SEEN, emptySet())?.contains(userId) == true

    /** It is on screen: not again this launch, even if it is not clicked away. */
    fun markShown() {
        shownThisSession = true
    }

    /** The person clicked Got it (or pressed Back): never again for this user. */
    @Synchronized
    fun markSeen(userId: String) {
        prefs.edit()
            .putStringSet(SEEN, prefs.getStringSet(SEEN, emptySet()).orEmpty() + userId)
            .putStringSet(UNREPORTED, prefs.getStringSet(UNREPORTED, emptySet()).orEmpty() + userId)
            .apply()
        reportIfPending(userId)
    }

    /**
     * Tells the server (for the developer panel) that [userId] clicked the pop-up away, if that has not got through yet. Best effort and
     * retried: called when the pop-up is dismissed and again whenever Home opens for that user, until the server has it.
     */
    fun reportIfPending(userId: String?) {
        if (userId == null || prefs.getStringSet(UNREPORTED, emptySet())?.contains(userId) != true) return
        scope.launch {
            try {
                if (!authRepository.ensureFreshSession()) return@launch
                val session = authRepository.getCurrentSession() ?: return@launch
                // The report goes out under the signed-in account, so only for that account.
                if (session.user.id != userId) return@launch
                apiClient.acknowledge(session.accessToken, FEATURE)
                clearUnreported(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                // Kept for the next time Home opens.
            } catch (e: Exception) {
                // Offline: kept for the next time.
            }
        }
    }

    @Synchronized
    private fun clearUnreported(userId: String) {
        prefs.edit().putStringSet(UNREPORTED, prefs.getStringSet(UNREPORTED, emptySet()).orEmpty() - userId).apply()
    }

    private companion object {
        const val BASELINE_DONE = "torrent_intro_baseline_done"
        const val ELIGIBLE = "torrent_intro_eligible_users"
        const val SEEN = "torrent_intro_seen_users"
        const val UNREPORTED = "torrent_intro_unreported_users"
        const val FEATURE = "torrent_intro"
    }
}
