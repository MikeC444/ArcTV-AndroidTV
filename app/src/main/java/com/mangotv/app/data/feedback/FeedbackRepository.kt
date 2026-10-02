package com.mangotv.app.data.feedback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.profile.ActiveProfile
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.FeedbackApiClient
import com.mangotv.app.data.network.FeedbackDto
import com.mangotv.app.data.recommend.Feedback
import com.mangotv.app.data.sync.PendingChangeStore
import com.mangotv.app.util.Iso8601
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.feedbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_feedback")

/** One movie's Like / Not for me as stored on this device. */
@Serializable
data class FeedbackEntry(
    /** [Feedback.wire]. */
    val value: String,
    val title: String,
    /** When it was given -- the last-write-wins timestamp. */
    val at: String,
    val providerId: String,
    /** True once the server has acknowledged exactly this entry. Entries without it (given offline) are pushed on the next sync. */
    val synced: Boolean = false
) {
    val feedback: Feedback? get() = Feedback.fromWire(value)
}

/** What the UI hands over to say which movie feedback is about. */
data class FeedbackTarget(val id: String, val title: String, val providerId: String?)

@Serializable
private data class PendingFeedback(val clear: Boolean, val dto: FeedbackDto)

/**
 * Explicit taste feedback (Like / Not for me), the input to "Picked for you". Kept on this device at once and synced to
 * the account so every device agrees: changes are pushed immediately, queued when offline, and reconciled
 * last-write-wins on their own timestamp -- the same behaviour as the web app's feedback store, against the same backend
 * endpoint. Only movies carry feedback; feedback is kept per profile (the active one, see [ActiveProfile]).
 */
class FeedbackRepository(context: Context, private val authRepository: AuthRepository) {

    /** The profile this device is on: Likes are per profile, and a profile switch wipes this cache (see ProfileSwitcher), so they always belong to it. */
    private val profileId: String get() = ActiveProfile.id


    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), FeedbackEntry.serializer())
    private val apiClient = FeedbackApiClient(BuildConfig.API_BASE_URL)
    private val pendingStore = PendingChangeStore(context, "mango_feedback_pending", PendingFeedback.serializer())
    private val lock = Mutex()

    private val _entries = MutableStateFlow<Map<String, FeedbackEntry>>(emptyMap())

    /** Feedback by movie id. */
    val entries: StateFlow<Map<String, FeedbackEntry>> = _entries.asStateFlow()

    init {
        scope.launch { lock.withLock { _entries.value = readPersisted() } }
    }

    /** Sets the feedback for [movie], or clears it when [value] is null. */
    suspend fun set(movie: FeedbackTarget, value: Feedback?) {
        val (dto, clearing) = lock.withLock {
            val current = _entries.value
            val existing = current[movie.id]
            val providerId = movie.providerId ?: existing?.providerId ?: DEFAULT_PROVIDER_ID
            val at = nextTimestamp(existing?.at)
            if (value == null) {
                if (existing == null) return
                commit(current - movie.id)
                existing.toDto(movie.id, updatedAt = at, providerId = existing.providerId) to true
            } else {
                val entry = FeedbackEntry(value = value.wire, title = movie.title, at = at, providerId = providerId, synced = false)
                commit(current + (movie.id to entry))
                entry.toDto(movie.id, updatedAt = at, providerId = providerId) to false
            }
        }
        scope.launch { push(dto, clearing) }
    }

    /** Pressing Like when already liked clears it; pressing it when disliked switches it. */
    suspend fun toggle(movie: FeedbackTarget, value: Feedback) {
        set(movie, if (_entries.value[movie.id]?.feedback == value) null else value)
    }

    /**
     * Reads this account's feedback and reconciles it with this device's, last-write-wins. Returns false when it could
     * not be read. Called by SyncManager on login/launch.
     */
    suspend fun pullFromServer(): Boolean {
        val toPush = mutableListOf<FeedbackDto>()
        try {
            val token = freshAccessTokenOrNull() ?: return false
            val response = apiClient.list(token, profileId)
            val pending = pendingStore.all()
            val remote = response.items.filter { it.contentType == MOVIE && it.profileId == profileId }.associateBy { it.contentId }
            lock.withLock {
                val next = LinkedHashMap<String, FeedbackEntry>()
                for ((id, local) in _entries.value) {
                    val server = remote[id]
                    val key = naturalKey(local.providerId, id)
                    if (key in pending) {
                        next[id] = local // a change made here that has not reached the server yet wins
                    } else if (server == null) {
                        if (local.synced) continue // acknowledged once and gone now: cleared on another device
                        next[id] = local // never reached the server (given offline): send it up
                        toPush += local.toDto(id, local.at, local.providerId)
                    } else if (!local.synced && millis(local.at) > millis(server.updatedAt)) {
                        next[id] = local // given here after the server's version
                        toPush += local.toDto(id, local.at, local.providerId)
                    }
                }
                for ((id, server) in remote) {
                    if (id in next) continue
                    if (naturalKey(server.providerId, id) in pending) continue
                    next[id] = FeedbackEntry(server.feedback, server.title, server.updatedAt, server.providerId, synced = true)
                }
                commit(next)
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
        toPush.forEach { dto -> scope.launch { push(dto, clear = false) } }
        return true
    }

    /** Retries every change this device failed to push. Called by SyncManager after a pull, when connectivity returns, and periodically. */
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
        for ((key, change) in pending) {
            try {
                if (change.clear) apiClient.clear(token, change.dto)?.let { reconcile(it) } else reconcile(apiClient.set(token, change.dto))
                pendingStore.remove(key)
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    authRepository.clearSessionOnConfirmedUnauthorized()
                    return
                }
            } catch (e: IOException) {
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave it queued, try the rest.
            }
        }
    }

    /** Drops queued pushes (account switching): a queued push belongs to the account it was made for. */
    suspend fun clearPending() = pendingStore.clear()

    /** Forgets this device's copy (account switching). Nothing is pushed. */
    suspend fun clear() = lock.withLock { commit(emptyMap()) }

    // -- internals -------------------------------------------------------------------------------------------

    private suspend fun push(dto: FeedbackDto, clear: Boolean) {
        val key = naturalKey(dto.providerId, dto.contentId)
        try {
            val token = freshAccessTokenOrNull()
            if (token == null) {
                pendingStore.put(key, PendingFeedback(clear, dto))
                return
            }
            if (clear) apiClient.clear(token, dto)?.let { reconcile(it) } else reconcile(apiClient.set(token, dto))
            pendingStore.remove(key)
        } catch (e: ApiException) {
            pendingStore.put(key, PendingFeedback(clear, dto))
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            pendingStore.put(key, PendingFeedback(clear, dto))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendingStore.put(key, PendingFeedback(clear, dto))
        }
    }

    /** Applies the server's authoritative row for one movie (it may have lost a last-write-wins race to another device). */
    private suspend fun reconcile(dto: FeedbackDto) {
        if (dto.profileId != profileId) return
        lock.withLock {
            val current = _entries.value
            commit(
                if (dto.deletedAt != null) current - dto.contentId
                else current + (dto.contentId to FeedbackEntry(dto.feedback, dto.title, dto.updatedAt, dto.providerId, synced = true))
            )
        }
    }

    private suspend fun commit(next: Map<String, FeedbackEntry>) {
        _entries.value = next
        withContext(Dispatchers.IO) {
            appContext.feedbackDataStore.edit { it[KEY] = json.encodeToString(mapSerializer, next) }
        }
    }

    private suspend fun readPersisted(): Map<String, FeedbackEntry> {
        val raw = appContext.feedbackDataStore.data.first()[KEY] ?: return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }

    private fun FeedbackEntry.toDto(id: String, updatedAt: String, providerId: String) = FeedbackDto(
        profileId = profileId, providerId = providerId, contentId = id, contentType = MOVIE, title = title, feedback = value, updatedAt = updatedAt
    )

    /** Strictly later than the previous write for the same movie, so two quick changes can't tie on the server. */
    private fun nextTimestamp(previous: String?): String {
        val now = System.currentTimeMillis()
        val floor = previous?.let { runCatching { millis(it) + 1 }.getOrNull() } ?: now
        return Iso8601.format(maxOf(now, floor))
    }

    private fun millis(iso: String): Long = Iso8601.parseToEpochMillis(iso)

    private fun naturalKey(providerId: String, id: String) = "$profileId|$providerId|$id"

    companion object {
        private const val MOVIE = "MOVIE"
        /** The addon id Cinemeta reports; used when a movie's provider isn't known. */
        const val DEFAULT_PROVIDER_ID = "com.linvo.cinemeta"
        private val KEY = stringPreferencesKey("feedback_json")
    }
}
