package com.mangotv.app.data.recommend

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.featureCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_recommend_features")

@Serializable
private data class CachedFeatures(val g: List<String>, val d: List<String>, val c: List<String>, val at: Long)

/**
 * Movie features outlive a refresh: a small persistent cache on this device (the web app keeps the same cache in the
 * browser), so a "Picked for you" refresh rarely touches the network. Bounded in size and age.
 */
class FeatureCacheRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), CachedFeatures.serializer())
    private val lock = Mutex()
    private var memory: MutableMap<String, CachedFeatures>? = null

    private suspend fun store(): MutableMap<String, CachedFeatures> {
        memory?.let { return it }
        val raw = withContext(Dispatchers.IO) { appContext.featureCacheDataStore.data.first()[KEY] }
        val loaded = raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty().toMutableMap()
        memory = loaded
        return loaded
    }

    suspend fun cached(id: String, now: Long = System.currentTimeMillis()): Features? = lock.withLock {
        val entry = store()[id] ?: return@withLock null
        if (now - entry.at > RecommendConfig.FEATURE_CACHE_TTL_MS) null else Features(entry.g, entry.d, entry.c)
    }

    private suspend fun remember(all: Map<String, Features>, now: Long = System.currentTimeMillis()) = lock.withLock {
        val store = store()
        for ((id, features) in all) store[id] = CachedFeatures(features.genres, features.directors, features.cast, now)
        if (store.size > RecommendConfig.FEATURE_CACHE_MAX_ENTRIES) {
            store.entries.sortedBy { it.value.at }.take(store.size - RecommendConfig.FEATURE_CACHE_MAX_ENTRIES).forEach { store.remove(it.key) }
        }
        val raw = json.encodeToString(serializer, store)
        withContext(Dispatchers.IO) { appContext.featureCacheDataStore.edit { it[KEY] = raw } }
    }

    /**
     * Features for the given movies: cache first, then at most `limit` lookups through [fetchOne], [concurrency] at a time.
     * A failed or empty lookup yields null and is not cached, so a later refresh can try again.
     */
    suspend fun load(
        refs: List<MovieRef>,
        limit: Int,
        fetchOne: suspend (MovieRef) -> Features?,
        concurrency: Int = RecommendConfig.DETAIL_FETCH_CONCURRENCY
    ): Map<String, Features?> {
        val out = LinkedHashMap<String, Features?>()
        val missing = ArrayList<MovieRef>()
        for (ref in refs) {
            if (out.containsKey(ref.id)) continue
            val hit = cached(ref.id)
            if (hit != null) {
                out[ref.id] = hit
            } else if (missing.size < limit) {
                out[ref.id] = null
                missing += ref
            }
        }
        if (missing.isEmpty()) return out
        val permits = Semaphore(concurrency.coerceAtLeast(1))
        val fetched = coroutineScope {
            missing.map { ref ->
                async {
                    permits.withPermit {
                        ref to runCatching { fetchOne(ref) }.getOrNull()?.takeIf { it.hasAny }
                    }
                }
            }.awaitAll()
        }
        val good = HashMap<String, Features>()
        for ((ref, features) in fetched) {
            if (features != null) {
                out[ref.id] = features
                good[ref.id] = features
            }
        }
        if (good.isNotEmpty()) remember(good)
        return out
    }

    private companion object {
        val KEY = stringPreferencesKey("features_json")
    }
}
