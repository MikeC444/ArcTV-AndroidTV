package com.mangotv.app.data.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.util.Iso8601
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.statsResetDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_stats_reset")

/** The later of two reset times: a reset only ever moves forward (an earlier or missing time never undoes a later one), as the account itself does. */
fun laterReset(local: Long?, remote: Long?): Long? = when {
    local == null -> remote
    remote == null -> local
    else -> maxOf(local, remote)
}

/**
 * When Your stats was last reset, for the profile in use (the web app's `statsResetAt`). Your stats counts only what was watched after it. Kept on
 * this device for instant reads and mirrored to the account through SettingsSyncRepository (it hooks [onLocalChange]) so every device agrees.
 * Resetting changes nothing in the watch history itself, Continue Watching or the watched ticks.
 */
class StatsResetRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _resetAtMs = MutableStateFlow<Long?>(null)

    /** Epoch ms of the last reset, or null when Your stats never was reset. */
    val resetAtMs: StateFlow<Long?> = _resetAtMs.asStateFlow()

    /** Fired after a reset made on this device (not for what the account tells us), so the account can be told. */
    @Volatile
    var onLocalChange: (() -> Unit)? = null

    init {
        scope.launch { _resetAtMs.value = laterReset(_resetAtMs.value, readPersisted()) }
    }

    /** "Reset stats": from now on Your stats counts only what is watched after this moment. */
    fun reset(now: Long = System.currentTimeMillis()) {
        scope.launch {
            update(laterReset(_resetAtMs.value, now))
            onLocalChange?.invoke()
        }
    }

    /** The account's time (a pull): kept when it is later than ours; never notifies [onLocalChange]. */
    suspend fun applyRemote(iso: String?) {
        val remote = iso?.let { runCatching { Iso8601.parseToEpochMillis(it) }.getOrNull() }
        val next = laterReset(_resetAtMs.value, remote)
        if (next != _resetAtMs.value) update(next)
    }

    /** The reset time as the account takes it, or null when there is none. */
    fun isoOrNull(): String? = _resetAtMs.value?.let { Iso8601.format(it) }

    /** Forgets the local copy (account or profile switching): the next profile's own time comes with its settings. */
    suspend fun clear() = update(null)

    private suspend fun update(next: Long?) {
        _resetAtMs.value = next
        withContext(Dispatchers.IO) {
            appContext.statsResetDataStore.edit { if (next == null) it.remove(KEY) else it[KEY] = next }
        }
    }

    private suspend fun readPersisted(): Long? = appContext.statsResetDataStore.data.first()[KEY]

    private companion object {
        val KEY = longPreferencesKey("stats_reset_at_ms")
    }
}
