package com.mangotv.app.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.lastSourceDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_last_source")

/**
 * Remembers which Stream the user's playback actually reached "watching"
 * status on (same threshold PlayerViewModel.reportProgress already uses to
 * decide whether a Continue Watching entry exists at all) for each exact
 * title/episode -- keyed the same way, but deliberately its own local-only
 * store rather than a field on ContinueWatchingEntry: Milestone 8's sync
 * protocol (ContinueWatchingSyncRepository/WatchProgressRequest/the server
 * schema) only knows about position/duration, and a per-device "which addon
 * source worked" pick has no business on a cross-device synced record
 * (device A's addons aren't necessarily device B's).
 *
 * SourcesViewModel reads this to skip straight back to the same source
 * when re-opening a title that's already resumable, instead of making the
 * user pick from the list again every time.
 */
/** What is remembered about the source a title was last watched on, enough to find it again when its id has changed. */
@kotlinx.serialization.Serializable
data class LastSource(
    val streamId: String,
    val providerLabel: String = "",
    val releaseTitle: String = "",
    val infoHash: String? = null
)

/**
 * Finds the source a title was last watched on in a fresh list of [streams]: the same id when it is still there, otherwise the same
 * torrent (info hash), otherwise the same release from the same addon, otherwise the same release name. An addon can hand out new ids
 * from one fetch to the next, which used to send a part-watched title back to the source list.
 */
fun matchLastSource(streams: List<com.mangotv.app.data.model.Stream>, last: LastSource): com.mangotv.app.data.model.Stream? {
    streams.firstOrNull { it.id == last.streamId }?.let { return it }
    last.infoHash?.takeIf { it.isNotBlank() }?.let { hash ->
        streams.firstOrNull { it.infoHash.equals(hash, ignoreCase = true) }?.let { return it }
    }
    if (last.releaseTitle.isBlank()) return null
    streams.firstOrNull { it.releaseTitle == last.releaseTitle && it.providerLabel == last.providerLabel }?.let { return it }
    return streams.firstOrNull { it.releaseTitle == last.releaseTitle }
}

class LastSourceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _details = MutableStateFlow<Map<String, LastSource>>(emptyMap())

    init {
        scope.launch {
            _entries.value = readPersisted()
            _details.value = readPersistedDetails()
        }
    }

    /** What is remembered about this title's last source (just the id for one saved before details were kept), or null. */
    fun findLastSource(providerId: String, contentId: String, contentType: ContentType, season: Int?, episode: Int?): LastSource? {
        val k = key(providerId, contentId, contentType, season, episode)
        return _details.value[k] ?: _entries.value[k]?.let { LastSource(streamId = it) }
    }

    /** Same as [setLastStreamId] but also remembers what the source was, so it can be found again if its id changes. */
    fun setLastSource(providerId: String, contentId: String, contentType: ContentType, season: Int?, episode: Int?, stream: com.mangotv.app.data.model.Stream) {
        scope.launch {
            val k = key(providerId, contentId, contentType, season, episode)
            val entries = _entries.value + (k to stream.id)
            val details = _details.value + (k to LastSource(stream.id, stream.providerLabel, stream.releaseTitle, stream.infoHash))
            _entries.value = entries
            _details.value = details
            persist(entries)
            persistDetails(details)
        }
    }

    /** The stream id last used for this exact title/episode, if any -- a synchronous snapshot read, same shape as ContinueWatchingRepository.findResumePoint. */
    fun findLastStreamId(providerId: String, contentId: String, contentType: ContentType, season: Int?, episode: Int?): String? =
        _entries.value[key(providerId, contentId, contentType, season, episode)]

    /**
     * Deliberately NOT suspend, unlike every other write here -- the same
     * reason ContinueWatchingSyncRepository.reportProgress documents for
     * its own non-suspend signature. PlayerViewModel.reportProgress calls
     * this from the player's dispose-time "final report" (PlayerScreen's
     * DisposableEffect.onDispose{}), which isn't a coroutine context and
     * fires right as that ViewModel's own viewModelScope may already be
     * cancelling -- a call site that used to wrap this in
     * viewModelScope.launch{} silently lost exactly this write, the one
     * report that matters most for "the source the user was actually on
     * when they left". Dispatching onto this repository's own long-lived
     * [scope] instead of relying on the caller's means the write survives
     * regardless of what's happening to the caller's own coroutine scope.
     */
    fun setLastStreamId(
        providerId: String,
        contentId: String,
        contentType: ContentType,
        season: Int?,
        episode: Int?,
        streamId: String
    ) {
        scope.launch {
            val updated = _entries.value + (key(providerId, contentId, contentType, season, episode) to streamId)
            _entries.value = updated
            persist(updated)
        }
    }

    /** Wipes the locally-cached map (Milestone 12's account switching) -- same reasoning as ContinueWatchingRepository.clear(): this device is only forgetting its own local copy. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        _entries.value = emptyMap()
        _details.value = emptyMap()
        persist(emptyMap())
        persistDetails(emptyMap())
    }

    private fun key(providerId: String, contentId: String, contentType: ContentType, season: Int?, episode: Int?): String =
        "$providerId|$contentId|${contentType.name}|${season ?: -1}|${episode ?: -1}"

    private suspend fun readPersisted(): Map<String, String> {
        val raw = appContext.lastSourceDataStore.data.first()[ENTRIES_KEY] ?: return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw)
        }.getOrDefault(emptyMap())
    }

    private suspend fun readPersistedDetails(): Map<String, LastSource> {
        val raw = appContext.lastSourceDataStore.data.first()[DETAILS_KEY] ?: return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), LastSource.serializer()), raw)
        }.getOrDefault(emptyMap())
    }

    private suspend fun persistDetails(details: Map<String, LastSource>) {
        val raw = json.encodeToString(MapSerializer(String.serializer(), LastSource.serializer()), details)
        appContext.lastSourceDataStore.edit { it[DETAILS_KEY] = raw }
    }

    private suspend fun persist(entries: Map<String, String>) {
        val raw = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), entries)
        appContext.lastSourceDataStore.edit { it[ENTRIES_KEY] = raw }
    }

    companion object {
        private val ENTRIES_KEY = stringPreferencesKey("last_source_json")
        private val DETAILS_KEY = stringPreferencesKey("last_source_details_json")
    }
}
