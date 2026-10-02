package com.mangotv.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GateDestination {
    data object Home : GateDestination
}

/**
 * Decides, once, how this launch starts -- the "App Launch -> Check local
 * session -> ..." flow the account system is built around. Every launch now
 * goes straight to Home: a person with a usable session is signed in, and
 * anyone else browses as a guest with the default addon (see [GuestGate]) and
 * is asked to sign in only for Play, My List, Settings and saving a title.
 * The check itself is local-only and fast (no network round trip gates
 * navigation); a session is treated as good enough to proceed on as long as
 * its refresh token hasn't expired, since the access token can always be
 * silently renewed. If the sign-in the user reached this launch with doesn't
 * hold up server-side, the *next* thing that actually needs the network to
 * succeed (starting with Milestones 6+'s data sync) is what ultimately
 * discovers that, not this screen re-litigating it here.
 */
class AuthGateViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MangoTvApplication).container
    private val authRepository = container.authRepository
    private val syncManager = container.syncManager

    private val _destination = MutableStateFlow<GateDestination?>(null)
    val destination: StateFlow<GateDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val session = authRepository.getCurrentSession()
            val hasUsableSession = session != null && session.isRefreshTokenValid()

            // A guest needs something to browse: make sure the default addon is there before Home first asks for it.
            if (!hasUsableSession) container.addonRepository.ensureDefaultAddon()

            // ArcTV Plus profiles: back onto the profile this device was last on (saved copy only, no network), before Home reads any
            // cache or any request goes out, so everything is for the right profile from the first frame.
            if (hasUsableSession) container.profileRepository.restoreFromDisk(session!!.user.id)

            _destination.value = GateDestination.Home

            if (hasUsableSession) {
                // Both fire after the navigation decision, not before —
                // this is purely about keeping the access token fresh and
                // every synced domain (settings/watchlist/continue-watching/
                // addons, plus retrying anything queued from a previous
                // offline change — see SyncManager) current for whenever
                // they're next needed; neither must ever delay getting the
                // user into the app.
                launch { authRepository.ensureFreshSession() }
                launch { syncManager.syncAll() }
            }
        }
    }
}
