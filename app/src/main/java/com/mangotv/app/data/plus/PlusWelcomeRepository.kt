package com.mangotv.app.data.plus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.plusWelcomeDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_plus_welcome_once")

/** Whether the welcome may appear now. Pure, so the rule is testable. It is shown once ever on a device. */
fun welcomeDue(seen: Boolean, shownThisSession: Boolean): Boolean = !shownThisSession && !seen

/**
 * Remembers whether the one-time "Everything in ArcTV Plus" welcome has ever been seen on this device (the same for every account, and it
 * survives signing out). Whether it is *eligible* (signed in, has Plus, not a kids profile, on Home) is decided by the caller.
 * [shownThisSession] is in memory only, so it never shows twice in one launch.
 */
class PlusWelcomeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether the stored answer has been read yet (so the popup never flashes up early) and what it is. */
    data class State(val loaded: Boolean = false, val seen: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    var shownThisSession: Boolean = false
        private set

    init {
        scope.launch {
            val seen = appContext.plusWelcomeDataStore.data.first()[KEY] == true
            _state.value = State(loaded = true, seen = seen)
        }
    }

    fun markShown() {
        shownThisSession = true
    }

    /** Close or "See my Plus settings": it will not come back. */
    fun markSeen() {
        _state.value = State(loaded = true, seen = true)
        scope.launch { appContext.plusWelcomeDataStore.edit { it[KEY] = true } }
    }

    private companion object {
        val KEY = booleanPreferencesKey("plus_welcome_seen")
    }
}
