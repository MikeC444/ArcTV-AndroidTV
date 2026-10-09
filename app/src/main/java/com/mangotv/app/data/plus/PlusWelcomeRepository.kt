package com.mangotv.app.data.plus

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.plusWelcomeDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_plus_welcome")

/** Which version of the Plus welcome tour this is. A revised tour later gets a new id and so shows once more (the web app's same id). */
const val WELCOME_ID = "2026-10-features"

/** Whether the welcome may appear now. Pure, so the rule is testable. [alwaysShow] is for emulators, where it appears on every launch. */
fun welcomeDue(seen: String?, shownThisSession: Boolean, alwaysShow: Boolean = false): Boolean =
    !shownThisSession && (alwaysShow || seen != WELCOME_ID)

/** True on an Android emulator, where the welcome is shown on every launch so it can be looked at. */
fun runningOnEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") || Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK") || Build.PRODUCT.contains("sdk_gphone") || Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")

/**
 * Remembers, per account on this device, whether the one-time "Everything in ArcTV Plus" welcome has been seen (ported from the web app's
 * `plusWelcome.ts`). Whether it is *eligible* (signed in, has Plus, not a kids profile, on Home) is decided by the caller. [shownThisSession] is
 * in memory only, so it never shows twice in one launch.
 */
class PlusWelcomeRepository(context: Context, private val authRepository: AuthRepository) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val mutex = Mutex()

    private var seenByUser: Map<String, String> = emptyMap()
    private var currentUser: String? = null

    /** Whether the signed-in account has been read yet (so the popup never flashes up early) and what it has seen. */
    data class State(val loaded: Boolean = false, val seen: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    var shownThisSession: Boolean = false
        private set

    init {
        scope.launch {
            mutex.withLock { seenByUser = readPersisted() }
            authRepository.session.map { it?.user?.id }.distinctUntilChanged().collect { user ->
                mutex.withLock {
                    currentUser = user
                    _state.value = if (user == null) State() else State(loaded = true, seen = seenByUser[user])
                }
            }
        }
    }

    fun markShown() {
        shownThisSession = true
    }

    /** Close or "See my Plus settings": it will not come back. */
    fun markSeen() {
        scope.launch {
            mutex.withLock {
                val user = currentUser ?: return@withLock
                seenByUser = seenByUser + (user to WELCOME_ID)
                _state.value = State(loaded = true, seen = WELCOME_ID)
                val raw = json.encodeToString(serializer, seenByUser)
                withContext(Dispatchers.IO) { appContext.plusWelcomeDataStore.edit { it[KEY] = raw } }
            }
        }
    }

    private suspend fun readPersisted(): Map<String, String> {
        val raw = appContext.plusWelcomeDataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        val KEY = stringPreferencesKey("plus_welcome_json")
    }
}
