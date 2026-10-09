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
    data class ShowingQr(val plan: String, val url: String, val priceLabel: String?, val trialDays: Int = 0) : PlusCheckoutState
    /** Payment went through; the page shows a thank-you for a moment before closing. */
    data class Done(val plan: String) : PlusCheckoutState
    data class Error(val message: String) : PlusCheckoutState
}

/** The "Cancel subscription" flow on this tab: asking first, working, or an error to show in the question. */
data class CancelSubscriptionState(val confirming: Boolean = false, val busy: Boolean = false, val error: String? = null)

/** How often, and for how long, the TV asks whether the payment has gone through once a QR code is up. */
private const val POLL_EVERY_TICKS = 4
private const val POLL_FOR_SECONDS = 10 * 60
private const val DONE_SHOWN_MS = 3_000L

/** Backs Settings > Arc TV Plus: the account's status, and buying a plan by scanning a QR code with a phone. */
class PlusSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val plusRepository = (application as MangoTvApplication).container.plusRepository

    val status: StateFlow<PlusStatus> = plusRepository.status

    private val _checkout = MutableStateFlow<PlusCheckoutState>(PlusCheckoutState.Idle)
    val checkout: StateFlow<PlusCheckoutState> = _checkout.asStateFlow()

    private val _cancel = MutableStateFlow(CancelSubscriptionState())
    val cancel: StateFlow<CancelSubscriptionState> = _cancel.asStateFlow()

    fun askToCancel() { _cancel.value = CancelSubscriptionState(confirming = true) }

    fun keepPlus() { if (!_cancel.value.busy) _cancel.value = CancelSubscriptionState() }

    /** Cancels at the end of the paid period (Plus stays on until then). Same wording for the failures as the web app. */
    fun confirmCancel() {
        if (_cancel.value.busy) return
        _cancel.value = CancelSubscriptionState(confirming = true, busy = true)
        viewModelScope.launch {
            try {
                plusRepository.cancelSubscription()
                _cancel.value = CancelSubscriptionState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val message = when {
                    e.statusCode == 409 -> "Lifetime Plus has no subscription to cancel."
                    e.statusCode == 404 && e.message.orEmpty().contains("subscription", ignoreCase = true) -> "We couldn't find a subscription to cancel on this account."
                    e.statusCode == 429 -> e.message.orEmpty()
                    else -> "Couldn't cancel your subscription. Try again in a moment."
                }
                _cancel.value = CancelSubscriptionState(confirming = true, error = message)
            } catch (e: Exception) {
                _cancel.value = CancelSubscriptionState(confirming = true, error = "Couldn't cancel your subscription. Try again in a moment.")
            }
        }
    }

    private var polling: Job? = null
    private var starting: Job? = null

    /** Seconds left before the QR code stops waiting; drives the countdown beside it. */
    private val _remainingSeconds = MutableStateFlow(POLL_FOR_SECONDS)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    init {
        // A fresh read whenever this tab opens, so what it shows is current.
        viewModelScope.launch { plusRepository.pullFromServer() }
    }

    fun choose(plan: String) {
        if (_checkout.value is PlusCheckoutState.Starting) return
        _checkout.value = PlusCheckoutState.Starting(plan)
        starting = viewModelScope.launch {
            try {
                val link = plusRepository.startCheckout(plan)
                _checkout.value = PlusCheckoutState.ShowingQr(plan, link.url, formatPlusPrice(link.amountTotal, link.currency), if (plan == "yearly") link.trialDays else 0)
                startPolling(plan)
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
        starting?.cancel()
        polling?.cancel()
        _checkout.value = PlusCheckoutState.Idle
    }

    /** Counts down once a second, asks every few seconds whether Plus is on yet, and thanks the person when it is. */
    private fun startPolling(plan: String) {
        polling?.cancel()
        _remainingSeconds.value = POLL_FOR_SECONDS
        polling = viewModelScope.launch {
            var tick = 0
            while (_remainingSeconds.value > 0) {
                delay(1_000L)
                _remainingSeconds.value -= 1
                tick++
                if (tick % POLL_EVERY_TICKS != 0) continue
                plusRepository.pullFromServer()
                if (plusRepository.status.value.owned) {
                    _checkout.value = PlusCheckoutState.Done(plan)
                    delay(DONE_SHOWN_MS)
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
