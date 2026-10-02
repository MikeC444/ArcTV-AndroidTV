package com.mangotv.app.data.sync

import com.mangotv.app.data.addon.AddonRepository
import com.mangotv.app.data.feedback.FeedbackRepository
import com.mangotv.app.data.history.ContinueWatchingRepository
import com.mangotv.app.data.player.LastSourceRepository
import com.mangotv.app.data.player.PlayerPreferencesRepository
import com.mangotv.app.data.profile.ProfileRepository
import com.mangotv.app.data.provider.BlockedGenresRepository
import com.mangotv.app.data.provider.HomeCacheRepository
import com.mangotv.app.data.provider.HomeRowPreferencesRepository
import com.mangotv.app.data.provider.MyListRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Moves this device from one profile to another (ArcTV Plus profiles). Each profile has its own library, and every local cache holds
 * the active profile's, so a switch is the same sweep as an account switch ([AccountSwitchCoordinator]) without the sign-out:
 *
 * 1. Send anything still queued, under the profile it was made on (bounded, so a dead network can't hang the switch).
 * 2. Forget every local cache and queue, so nothing of the old profile can show up for, or be pushed as, the new one.
 * 3. Record the new profile as active, then pull its library from the account.
 *
 * The order matters if the app is killed half way: caches are emptied before the new profile is recorded, so a restart comes back
 * on the old profile with empty caches and simply pulls them again, never on the new profile with the old one's data.
 *
 * The account's one-time first-sync state is left alone (it is about the account, not a profile); the watched-history catch-up is
 * reset, because each profile has its own history and My List.
 */
class ProfileSwitcher(
    private val profileRepository: ProfileRepository,
    private val watchedBackfillState: WatchedBackfillState,
    private val myListRepository: MyListRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val lastSourceRepository: LastSourceRepository,
    private val addonRepository: AddonRepository,
    private val homeRowPreferencesRepository: HomeRowPreferencesRepository,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
    private val blockedGenresRepository: BlockedGenresRepository,
    private val feedbackRepository: FeedbackRepository,
    private val homeCacheRepository: HomeCacheRepository,
    private val settingsSyncRepository: SettingsSyncRepository,
    private val watchlistSyncRepository: WatchlistSyncRepository,
    private val continueWatchingSyncRepository: ContinueWatchingSyncRepository,
    private val addonSyncRepository: AddonSyncRepository,
    private val syncManager: SyncManager
) {
    /** Opens [profileId]. The caller has already checked its PIN (and that the account has Plus). */
    suspend fun switchTo(profileId: String) {
        flushPending()
        wipeLocalLibrary()
        profileRepository.setActive(profileId)
        syncManager.syncAll()
        // A profile with no addons of its own yet (a new one) starts with the default addon, like a fresh install.
        addonRepository.bootstrapDefaultForNewProfile()
    }

    /**
     * The active profile changed underneath this device (removed elsewhere, or Plus lapsed; [ProfileRepository] has already moved to
     * the account's own profile): the caches are another profile's, so forget them without trying to send them anywhere. The sync
     * that called this loads the right library next.
     */
    suspend fun forgetLocalLibrary() = wipeLocalLibrary()

    private suspend fun flushPending() {
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
            coroutineScope {
                launch { settingsSyncRepository.retryPending() }
                launch { watchlistSyncRepository.retryPending() }
                launch { continueWatchingSyncRepository.retryPending() }
                launch { addonSyncRepository.retryPending() }
                launch { feedbackRepository.retryPending() }
            }
        }
    }

    private suspend fun wipeLocalLibrary() {
        coroutineScope {
            launch { myListRepository.clear() }
            launch { continueWatchingRepository.clear() }
            launch { lastSourceRepository.clear() }
            launch { addonRepository.clear() }
            launch { homeRowPreferencesRepository.clear() }
            launch { playerPreferencesRepository.clear() }
            launch { blockedGenresRepository.clear() }
            launch { feedbackRepository.clear() }
            launch { homeCacheRepository.clear() }
            launch { feedbackRepository.clearPending() }
            launch { settingsSyncRepository.clearPending() }
            launch { watchlistSyncRepository.clearPending() }
            launch { continueWatchingSyncRepository.clearPending() }
            launch { addonSyncRepository.clearPending() }
            launch { watchedBackfillState.reset() }
        }
    }

    private companion object {
        const val FLUSH_TIMEOUT_MS = 5_000L
    }
}
