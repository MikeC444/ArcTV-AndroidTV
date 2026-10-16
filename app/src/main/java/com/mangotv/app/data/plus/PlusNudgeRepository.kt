package com.mangotv.app.data.plus

import android.content.Context
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.plusNudgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_plus_nudges")

/** Two unprompted Plus asks, of any kind, are at least this far apart. */
const val NUDGE_GAP_MS = 3L * 24 * 60 * 60 * 1000

/** Each kind of unprompted ask is shown at most this many times to one account, then never again. */
const val NUDGE_CAP = 3

/** The kinds of unprompted ask (the ones that open by themselves, as against the ones opened by pressing a Plus-only button). */
object NudgeTrigger {
    /** After a movie, or the last episode of a show, has been watched through. */
    const val AFTER_WATCH = "after_watch"
}

/** What has been shown to one account (kept on this device). */
@Serializable
data class NudgeRecord(
    /** Epoch ms of the last unprompted ask of any kind; 0 when there has been none. */
    val lastShownAt: Long = 0L,
    /** How many times each kind has been shown. */
    val shown: Map<String, Int> = emptyMap()
)

/** Whether an unprompted ask of this kind may open now: nothing for this account has been read yet (null) means no; else the gap since the last one has passed and this kind is under its cap. Pure, so the rules are testable. */
fun nudgeDue(record: NudgeRecord?, trigger: String, now: Long): Boolean {
    if (record == null) return false
    return now - record.lastShownAt >= NUDGE_GAP_MS && (record.shown[trigger] ?: 0) < NUDGE_CAP
}

/**
 * Remembers which unprompted Plus asks an account has been shown, per account on this device (ported from the web app's `plusNudge.ts`): at most
 * one every [NUDGE_GAP_MS], and each kind at most [NUDGE_CAP] times. Whether the ask is *eligible* (signed in, no Plus, paywall on, not a kids
 * profile) is decided by the caller. Pressing a Plus-only button always opens its ask and is not counted here. Signing out forgets everything.
 *
 * Also carries the in-memory request "something worth asking about just happened" ([afterWatchPending]), which the player raises when a title has
 * been watched through and the app's root consumes once it is back on a screen where an ask may open.
 */
class PlusNudgeRepository(context: Context, private val authRepository: AuthRepository) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), NudgeRecord.serializer())
    private val mutex = Mutex()

    private var records: Map<String, NudgeRecord> = emptyMap()
    private var currentUser: String? = null

    private val _record = MutableStateFlow<NudgeRecord?>(null)

    /** The signed-in account's record; null until it has been read (or when nobody is signed in), so an ask never flashes up early. */
    val record: StateFlow<NudgeRecord?> = _record.asStateFlow()

    private val _afterWatchPending = MutableStateFlow(false)

    /** True from the moment a title has been watched through until the root has dealt with it (shown the ask, or found it not due). */
    val afterWatchPending: StateFlow<Boolean> = _afterWatchPending.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { records = readPersisted() }
            authRepository.session.map { it?.user?.id }.distinctUntilChanged().collect { user ->
                mutex.withLock {
                    currentUser = user
                    _record.value = user?.let { records[it] ?: NudgeRecord() }
                }
            }
        }
    }

    fun requestAfterWatch() {
        _afterWatchPending.value = true
    }

    fun clearAfterWatch() {
        _afterWatchPending.value = false
    }

    /** An ask of [trigger] has just been shown: the gap starts now and this kind's count goes up. */
    fun markShown(trigger: String, now: Long = System.currentTimeMillis()) {
        scope.launch { update { it.copy(lastShownAt = now, shown = it.shown + (trigger to ((it.shown[trigger] ?: 0) + 1))) } }
    }

    /** Forgets everything (sign-out): another account must never inherit this one's count. */
    suspend fun clear() = mutex.withLock {
        records = emptyMap()
        _record.value = currentUser?.let { NudgeRecord() }
        _afterWatchPending.value = false
        withContext(Dispatchers.IO) { appContext.plusNudgeDataStore.edit { it.remove(KEY) } }
        Unit
    }

    private suspend fun update(change: (NudgeRecord) -> NudgeRecord) = mutex.withLock {
        val user = currentUser ?: return@withLock
        val next = change(records[user] ?: NudgeRecord())
        records = records + (user to next)
        _record.value = next
        val raw = json.encodeToString(serializer, records)
        withContext(Dispatchers.IO) { appContext.plusNudgeDataStore.edit { it[KEY] = raw } }
    }

    private suspend fun readPersisted(): Map<String, NudgeRecord> {
        val raw = appContext.plusNudgeDataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        val KEY = stringPreferencesKey("plus_nudge_json")
    }
}
