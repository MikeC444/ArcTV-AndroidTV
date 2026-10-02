package com.mangotv.app.data.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.mangotv.app.data.profile.ActiveProfile
import com.mangotv.app.data.profile.KIDS_BLOCKED_GENRES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.blockedGenresDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_blocked_genres")

/**
 * Genres the person never wants to see (Settings > Blocked Genres). Kept on
 * this device for instant reads, and mirrored to the account through
 * SettingsSyncRepository (it hooks [onLocalChange]) so every device they sign
 * in on agrees. A guest's list simply stays on the device until they sign in.
 */
class BlockedGenresRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    private val _genres = MutableStateFlow<List<String>>(emptyList())

    /** The blocked genre names, as the person chose them (original casing), in the order they were blocked. */
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    /**
     * What the screens actually hide: the profile's own list, plus the fixed kids genres while a kids profile is active.
     * [genres] stays the profile's own list, since that is what Settings shows and what is saved to the account.
     */
    val effectiveGenres: StateFlow<List<String>> = combine(_genres, ActiveProfile.kids) { own, kids ->
        if (kids) own + KIDS_BLOCKED_GENRES.filter { kid -> own.none { it.trim().equals(kid, ignoreCase = true) } } else own
    }.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), SharingStarted.Eagerly, emptyList())

    /**
     * Fired after a genuine local change finishes persisting -- SettingsSyncRepository hooks this to push the account's
     * settings. Never fired from [applyRemote] or [clear], so a value pulled down can't turn around and push straight
     * back up.
     */
    var onLocalChange: (() -> Unit)? = null

    init {
        scope.launch { _genres.value = readPersisted() }
    }

    /** Blocks the genre, or unblocks it if it is already blocked. Matching ignores case. */
    suspend fun toggle(genre: String) = withContext(Dispatchers.IO) {
        val key = genre.trim().lowercase()
        if (key.isEmpty()) return@withContext
        val current = _genres.value
        val next = if (current.any { it.trim().lowercase() == key }) current.filterNot { it.trim().lowercase() == key } else current + genre.trim()
        update(next)
        onLocalChange?.invoke()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        if (_genres.value.isEmpty()) return@withContext
        update(emptyList())
        onLocalChange?.invoke()
    }

    /** Applies the account's list (a pull): persists locally without notifying [onLocalChange]. */
    suspend fun applyRemote(genres: List<String>) = withContext(Dispatchers.IO) { update(genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }) }

    /** Forgets the local copy (account switching), same "don't notify" reasoning as [applyRemote]. */
    suspend fun clear() = withContext(Dispatchers.IO) { update(emptyList()) }

    private suspend fun update(next: List<String>) {
        _genres.value = next
        appContext.blockedGenresDataStore.edit { it[KEY] = json.encodeToString(serializer, next) }
    }

    private suspend fun readPersisted(): List<String> {
        val raw = appContext.blockedGenresDataStore.data.first()[KEY] ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val KEY = stringPreferencesKey("blocked_genres_json")
    }
}
