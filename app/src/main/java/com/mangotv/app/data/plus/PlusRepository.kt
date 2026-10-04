package com.mangotv.app.data.plus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.PlusApiClient
import com.mangotv.app.data.network.PlusCheckoutLink
import com.mangotv.app.data.network.PlusStatusDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.plusDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_plus")

/** What this account has, as the backend last said. */
@Serializable
data class PlusStatus(
    val active: Boolean = false,
    /** "monthly", "yearly", "lifetime", "early_access" while the paywall is off, or null. */
    val plan: String? = null,
    val validUntil: String? = null,
    /** True once Plus is paid; false while it is free for everyone (early access). */
    val paywall: Boolean = false,
    /** A monthly or yearly subscription that has been cancelled: Plus runs to [validUntil] and then ends. */
    val cancelAtPeriodEnd: Boolean = false
) {
    /** Paid for (as opposed to free during early access). */
    val owned: Boolean get() = paywall && active
}

/**
 * ArcTV Plus for the signed-in account. The backend decides (GET /user/plus): while the paywall is off everyone has Plus
 * (early access), once it is on only paying accounts do. The last answer is kept on the device so a launch shows what the
 * person had straight away and the fresh answer replaces it a moment later. Features that need Plus read [status].active;
 * nobody signed in has none.
 */
class PlusRepository(context: Context, private val authRepository: AuthRepository) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val apiClient = PlusApiClient(BuildConfig.API_BASE_URL)

    private val _status = MutableStateFlow(PlusStatus())
    val status: StateFlow<PlusStatus> = _status.asStateFlow()

    init {
        scope.launch { _status.value = readPersisted() }
    }

    /** Reads this account's status. Returns false when it could not be read (the last known status is kept). Called by SyncManager on login/launch. */
    suspend fun pullFromServer(): Boolean {
        try {
            val token = freshAccessTokenOrNull() ?: return false
            val dto = apiClient.getStatus(token)
            update(dto.toStatus())
            return true
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            // Transient: keep the last known status.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unexpected (e.g. a malformed answer): same as a network failure.
        }
        return false
    }

    /**
     * Starts a checkout for [plan] and returns the payment page's URL (which the TV shows as a QR code to scan with a
     * phone) and what it will charge. Throws [ApiException] (409: already Plus for life; 503: checkout isn't available yet) or an IOException.
     */
    suspend fun startCheckout(plan: String): PlusCheckoutLink {
        val token = freshAccessTokenOrNull() ?: throw IOException("Not signed in")
        return apiClient.startCheckout(token, plan)
    }

    /**
     * Cancels a monthly or yearly subscription at the end of the period already paid for: Plus stays on until then. Throws [ApiException]
     * (409: Lifetime has no subscription; 404: no subscription found on the account; 429: too many tries) or an IOException.
     */
    suspend fun cancelSubscription() {
        val token = freshAccessTokenOrNull() ?: throw IOException("Not signed in")
        update(apiClient.cancelSubscription(token).toStatus())
    }

    private fun PlusStatusDto.toStatus() = PlusStatus(active = active, plan = plan, validUntil = validUntil, paywall = paywall, cancelAtPeriodEnd = cancelAtPeriodEnd)

    /** Forgets this device's copy (account switching): another account must never inherit this one's Plus. */
    suspend fun clear() = update(PlusStatus())

    private suspend fun update(next: PlusStatus) {
        _status.value = next
        withContext(Dispatchers.IO) {
            appContext.plusDataStore.edit { it[KEY] = json.encodeToString(PlusStatus.serializer(), next) }
        }
    }

    private suspend fun readPersisted(): PlusStatus {
        val raw = appContext.plusDataStore.data.first()[KEY] ?: return PlusStatus()
        return runCatching { json.decodeFromString(PlusStatus.serializer(), raw) }.getOrDefault(PlusStatus())
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }

    private companion object {
        val KEY = stringPreferencesKey("plus_status_json")
    }
}
