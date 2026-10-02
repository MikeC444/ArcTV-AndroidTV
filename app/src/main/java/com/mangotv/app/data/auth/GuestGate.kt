package com.mangotv.app.data.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the person is browsing without an account, and the one place that turns "this needs an account" into a
 * request to sign in. Visitors can browse Home, Movies, TV Shows, Genres, Search and title pages with the default
 * addon; Play, My List, Settings and saving a title (My List, Watched) ask them to sign in first.
 *
 * Starts out assuming a signed-in person until the stored session has been read, so a signed-in person is never
 * bounced to sign-in, or shown a "Sign In" tab, during the first moments after launch.
 */
class GuestGate(authRepository: AuthRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isGuest: StateFlow<Boolean> = combine(authRepository.session, authRepository.sessionLoaded) { session, loaded ->
        isGuestSession(loaded, session)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _signInRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits each time a guest tries something that needs an account; MangoNavHost shows the sign-in screens in response. */
    val signInRequests: SharedFlow<Unit> = _signInRequests.asSharedFlow()

    /** Runs [action] for a signed-in person. A guest is asked to sign in instead, and false is returned. */
    fun requireAccount(action: () -> Unit): Boolean {
        if (isGuest.value) {
            _signInRequests.tryEmit(Unit)
            return false
        }
        action()
        return true
    }
}

/** A guest is someone whose stored session has been read and is missing or can no longer be renewed. */
fun isGuestSession(loaded: Boolean, session: Session?): Boolean =
    loaded && (session == null || !session.isRefreshTokenValid())
