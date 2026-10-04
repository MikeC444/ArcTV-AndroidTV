package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/plus (see server/src/routes/plus.ts): what this account has, and starting a Stripe checkout.

@Serializable
data class PlusStatusDto(
    val active: Boolean,
    /** "monthly", "yearly", "lifetime", "early_access" while the paywall is off, or null when there is no Plus. */
    val plan: String? = null,
    val validUntil: String? = null,
    /** True once Plus is paid; false while it is free for everyone (early access). */
    val paywall: Boolean = false,
    /** A monthly or yearly subscription that has been cancelled: Plus runs to [validUntil] and then ends. Absent from an older backend. */
    val cancelAtPeriodEnd: Boolean = false
)

@Serializable
data class PlusCheckoutRequest(val plan: String)

@Serializable
data class PlusCheckoutResponse(
    val url: String,
    /** What the checkout will charge, in the currency's smallest unit (pence, cents). Absent from an older backend. */
    val amountTotal: Long? = null,
    /** Lower-case ISO currency code ("gbp"). Absent from an older backend. */
    val currency: String? = null
)

/** A started checkout: the payment page to open (shown as a QR code) and, when the backend says, what it will charge. */
data class PlusCheckoutLink(val url: String, val amountTotal: Long?, val currency: String?)
