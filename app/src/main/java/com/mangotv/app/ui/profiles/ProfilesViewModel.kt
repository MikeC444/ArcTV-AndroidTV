package com.mangotv.app.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.profile.ActiveProfile
import com.mangotv.app.data.profile.PinChange
import com.mangotv.app.data.profile.Profile
import com.mangotv.app.data.profile.ProfileException
import com.mangotv.app.data.profile.ProfilesState
import com.mangotv.app.data.profile.visibleProfiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/** What "Who's watching?" draws. */
data class ProfilesUiState(
    val ready: Boolean = false,
    val supported: Boolean = false,
    val plus: Boolean = false,
    /** What can be picked: everything with Plus, only the account's own profile without it. */
    val profiles: List<Profile> = emptyList(),
    val activeId: String = ActiveProfile.DEFAULT_ID,
    /** True once something has been picked since launch: "Back" is then allowed, since the screen was opened on purpose. */
    val chosen: Boolean = false,
    val limit: Int = com.mangotv.app.data.profile.PROFILE_LIMIT
)

/** What a screen action ended with. */
sealed interface ProfileResult {
    data object Ok : ProfileResult
    data class Failed(val message: String, val code: ProfileException.Code) : ProfileResult
}

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MangoTvApplication).container
    private val profileRepository = container.profileRepository
    private val switcher = container.profileSwitcher

    val uiState: StateFlow<ProfilesUiState> = combine(profileRepository.state, container.plusRepository.status) { state: ProfilesState, plus ->
        ProfilesUiState(
            ready = state.ready,
            supported = state.supported,
            plus = plus.active,
            profiles = visibleProfiles(state.profiles, plus.active),
            activeId = state.activeId,
            chosen = state.chosen
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfilesUiState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Reads the list again when the screen opens (it may have changed on another device). */
    init {
        viewModelScope.launch { runCatching { profileRepository.refresh() } }
    }

    /**
     * Opens [profile], after checking its PIN when it has one. Opening the profile this device is already on only records the answer;
     * any other profile swaps the local library for that profile's (see ProfileSwitcher). [onDone] gets true for a different profile.
     */
    fun open(profile: Profile, pin: String?, onResult: (ProfileResult) -> Unit, onDone: (switched: Boolean) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val result = attempt {
                if (profile.hasPin) profileRepository.verifyPin(profile.id, requireNotNull(pin))
            }
            if (result is ProfileResult.Ok) {
                val switched = profile.id != ActiveProfile.id
                if (switched) switcher.switchTo(profile.id) else profileRepository.markChosen()
                _busy.value = false
                onResult(result)
                onDone(switched)
            } else {
                _busy.value = false
                onResult(result)
            }
        }
    }

    fun create(name: String, avatar: String, kind: String, pin: String?, onResult: (ProfileResult) -> Unit) = run(onResult) {
        profileRepository.create(name, avatar, kind, pin)
    }

    /** [currentPin] is checked first when the profile is locked. */
    fun update(profile: Profile, name: String, avatar: String, kind: String?, pin: PinChange, currentPin: String?, onResult: (ProfileResult) -> Unit) = run(onResult) {
        if (profile.hasPin) profileRepository.verifyPin(profile.id, requireNotNull(currentPin))
        profileRepository.update(profile.id, name, avatar, kind, pin)
    }

    fun remove(profile: Profile, currentPin: String?, onResult: (ProfileResult) -> Unit) = run(onResult) {
        if (profile.hasPin) profileRepository.verifyPin(profile.id, requireNotNull(currentPin))
        profileRepository.remove(profile.id)
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.accountSwitchCoordinator.signOut() }
            _busy.value = false
            onSignedOut()
        }
    }

    private fun run(onResult: (ProfileResult) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val result = attempt(block)
            _busy.value = false
            onResult(result)
        }
    }

    private suspend fun attempt(block: suspend () -> Unit): ProfileResult = try {
        block()
        ProfileResult.Ok
    } catch (e: ProfileException) {
        ProfileResult.Failed(e.message ?: "Something went wrong.", e.code)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ProfileResult.Failed("Something went wrong. Please try again.", ProfileException.Code.OTHER)
    }
}
