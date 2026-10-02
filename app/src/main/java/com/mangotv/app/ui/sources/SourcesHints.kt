package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.StreamLookup

/** One line saying what an addon answered; null (still being asked) reads "Checking...". */
fun lookupText(lookup: StreamLookup?): String = when (lookup) {
    null -> "Checking…"
    is StreamLookup.Ok -> "${lookup.count} source${if (lookup.count == 1) "" else "s"}"
    StreamLookup.None -> "No streams for this title"
    StreamLookup.Unsupported -> "Doesn't provide streams"
    is StreamLookup.Failed -> lookup.reason
}

/** True when at least one addon couldn't be asked or didn't answer, so the list may be incomplete. */
fun anyAddonFailed(addons: List<AddonLookupRow>): Boolean = addons.any { it.lookup is StreamLookup.Failed }

/** Why the list is empty, said plainly, so "no sources" never reads the same whatever the cause. */
fun noSourcesHint(addons: List<AddonLookupRow>): String = when {
    addons.isEmpty() ->
        "You don't have any addons installed. Add a stream addon under Settings › Addons to find sources."
    anyAddonFailed(addons) ->
        "Some of your addons didn't answer, so this list may be incomplete. Try again in a moment."
    addons.all { it.lookup == StreamLookup.Unsupported } ->
        "None of your installed addons provide streams. Add a stream addon under Settings › Addons."
    else ->
        "Your addons were asked, but none of them has a stream for this title yet. Newer or lesser-known titles often have none."
}

/** "Torrentio took too long to answer", one per addon that failed. */
fun failedAddonLines(addons: List<AddonLookupRow>): List<String> =
    addons.mapNotNull { row -> (row.lookup as? StreamLookup.Failed)?.let { "${row.name} ${it.reason}" } }
