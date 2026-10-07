package com.mangotv.app.data.torrent.platform

import android.content.Context

/**
 * Remembers, on this device, who gets the "Addons now support torrents" pop-up and who has clicked it away. The app has no account-creation
 * date to compare, so "already has an account" means: signed in the first time this version was opened. That user (and only that one) is
 * eligible, a guest or an account that signs in later is not, and once they click it away it is never shown to them again, even after signing
 * out and back in.
 */
class TorrentIntroStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("arctv_intros", Context.MODE_PRIVATE)

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
        prefs.edit().putStringSet(SEEN, prefs.getStringSet(SEEN, emptySet()).orEmpty() + userId).apply()
    }

    private companion object {
        const val BASELINE_DONE = "torrent_intro_baseline_done"
        const val ELIGIBLE = "torrent_intro_eligible_users"
        const val SEEN = "torrent_intro_seen_users"
    }
}
