package com.mangotv.app.ui.settings

import com.mangotv.app.BuildConfig

/**
 * Arc TV Plus -- the optional paid tier. The whole app stays free; Plus adds extras. Everything the Settings > Arc TV
 * Plus tab shows comes from here, so launching is a matter of filling this in, per plan (same shape as the web app):
 *  - [PlusPlan.checkoutUrl]: a hosted checkout page (for example a Stripe Payment Link). Empty = "opens soon".
 *  - [PlusPlan.price]: shown as-is (e.g. "$3.99"); null = "Price announced soon".
 */
data class PlusPlan(
    val id: String,
    val label: String,
    val price: String?,
    val per: String,
    /** What the person is buying, shown under the price. */
    val blurb: String,
    val note: String? = null,
    val checkoutUrl: String = ""
)

val PLUS_PLANS: List<PlusPlan> = listOf(
    PlusPlan("monthly", "Monthly", price = null, per = "per month", blurb = "Cancel any time."),
    PlusPlan("yearly", "Yearly", price = null, per = "per year", blurb = "Cancel any time.", note = "Best value"),
    PlusPlan("lifetime", "Lifetime", price = null, per = "one-time payment", blurb = "Pay once, keep Plus forever. No renewals.", note = "Pay once")
)

data class PlusPerk(val title: String, val detail: String, val comingSoon: Boolean = true)

val PLUS_PERKS: List<PlusPerk> = listOf(
    PlusPerk("Profiles", "Separate profiles on one account, each with its own My List and Continue Watching."),
    PlusPerk("Parental controls", "A PIN, plus locks on genres and titles."),
    PlusPerk("Smart source picking", "Automatically choose the best playable source for your device."),
    PlusPerk("Picked for you", "A Home row chosen from what you like, have finished and have saved.")
)

const val PLUS_FREE_NOTE = "Everything you use today stays free: browsing, playing, My List, Continue Watching and addons."

const val PLUS_PROCEEDS_NOTE = "Every subscription goes straight back into building and running Arc TV: new features, faster servers and keeping the free app free."

/** True once any plan has a checkout page to send people to. */
fun plusIsOnSale(plans: List<PlusPlan> = PLUS_PLANS): Boolean = plans.any { it.checkoutUrl.isNotBlank() }

/**
 * Whether the Arc TV Plus tab is shown. Plus is not public yet (the web app hides it too), so it only appears in debug
 * builds, such as the CI-built APK used for testing. Switching it on for everyone at launch is changing this one line.
 */
val PLUS_TAB_VISIBLE: Boolean = BuildConfig.DEBUG
