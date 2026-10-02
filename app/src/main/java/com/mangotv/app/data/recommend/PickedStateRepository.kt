package com.mangotv.app.data.recommend

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.profile.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.pickedStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_picked_state")

/** What is remembered for one account's one profile. */
@Serializable
data class PickedEntry(
    /** Titles taken out of "Picked for you" by hand. */
    val dismissed: List<String> = emptyList(),
    /** What the previous launch showed, so this launch moves away from it. */
    val shown: List<String> = emptyList()
)

/**
 * Two small things "Picked for you" keeps per account and profile (ported from the web app's `pickedDismissed.ts` and `previousShown`):
 *
 * - [dismissed]: titles taken out of the row with "Remove from Picked for you". They stay out of the row, but this is NOT feedback: no
 *   taste signal, no score changes, no sync; it is only a list kept on this device.
 * - what the previous launch showed ([previousShown], read once per launch; [rememberShown] saves this launch's row), which the
 *   rotation step uses to swap most of the row each time.
 *
 * It follows the signed-in account and the active profile by itself (they are the key), so a profile switch shows that profile's list.
 * Signing out forgets everything ([clear]).
 */
class PickedStateRepository(context: Context, private val authRepository: AuthRepository, private val profileRepository: ProfileRepository) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), PickedEntry.serializer())
    private val mutex = Mutex()

    private var entries: Map<String, PickedEntry> = emptyMap()
    private var currentKey: String? = null
    private var previousMemo: Set<String>? = null

    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())

    /** The active profile's hand-removed titles. */
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { entries = readPersisted() }
            combine(authRepository.session.map { it?.user?.id }, profileRepository.state.map { it.activeId }) { user, profile ->
                user?.let { "$it|$profile" }
            }.distinctUntilChanged().collect { key ->
                mutex.withLock {
                    currentKey = key
                    previousMemo = null
                    _dismissed.value = key?.let { entries[it]?.dismissed?.toSet() } ?: emptySet()
                }
            }
        }
    }

    /** Keeps [id] out of the row from now on. */
    suspend fun dismiss(id: String) = mutex.withLock {
        val key = currentKey ?: return@withLock
        val entry = entries[key] ?: PickedEntry()
        if (id in entry.dismissed) return@withLock
        save(key, entry.copy(dismissed = (entry.dismissed + id).takeLast(MAX_DISMISSED)))
        _dismissed.value = entries[key]?.dismissed?.toSet() ?: emptySet()
    }

    /** The ids the previous launch showed (read once per launch, so they don't change as this launch's row is rebuilt). */
    suspend fun previousShown(): Set<String> = mutex.withLock {
        previousMemo ?: (currentKey?.let { entries[it]?.shown?.toSet() } ?: emptySet()).also { previousMemo = it }
    }

    /** Remembers what this launch showed, for the next launch to move away from. */
    suspend fun rememberShown(ids: List<String>) = mutex.withLock {
        val key = currentKey ?: return@withLock
        save(key, (entries[key] ?: PickedEntry()).copy(shown = ids))
    }

    /** Forgets everything (sign-out): another account must never inherit this one's choices. */
    suspend fun clear() = mutex.withLock {
        entries = emptyMap()
        previousMemo = null
        _dismissed.value = emptySet()
        withContext(Dispatchers.IO) { appContext.pickedStateDataStore.edit { it.remove(KEY) } }
        Unit
    }

    private suspend fun save(key: String, entry: PickedEntry) {
        entries = entries + (key to entry)
        val raw = json.encodeToString(serializer, entries)
        withContext(Dispatchers.IO) { appContext.pickedStateDataStore.edit { it[KEY] = raw } }
    }

    private suspend fun readPersisted(): Map<String, PickedEntry> {
        val raw = appContext.pickedStateDataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        val KEY = stringPreferencesKey("picked_state_json")
        const val MAX_DISMISSED = 500
    }
}
