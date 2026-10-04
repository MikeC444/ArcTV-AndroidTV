package com.mangotv.app.data.search

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
import kotlinx.coroutines.flow.combine
import com.mangotv.app.data.profile.ActiveProfile
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_search_history")

const val RECENT_SEARCH_LIMIT = 8

/** Puts a search at the front of the list: trimmed, a repeat (any letter case) moves up instead of doubling, and only the newest few are kept. */
fun withRecentSearch(list: List<String>, query: String): List<String> {
    val q = query.trim().replace(Regex("\\s+"), " ")
    if (q.isEmpty()) return list
    return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(RECENT_SEARCH_LIMIT)
}

/**
 * The searches shown under the Search bar as "Recent searches" (ported from the web app's `recentSearches.ts`): the last [RECENT_SEARCH_LIMIT],
 * newest first, kept on this device per profile (someone browsing without an account has one shared list). The account's own profile keeps the
 * list it always had; every other profile starts empty. Signing out forgets them all ([clear]).
 */
class SearchHistoryRepository(context: Context, private val authRepository: AuthRepository) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    private val mutex = Mutex()

    private var lists: Map<String, List<String>> = emptyMap()
    private var currentKey: String = GUEST_KEY

    private val _recents = MutableStateFlow<List<String>>(emptyList())

    /** The current account's recent searches, newest first. */
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    init {
        scope.launch {
            mutex.withLock { lists = readPersisted() }
            combine(authRepository.session.map { it?.user?.id }, ActiveProfile.idFlow) { userId, profileId ->
                when {
                    userId == null -> GUEST_KEY
                    profileId == ActiveProfile.DEFAULT_ID -> userId
                    else -> "$userId:$profileId"
                }
            }.distinctUntilChanged().collect { key ->
                mutex.withLock {
                    currentKey = key
                    _recents.value = lists[key].orEmpty()
                }
            }
        }
    }

    fun add(query: String) {
        scope.launch { update { withRecentSearch(it, query) } }
    }

    fun remove(query: String) {
        scope.launch { update { list -> list.filterNot { it == query } } }
    }

    fun clearCurrent() {
        scope.launch { update { emptyList() } }
    }

    /** Forgets everything (sign-out): another account must never inherit this one's searches. */
    suspend fun clear() = mutex.withLock {
        lists = emptyMap()
        _recents.value = emptyList()
        withContext(Dispatchers.IO) { appContext.searchHistoryDataStore.edit { it.remove(KEY) } }
        Unit
    }

    private suspend fun update(change: (List<String>) -> List<String>) = mutex.withLock {
        val next = change(lists[currentKey].orEmpty())
        lists = lists + (currentKey to next)
        _recents.value = next
        val raw = json.encodeToString(serializer, lists)
        withContext(Dispatchers.IO) { appContext.searchHistoryDataStore.edit { it[KEY] = raw } }
    }

    private suspend fun readPersisted(): Map<String, List<String>> {
        val raw = appContext.searchHistoryDataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        val KEY = stringPreferencesKey("search_history_json")
        const val GUEST_KEY = "guest"
    }
}
