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
    val paywall: Boolean = false
)

@Serializable
data class PlusCheckoutRequest(val plan: String)

@Serializable
data class PlusCheckoutResponse(val url: String)
