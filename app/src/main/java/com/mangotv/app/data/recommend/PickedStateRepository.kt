package com.mangotv.app.data.recommend

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.PickedDismissalDto
import com.mangotv.app.data.network.PickedDismissalsApiClient
import com.mangotv.app.data.sync.PendingChangeStore
import com.mangotv.app.data.profile.ActiveProfile
import com.mangotv.app.util.Iso8601
import kotlinx.coroutines.CancellationException
import java.io.IOException
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
    /** Titles removed before removals had a time (the first version of this list): each now starts its hide window the first time it is read. */
    val dismissed: List<String> = emptyList(),
    /** Title id -> when it was taken out of "Picked for you" (epoch ms). Only removals less than [RecommendConfig.PICKED_DISMISS_DAYS] old matter. */
    val removedAt: Map<String, Long> = emptyMap(),
    /** What the previous launch showed, so this launch moves away from it. */
    val shown: List<String> = emptyList()
)

/**
 * The removals of [entry] still within the hide window ([RecommendConfig.PICKED_DISMISS_DAYS] days) at [now], by title id. Titles removed
 * before removals had a time (the first version's plain list of ids) start their window at [now].
 */
fun activeRemovalsOf(entry: PickedEntry, now: Long): Map<String, Long> {
    val window = RecommendConfig.PICKED_DISMISS_DAYS * 24L * 60 * 60 * 1000
    val out = LinkedHashMap<String, Long>()
    for (id in entry.dismissed) out[id] = now
    for ((id, at) in entry.removedAt) if (now - at < window) out[id] = at
    return out
}

/**
 * What "Picked for you" keeps per account and profile (ported from the web app's `pickedDismissed.ts` and `previousShown`):
 *
 * - [dismissed]: titles taken out of the row with "Remove from Picked for you". They stay out for [RecommendConfig.PICKED_DISMISS_DAYS] days;
 *   after that a title is an ordinary candidate again and the algorithm decides whether it is still worth showing. This is NOT feedback:
 *   no taste signal and no score changes. Each removal is also saved on the account (`/user/picked-dismissals`) so every device agrees:
 *   pushed at once, queued when offline, and merged on sign-in / launch with the later removal of a title winning.
 * - what the previous launch showed ([previousShown], read once per launch; [rememberShown] saves this launch's row), which the
 *   rotation step uses to swap most of the row each time. Kept on this device only.
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
    private val apiClient = PickedDismissalsApiClient(BuildConfig.API_BASE_URL)
    private val pendingStore = PendingChangeStore(context, "mango_picked_dismissals_pending", PickedDismissalDto.serializer())

    private var entries: Map<String, PickedEntry> = emptyMap()
    private var currentKey: String? = null
    private var previousMemo: Set<String>? = null

    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())

    /** The active profile's hand-removed titles that are still within their hide window. */
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
                    _dismissed.value = key?.let { activeRemovals(it).keys } ?: emptySet()
                }
            }
        }
    }

    /** Keeps [id] out of the row for [RecommendConfig.PICKED_DISMISS_DAYS] days, and tells the account. */
    suspend fun dismiss(id: String) {
        val dto = mutex.withLock {
            val key = currentKey ?: return
            val now = System.currentTimeMillis()
            val removals = activeRemovals(key, now) + (id to now)
            save(key, (entries[key] ?: PickedEntry()).copy(dismissed = emptyList(), removedAt = removals.trimmed()))
            _dismissed.value = removals.keys
            PickedDismissalDto(profileId = ActiveProfile.id, contentId = id, dismissedAt = Iso8601.format(now))
        }
        scope.launch { push(dto) }
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

    /**
     * Reads this account's removals and merges them with this device's: the later removal of a title wins, removals older than the hide
     * window are dropped, and one only this device has is sent up. Returns false when it could not be read. Called by SyncManager on login/launch.
     */
    suspend fun pullFromServer(): Boolean {
        val toPush = mutableListOf<PickedDismissalDto>()
        try {
            val token = freshAccessTokenOrNull() ?: return false
            val profile = ActiveProfile.id
            val response = apiClient.list(token, profile)
            mutex.withLock {
                val key = currentKey ?: return false
                val now = System.currentTimeMillis()
                val merged = activeRemovals(key, now).toMutableMap()
                val remote = HashMap<String, Long>()
                for (item in response.items) {
                    if (item.profileId != profile) continue
                    val at = runCatching { Iso8601.parseToEpochMillis(item.dismissedAt) }.getOrNull() ?: continue
                    remote[item.contentId] = at
                    if (now - at < dismissWindowMs && (merged[item.contentId] ?: 0L) < at) merged[item.contentId] = at
                }
                for ((id, at) in merged) if ((remote[id] ?: 0L) < at) toPush += PickedDismissalDto(profile, id, Iso8601.format(at))
                save(key, (entries[key] ?: PickedEntry()).copy(dismissed = emptyList(), removedAt = merged.trimmed()))
                _dismissed.value = merged.keys
            }
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            return false
        } catch (e: IOException) {
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        toPush.forEach { dto -> scope.launch { push(dto) } }
        return true
    }

    /** Retries every removal this device failed to send. Called by SyncManager after a pull, when connectivity returns, and before an account or profile switch. */
    suspend fun retryPending() {
        val pending = try {
            pendingStore.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        if (pending.isEmpty()) return
        val token = freshAccessTokenOrNull() ?: return
        for ((key, dto) in pending) {
            val at = runCatching { Iso8601.parseToEpochMillis(dto.dismissedAt) }.getOrNull()
            if (at == null || System.currentTimeMillis() - at >= dismissWindowMs) {
                pendingStore.remove(key) // too old to matter any more
                continue
            }
            try {
                reconcile(apiClient.save(token, dto))
                pendingStore.remove(key)
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    authRepository.clearSessionOnConfirmedUnauthorized()
                    return
                }
                // Left queued (a server that does not have the route yet answers 404); try the rest.
            } catch (e: IOException) {
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave it queued, try the rest.
            }
        }
    }

    /** Drops queued sends (account or profile switching): a queued removal belongs to the account and profile it was made for. */
    suspend fun clearPending() = pendingStore.clear()

    /** Forgets everything (sign-out): another account must never inherit this one's choices. */
    suspend fun clear() = mutex.withLock {
        entries = emptyMap()
        previousMemo = null
        _dismissed.value = emptySet()
        withContext(Dispatchers.IO) { appContext.pickedStateDataStore.edit { it.remove(KEY) } }
        Unit
    }

    // -- internals -------------------------------------------------------------------------------------------

    private val dismissWindowMs: Long get() = RecommendConfig.PICKED_DISMISS_DAYS * 24L * 60 * 60 * 1000

    /** This key's removals still within the hide window (see [activeRemovalsOf]). */
    private fun activeRemovals(key: String, now: Long = System.currentTimeMillis()): Map<String, Long> =
        entries[key]?.let { activeRemovalsOf(it, now) } ?: emptyMap()

    private fun Map<String, Long>.trimmed(): Map<String, Long> =
        if (size <= MAX_DISMISSED) this else entries.sortedByDescending { it.value }.take(MAX_DISMISSED).associate { it.key to it.value }

    private suspend fun push(dto: PickedDismissalDto) {
        val key = "${dto.profileId}|${dto.contentId}"
        try {
            val token = freshAccessTokenOrNull()
            if (token == null) {
                pendingStore.put(key, dto)
                return
            }
            reconcile(apiClient.save(token, dto))
            pendingStore.remove(key)
        } catch (e: ApiException) {
            pendingStore.put(key, dto)
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            pendingStore.put(key, dto)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendingStore.put(key, dto)
        }
    }

    /** If the server holds a later removal of the same title, this device takes that time. */
    private suspend fun reconcile(dto: PickedDismissalDto) = mutex.withLock {
        val key = currentKey ?: return@withLock
        if (dto.profileId != ActiveProfile.id) return@withLock
        val at = runCatching { Iso8601.parseToEpochMillis(dto.dismissedAt) }.getOrNull() ?: return@withLock
        val removals = activeRemovals(key)
        val mine = removals[dto.contentId] ?: return@withLock
        if (at > mine) {
            val next = removals + (dto.contentId to at)
            save(key, (entries[key] ?: PickedEntry()).copy(dismissed = emptyList(), removedAt = next.trimmed()))
            _dismissed.value = next.keys
        }
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
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
