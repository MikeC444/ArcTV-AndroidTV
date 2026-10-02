package com.mangotv.app.ui.settings

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

/** [comingSoon] perks are announced but not on yet; the others are on for everyone while Plus is in early access. */
data class PlusPerk(val title: String, val detail: String, val comingSoon: Boolean = true)

val PLUS_PERKS: List<PlusPerk> = listOf(
    PlusPerk("Picked for you", "A Home row chosen from the movies you like, finish and save, with the reason under each poster, plus Like and Not for me on movies.", comingSoon = false),
    PlusPerk("Profiles", "Separate profiles on one account, each with its own My List and Continue Watching."),
    PlusPerk("Parental controls", "A PIN, plus locks on genres and titles."),
    PlusPerk("Smart source picking", "Automatically choose the best playable source for your device.")
)

const val PLUS_FREE_NOTE = "Everything you use today stays free: browsing, playing, My List, Continue Watching and addons."

const val PLUS_PROCEEDS_NOTE = "Every subscription goes straight back into building and running Arc TV: new features, faster servers and keeping the free app free."

/** True once any plan has a checkout page to send people to. */
fun plusIsOnSale(plans: List<PlusPlan> = PLUS_PLANS): Boolean = plans.any { it.checkoutUrl.isNotBlank() }

/**
 * Whether ArcTV Plus features are on: the Plus tab, "Picked for you" and Like / Not for me. Plus is in early access, so
 * they are on for everyone, labelled as Plus features and free for now (the web app does the same). There is no
 * subscription status to read yet; when subscriptions launch, make this read the account's status instead.
 */
const val PLUS_EARLY_ACCESS: Boolean = true
val PLUS_TAB_VISIBLE: Boolean = PLUS_EARLY_ACCESS
