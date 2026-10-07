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

/** Whether a plan starts with free days: only a monthly or yearly subscription, and only when the backend offers them. */
fun hasTrial(planId: String, trialDays: Int): Boolean = trialDays > 0 && (planId == "monthly" || planId == "yearly")

/** The plan card's button: "Start 5-day free trial" when free days are on offer, otherwise the usual wording. */
fun planButtonLabel(planId: String, planLabel: String, trialDays: Int): String = when {
    hasTrial(planId, trialDays) -> "Start $trialDays-day free trial"
    planId == "lifetime" -> "Get Lifetime"
    else -> "Choose $planLabel"
}

/** The note under a plan being paid for, when it starts with free days (the card is taken now, nothing is charged until they end). */
fun trialBillingNote(planId: String, trialDays: Int): String =
    "Your card is taken now but nothing is charged for $trialDays days. Then it renews ${if (planId == "yearly") "yearly" else "monthly"}; the price is shown on your phone. Cancel before then and you pay nothing."
