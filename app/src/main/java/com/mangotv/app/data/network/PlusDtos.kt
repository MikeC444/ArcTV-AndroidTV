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
    val cancelAtPeriodEnd: Boolean = false,
    /** Free days a first monthly or yearly subscription starts with, when this account is offered them; 0 otherwise. Absent from an older backend. */
    val trialDays: Int = 0,
    /** The list price of each plan for the plan screen; null when the paywall is off or Stripe's prices couldn't be read. Absent from an older backend. */
    val prices: PlusPricesDto? = null,
    /** For a Lifetime member: whether they chose to be on the supporters board (null for everyone else, and from an older backend). */
    val supporterBoard: Boolean? = null,
    /** With the board on: listed as "Anonymous" rather than by name. */
    val supporterAnonymous: Boolean? = null
)

@Serializable
data class PlusSupporterRequest(val show: Boolean, val anonymous: Boolean = false)

/** One plan's list price: the amount in the currency's smallest unit (pence, cents) and its lower-case ISO code. */
@Serializable
data class PlusPriceDto(val amount: Double? = null, val currency: String? = null)

@Serializable
data class PlusPricesDto(val monthly: PlusPriceDto? = null, val yearly: PlusPriceDto? = null, val lifetime: PlusPriceDto? = null)

@Serializable
data class PlusCheckoutRequest(val plan: String)

@Serializable
data class PlusCheckoutResponse(
    val url: String,
    /** What the checkout will charge, in the currency's smallest unit (pence, cents). Absent from an older backend. */
    val amountTotal: Long? = null,
    /** Lower-case ISO currency code ("gbp"). Absent from an older backend. */
    val currency: String? = null,
    /** Free days this checkout starts with (0: none). Absent from an older backend. */
    val trialDays: Int = 0
)

/** A started checkout: the payment page to open (shown as a QR code) and, when the backend says, what it will charge. */
data class PlusCheckoutLink(val url: String, val amountTotal: Long?, val currency: String?, val trialDays: Int = 0)
