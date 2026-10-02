package com.mangotv.app.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.ProfileApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_profiles")

/** What this device knows about the account's profiles. */
data class ProfilesState(
    /** False until the list has been read (or its last copy loaded). */
    val ready: Boolean = false,
    /** False when the backend has no profiles yet (it answered 404): everyone then has the one implicit profile. */
    val supported: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val activeId: String = ActiveProfile.DEFAULT_ID,
    /** True once a profile has been picked since the app started (the picker is shown once per launch, not on every screen). */
    val chosen: Boolean = false,
    /** Why the list isn't available, in words (shown in Settings > Account). Null when it loaded fine. */
    val problem: String? = null
) {
    val active: Profile? get() = profiles.firstOrNull { it.id == activeId }
}

/** The copy kept on the device so a launch (even offline) comes back on the same profile. Per account: another account never reads it. */
@Serializable
private data class StoredProfiles(
    val userId: String,
    val supported: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val activeId: String = ActiveProfile.DEFAULT_ID
)

/** Why a profile couldn't be opened or changed, in words for the screen. */
class ProfileException(message: String, val code: Code) : Exception(message) {
    enum class Code { WRONG_PIN, LOCKED, PLUS_REQUIRED, LIMIT, OTHER }
}

/**
 * The account's profiles (ArcTV Plus) and which one this device is on. The backend holds the list; this keeps the last copy and the
 * active profile id per account, and is the only writer of [ActiveProfile]. Switching libraries is not done here (see
 * ProfileSwitcher): [setActive] only records the choice.
 *
 * A PIN is checked by asking the backend (`verify-pin`), which counts wrong tries per profile and locks guessing after five. Unlike
 * the web app, where the server keeps the active profile in a cookie the browser can't change, a TV talks to the backend directly,
 * so the PIN is a household gate in this app rather than something the backend can enforce on every request.
 */
class ProfileRepository(context: Context, private val authRepository: AuthRepository) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val apiClient = ProfileApiClient(BuildConfig.API_BASE_URL)

    private val _state = MutableStateFlow(ProfilesState())
    val state: StateFlow<ProfilesState> = _state.asStateFlow()

    /**
     * Brings this device back onto the profile it was last on, from the saved copy only (no network): called before anything is
     * loaded so every request, and every cache read, is for the right profile. Does nothing for another account's copy.
     */
    suspend fun restoreFromDisk(userId: String) {
        val stored = readStored()?.takeIf { it.userId == userId } ?: return
        applyActive(stored.activeId, stored.profiles)
        _state.value = _state.value.copy(ready = true, supported = stored.supported, profiles = stored.profiles, activeId = activeIdOrDefault(stored.activeId, stored.profiles))
    }

    /**
     * Reads the account's profiles. [plus] says whether the account has Plus right now: without it only the account's own profile is
     * usable, and a device left on another one goes back to it. Returns true when the active profile changed under this call (it was
     * removed elsewhere, or Plus lapsed), which means the library caches belong to a different profile and must be reloaded.
     * Returns false when nothing changed or the list couldn't be read (the saved copy stays).
     */
    suspend fun pullFromServer(plus: Boolean): Boolean {
        val session = authRepository.getCurrentSession() ?: return false
        try {
            if (!authRepository.ensureFreshSession()) return false
            val token = authRepository.getCurrentSession()?.accessToken ?: return false
            val all = apiClient.list(token)
            val usable = visibleProfiles(all, plus)
            val before = ActiveProfile.id
            val activeId = activeIdOrDefault(before, usable)
            applyActive(activeId, all)
            _state.value = _state.value.copy(ready = true, supported = true, profiles = all, activeId = activeId, problem = null)
            store(session.user.id)
            return activeId != before
        } catch (e: ApiException) {
            if (e.statusCode == 404) {
                // An older backend: one implicit profile, exactly as before profiles existed.
                val before = ActiveProfile.id
                applyActive(ActiveProfile.DEFAULT_ID, emptyList())
                _state.value = _state.value.copy(ready = true, supported = false, profiles = emptyList(), activeId = ActiveProfile.DEFAULT_ID, problem = "the ArcTV service doesn't have profiles yet (it answered 404)")
                store(session.user.id)
                return before != ActiveProfile.DEFAULT_ID
            }
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            noteProblem("the ArcTV service answered HTTP ${e.statusCode}")
            return false
        } catch (e: IOException) {
            // Offline or the service is down: keep the saved copy.
            noteProblem("couldn't reach the ArcTV service (${e.javaClass.simpleName})")
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unexpected (e.g. a malformed answer): same as being offline.
            noteProblem("unexpected answer (${e.javaClass.simpleName}: ${e.message.orEmpty().take(80)})")
            return false
        }
        _state.value = _state.value.copy(ready = true)
        return false
    }

    /** Remembers why the profile list couldn't be read, for Settings > Account (the saved copy, if any, stays in use). */
    fun noteProblem(problem: String) {
        _state.value = _state.value.copy(ready = true, problem = problem)
    }

    /** Records which profile this device is on (the caller has already checked the PIN and swapped the library). */
    suspend fun setActive(profileId: String) {
        val profiles = _state.value.profiles
        applyActive(profileId, profiles)
        _state.value = _state.value.copy(activeId = profileId, chosen = true)
        authRepository.getCurrentSession()?.let { store(it.user.id) }
    }

    /** The picker has been answered without a different profile being chosen (the person kept the one they were on). */
    fun markChosen() {
        _state.value = _state.value.copy(chosen = true)
    }

    /** Checks a profile's PIN with the backend. Throws [ProfileException] for a wrong or locked one. */
    suspend fun verifyPin(profileId: String, pin: String) {
        call { token -> apiClient.verifyPin(token, profileId, pin) }
    }

    suspend fun create(name: String, avatar: String, kind: String, pin: String?): Profile {
        if (_state.value.profiles.size >= PROFILE_LIMIT) throw ProfileException("An account can have up to $PROFILE_LIMIT profiles.", ProfileException.Code.LIMIT)
        val created = call { token -> apiClient.create(token, name, avatar, kind, pin) }
        refresh()
        return created
    }

    /** Changes a profile. If it is locked, the caller must have checked its current PIN with [verifyPin] first. */
    suspend fun update(profileId: String, name: String?, avatar: String?, kind: String?, pin: PinChange): Profile {
        val updated = call { token -> apiClient.update(token, profileId, name, avatar, kind, pin) }
        refresh()
        // The active profile may just have become a kids profile (or stopped being one).
        if (profileId == ActiveProfile.id) applyActive(profileId, _state.value.profiles)
        return updated
    }

    /** Removes a profile and its library. If it is locked, the caller must have checked its current PIN first. */
    suspend fun remove(profileId: String) {
        call { token -> apiClient.delete(token, profileId) }
        refresh()
    }

    /** Reads the list again (after a change, or when the picker opens), without moving this device onto another profile. */
    suspend fun refresh() {
        val session = authRepository.getCurrentSession() ?: return
        val token = session.accessToken
        val all = runCatching { apiClient.list(token) }.getOrNull() ?: return
        _state.value = _state.value.copy(profiles = all)
        store(session.user.id)
    }

    /** Forgets everything (sign-out): another account must never inherit this one's profiles or choice. */
    suspend fun clear() {
        ActiveProfile.reset()
        _state.value = ProfilesState()
        withContext(Dispatchers.IO) { appContext.profileDataStore.edit { it.remove(KEY) } }
    }

    private suspend fun <T> call(block: suspend (token: String) -> T): T {
        try {
            if (!authRepository.ensureFreshSession()) throw ProfileException("Sign in to continue.", ProfileException.Code.OTHER)
            val token = authRepository.getCurrentSession()?.accessToken ?: throw ProfileException("Sign in to continue.", ProfileException.Code.OTHER)
            return block(token)
        } catch (e: ApiException) {
            when {
                e.statusCode == 401 -> {
                    authRepository.clearSessionOnConfirmedUnauthorized()
                    throw ProfileException("Your session has expired. Please sign in again.", ProfileException.Code.OTHER)
                }
                e.statusCode == 403 && e.message.orEmpty().contains("PIN", ignoreCase = true) -> throw ProfileException("That PIN isn't right.", ProfileException.Code.WRONG_PIN)
                e.statusCode == 403 -> throw ProfileException("Profiles are part of ArcTV Plus.", ProfileException.Code.PLUS_REQUIRED)
                e.statusCode == 429 -> throw ProfileException("Too many wrong PINs. Try again in a few minutes.", ProfileException.Code.LOCKED)
                e.statusCode == 400 -> throw ProfileException(e.message ?: "That isn't allowed.", ProfileException.Code.OTHER)
                else -> throw ProfileException("Something went wrong. Please try again.", ProfileException.Code.OTHER)
            }
        } catch (e: IOException) {
            throw ProfileException("Can't reach ArcTV right now. Check your connection.", ProfileException.Code.OTHER)
        }
    }

    private fun applyActive(profileId: String, profiles: List<Profile>) {
        ActiveProfile.set(profileId, kids = profiles.firstOrNull { it.id == profileId }?.isKids == true)
    }

    private fun activeIdOrDefault(wanted: String, profiles: List<Profile>): String =
        if (profiles.any { it.id == wanted }) wanted else ActiveProfile.DEFAULT_ID

    private suspend fun store(userId: String) {
        val current = _state.value
        val stored = StoredProfiles(userId, current.supported, current.profiles, current.activeId)
        withContext(Dispatchers.IO) { appContext.profileDataStore.edit { it[KEY] = json.encodeToString(StoredProfiles.serializer(), stored) } }
    }

    private suspend fun readStored(): StoredProfiles? = withContext(Dispatchers.IO) {
        val raw = appContext.profileDataStore.data.first()[KEY] ?: return@withContext null
        runCatching { json.decodeFromString(StoredProfiles.serializer(), raw) }.getOrNull()
    }

    private companion object {
        val KEY = stringPreferencesKey("profiles_json")
    }
}
