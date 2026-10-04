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

private val Context.plusPromoDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_plus_promo")

/** After "Close" the popup stays away this long before it may appear again. */
const val PROMO_SNOOZE_MS = 7L * 24 * 60 * 60 * 1000

/** What the person told the popup (kept per account). */
@Serializable
data class PromoRecord(
    /** "Don't show me again": never again for this account. */
    val never: Boolean = false,
    /** Epoch ms before which the popup stays hidden (set by Close). */
    val snoozedUntil: Long = 0L
)

/** Whether the popup may appear now. Pure, so the rules are testable. */
fun promoDue(record: PromoRecord, shownThisSession: Boolean, now: Long): Boolean =
    !record.never && !shownThisSession && now >= record.snoozedUntil

/**
 * Remembers what the person told the ArcTV Plus popup, per account on this device (ported from the web app's `plusPromo.ts`): Close snoozes it for
 * a week, "Don't show me again" switches it off for good. Whether it is *eligible* (signed in, no Plus, paywall on, not a kids profile) is decided by
 * the caller. [shownThisSession] is in memory only, so it never shows twice in one launch. Signing out forgets everything ([clear]).
 */
class PlusPromoRepository(context: Context, private val authRepository: AuthRepository) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), PromoRecord.serializer())
    private val mutex = Mutex()

    private var records: Map<String, PromoRecord> = emptyMap()
    private var currentUser: String? = null

    private val _record = MutableStateFlow<PromoRecord?>(null)

    /** The signed-in account's record; null until it has been read (or when nobody is signed in), so the popup never flashes up early. */
    val record: StateFlow<PromoRecord?> = _record.asStateFlow()

    @Volatile
    var shownThisSession: Boolean = false
        private set

    init {
        scope.launch {
            mutex.withLock { records = readPersisted() }
            authRepository.session.map { it?.user?.id }.distinctUntilChanged().collect { user ->
                mutex.withLock {
                    currentUser = user
                    _record.value = user?.let { records[it] ?: PromoRecord() }
                }
            }
        }
    }

    fun markShown() {
        shownThisSession = true
    }

    /** Close: hide now and come back after the snooze. */
    fun snooze(now: Long = System.currentTimeMillis()) {
        scope.launch { update { it.copy(snoozedUntil = now + PROMO_SNOOZE_MS) } }
    }

    /** Don't show me again. */
    fun dismissForever() {
        scope.launch { update { it.copy(never = true) } }
    }

    /** Forgets everything (sign-out): another account must never inherit this one's choice. */
    suspend fun clear() = mutex.withLock {
        records = emptyMap()
        _record.value = currentUser?.let { PromoRecord() }
        withContext(Dispatchers.IO) { appContext.plusPromoDataStore.edit { it.remove(KEY) } }
        Unit
    }

    private suspend fun update(change: (PromoRecord) -> PromoRecord) = mutex.withLock {
        val user = currentUser ?: return@withLock
        val next = change(records[user] ?: PromoRecord())
        records = records + (user to next)
        _record.value = next
        val raw = json.encodeToString(serializer, records)
        withContext(Dispatchers.IO) { appContext.plusPromoDataStore.edit { it[KEY] = raw } }
    }

    private suspend fun readPersisted(): Map<String, PromoRecord> {
        val raw = appContext.plusPromoDataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        val KEY = stringPreferencesKey("plus_promo_json")
    }
}
