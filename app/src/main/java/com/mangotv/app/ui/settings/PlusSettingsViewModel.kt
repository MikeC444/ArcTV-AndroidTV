package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.plus.PlusStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Where a purchase is: nothing started, asking the backend for a payment page, showing its QR code, or an error. */
sealed interface PlusCheckoutState {
    data object Idle : PlusCheckoutState
    data class Starting(val plan: String) : PlusCheckoutState
    /** The payment page's URL, shown as a QR code to scan with a phone. */
    data class ShowingQr(val plan: String, val url: String) : PlusCheckoutState
    data class Error(val message: String) : PlusCheckoutState
}

/** How often, and for how long, the TV asks whether the payment has gone through once a QR code is up. */
private const val POLL_EVERY_MS = 4_000L
private const val POLL_FOR_MS = 10 * 60_000L

/** Backs Settings > Arc TV Plus: the account's status, and buying a plan by scanning a QR code with a phone. */
class PlusSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val plusRepository = (application as MangoTvApplication).container.plusRepository

    val status: StateFlow<PlusStatus> = plusRepository.status

    private val _checkout = MutableStateFlow<PlusCheckoutState>(PlusCheckoutState.Idle)
    val checkout: StateFlow<PlusCheckoutState> = _checkout.asStateFlow()

    private var polling: Job? = null

    init {
        // A fresh read whenever this tab opens, so what it shows is current.
        viewModelScope.launch { plusRepository.pullFromServer() }
    }

    fun choose(plan: String) {
        if (_checkout.value is PlusCheckoutState.Starting) return
        _checkout.value = PlusCheckoutState.Starting(plan)
        viewModelScope.launch {
            try {
                val url = plusRepository.startCheckout(plan)
                _checkout.value = PlusCheckoutState.ShowingQr(plan, url)
                startPolling()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _checkout.value = PlusCheckoutState.Error(
                    when (e.statusCode) {
                        409 -> "You already have Plus for life."
                        503 -> "Plus checkout isn't available yet."
                        else -> "Couldn't start checkout. Try again in a moment."
                    }
                )
            } catch (e: IOException) {
                _checkout.value = PlusCheckoutState.Error("Couldn't reach the server. Check your connection and try again.")
            } catch (e: Exception) {
                _checkout.value = PlusCheckoutState.Error("Couldn't start checkout. Try again in a moment.")
            }
        }
    }

    fun cancelCheckout() {
        polling?.cancel()
        _checkout.value = PlusCheckoutState.Idle
    }

    /** Asks every few seconds whether Plus is on yet; closes the QR code by itself when it is. */
    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch {
            val until = System.currentTimeMillis() + POLL_FOR_MS
            while (System.currentTimeMillis() < until) {
                delay(POLL_EVERY_MS)
                plusRepository.pullFromServer()
                if (plusRepository.status.value.owned) {
                    _checkout.value = PlusCheckoutState.Idle
                    return@launch
                }
            }
            _checkout.value = PlusCheckoutState.Idle
        }
    }

    override fun onCleared() {
        polling?.cancel()
        super.onCleared()
    }
}
