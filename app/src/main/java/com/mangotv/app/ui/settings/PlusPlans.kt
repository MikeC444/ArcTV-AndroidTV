package com.mangotv.app.ui.settings

/**
 * Arc TV Plus -- the optional paid tier. The whole app stays free; Plus adds extras. What the Settings > Arc TV Plus tab
 * shows comes from here (same shape as the web app). The real prices live in Stripe and are shown on its checkout page;
 * [PlusPlan.price] is only a label for the plan card (e.g. "$3.99"; null = "Price at checkout").
 */
data class PlusPlan(
    val id: String,
    val label: String,
    val price: String?,
    val per: String,
    /** What the person is buying, shown under the price. */
    val blurb: String,
    val note: String? = null
)

val PLUS_PLANS: List<PlusPlan> = listOf(
    PlusPlan("monthly", "Monthly", price = null, per = "per month", blurb = "Cancel any time."),
    PlusPlan("yearly", "Yearly", price = null, per = "per year", blurb = "Cancel any time.", note = "Best value"),
    PlusPlan("lifetime", "Lifetime", price = null, per = "one-time payment", blurb = "Pay once, keep Plus forever. No renewals.", note = "Pay once")
)

/** [comingSoon] perks are announced but not on yet; the others are on for accounts with Plus (everyone, while it is in early access). */
data class PlusPerk(val title: String, val detail: String, val comingSoon: Boolean = true)

val PLUS_PERKS: List<PlusPerk> = listOf(
    PlusPerk("Picked for you", "A Home row chosen from the movies you like, finish and save, with the reason under each poster, plus Like and Not for me on movies.", comingSoon = false),
    PlusPerk("Profiles", "Up to 5 profiles on one account, each with its own My List, Continue Watching, settings and recommendations. Add kids profiles, and lock any profile with a PIN.", comingSoon = false),
    PlusPerk("Parental controls", "Locks on individual genres and titles, built on kids profiles and Blocked Genres."),
    PlusPerk("Smart source picking", "Skips Select a Source and starts the best source for your device, once every addon has answered. Turn it on below.", comingSoon = false),
    PlusPerk("Your stats", "How much you watch, how this week compares, your streak, a map of your last 13 weeks and your busiest day, under Settings > Your stats.", comingSoon = false)
)

const val PLUS_FREE_NOTE = "Everything you use today stays free: browsing, playing, My List, Continue Watching and addons."

const val PLUS_PROCEEDS_NOTE = "Every subscription goes straight back into building and running Arc TV: new features, faster servers and keeping the free app free."
