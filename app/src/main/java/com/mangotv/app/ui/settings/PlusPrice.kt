package com.mangotv.app.ui.settings

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A charge as people read it ("£59.99"), from what Stripe reports: the amount in the currency's smallest unit and its
 * ISO code. Null when either is missing or the code isn't a currency, so the page can say the price is on the phone.
 */
fun formatPlusPrice(amountTotal: Long?, currencyCode: String?, locale: Locale = Locale.getDefault()): String? {
    if (amountTotal == null || currencyCode.isNullOrBlank()) return null
    val currency = runCatching { Currency.getInstance(currencyCode.trim().uppercase()) }.getOrNull() ?: return null
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    var divisor = 1.0
    repeat(digits) { divisor *= 10 }
    val format = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
    return format.format(amountTotal / divisor)
}

/** "mm:ss" for the countdown beside the QR code. */
fun formatCountdown(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

/** How a plan is billed, for the "Billing" row and the price suffix. */
fun billingLabel(planId: String): String = when (planId) {
    "monthly" -> "Monthly"
    "yearly" -> "Yearly"
    else -> "One-time payment"
}

fun billingSuffix(planId: String): String? = when (planId) {
    "monthly" -> "/ month"
    "yearly" -> "/ year"
    else -> null
}

fun billingNote(planId: String): String = when (planId) {
    "monthly" -> "Renews monthly. Cancel any time."
    "yearly" -> "Renews yearly. Cancel any time."
    else -> "Pay once, keep Plus forever. No renewals."
}
