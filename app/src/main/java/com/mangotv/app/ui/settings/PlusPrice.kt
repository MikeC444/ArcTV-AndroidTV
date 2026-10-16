package com.mangotv.app.ui.settings

import com.mangotv.app.data.plus.PlanPrice
import com.mangotv.app.data.plus.PlanPrices
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A charge as people read it ("£59.99"), from what Stripe reports: the amount in the currency's smallest unit and its
 * ISO code. Null when either is missing or the code isn't a currency, so the page can say the price is on the phone.
 */
fun formatPlusPrice(amountTotal: Long?, currencyCode: String?, locale: Locale = Locale.getDefault(), dropWholeCents: Boolean = false): String? {
    if (amountTotal == null || currencyCode.isNullOrBlank()) return null
    val currency = runCatching { Currency.getInstance(currencyCode.trim().uppercase()) }.getOrNull() ?: return null
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    var divisor = 1.0
    repeat(digits) { divisor *= 10 }
    val format = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
    // "£40" rather than "£40.00" when asked and the amount is whole, as the web app shows it.
    if (dropWholeCents && digits > 0 && amountTotal % divisor.toLong() == 0L) format.minimumFractionDigits = 0
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

/** Whether a plan starts with free days: only the yearly subscription (never monthly or Lifetime), and only when the backend offers them. */
fun hasTrial(planId: String, trialDays: Int): Boolean = trialDays > 0 && planId == "yearly"

/** The plan card's button: "Start 5-day free trial" when free days are on offer, otherwise the usual wording. */
fun planButtonLabel(planId: String, planLabel: String, trialDays: Int): String = when {
    hasTrial(planId, trialDays) -> "Start $trialDays-day free trial"
    planId == "lifetime" -> "Get Lifetime"
    else -> "Choose $planLabel"
}

/** The note under a plan being paid for, when it starts with free days (the card is taken now, nothing is charged until they end). */
fun trialBillingNote(planId: String, trialDays: Int): String =
    "Your card is taken now but nothing is charged for $trialDays days. Then it renews ${if (planId == "yearly") "yearly" else "monthly"}; the price is shown on your phone. Cancel before then and you pay nothing."

/** What a plan card says about its price, all worked out from the list prices. */
data class PlanPriceText(
    val price: String,
    /** Yearly only: the monthly price, shown struck through, when yearly really is cheaper per month. */
    val was: String? = null,
    /** Yearly only: "Save 33%". */
    val saving: String? = null,
    /** Yearly only: "$2.50/mo billed yearly, about 7c a day" (without the per-month part when it can't be formatted). */
    val perDay: String? = null,
    /** Lifetime only: "One payment instead of $29.99 every year". */
    val versus: String? = null
)

/** What the yearly plan comes to per month, in the smallest unit. */
fun yearlyPerMonth(yearly: PlanPrice): Long = Math.round(yearly.amount / 12.0)

/** What the yearly plan costs per day, in the smallest unit. */
fun yearlyPerDay(yearly: PlanPrice): Long = Math.round(yearly.amount / 365.0)

/** How much cheaper per month yearly is than paying monthly, as a whole percent. Null unless it really is cheaper (same currency, at least 1%). */
fun savingPercent(prices: PlanPrices): Int? {
    if (prices.monthly.currency != prices.yearly.currency || prices.monthly.amount <= 0) return null
    val percent = Math.round((1 - prices.yearly.amount / 12.0 / prices.monthly.amount) * 100).toInt()
    return if (percent >= 1) percent else null
}

/** The plan card's price lines for [planId], or null when there are no list prices (the card then says "Price at checkout"). */
fun planPriceText(planId: String, prices: PlanPrices?, locale: Locale = Locale.getDefault()): PlanPriceText? {
    if (prices == null) return null
    fun money(p: PlanPrice) = formatPlusPrice(p.amount, p.currency, locale, dropWholeCents = true)
    return when (planId) {
        "monthly" -> money(prices.monthly)?.let { PlanPriceText(it) }
        "yearly" -> {
            val price = money(prices.yearly) ?: return null
            val saving = savingPercent(prices)
            val perMonth = formatPlusPrice(yearlyPerMonth(prices.yearly), prices.yearly.currency, locale)
            val perDay = formatPlusPrice(yearlyPerDay(prices.yearly), prices.yearly.currency, locale)
            PlanPriceText(
                price = price,
                was = if (saving != null && perMonth != null) money(prices.monthly) else null,
                saving = saving?.let { "Save $it%" },
                perDay = perDay?.let { (if (perMonth != null) "$perMonth/mo billed yearly, " else "") + "about $it a day" }
            )
        }
        "lifetime" -> {
            val price = money(prices.lifetime) ?: return null
            val yearly = if (prices.lifetime.currency == prices.yearly.currency) money(prices.yearly) else null
            PlanPriceText(price, versus = yearly?.let { "One payment instead of $it every year" })
        }
        else -> null
    }
}
