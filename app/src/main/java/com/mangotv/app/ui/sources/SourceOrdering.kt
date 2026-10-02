package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.Stream

/** The order the Select a Source list uses for [sort]; ties keep the order the sources arrived in. */
fun sortSources(streams: List<Stream>, sort: SourceSort): List<Stream> = when (sort) {
    SourceSort.QUALITY -> streams.sortedWith(
        compareBy<Stream> { it.resolutionTier.ordinal }.thenByDescending { it.seeders ?: -1 }
    )
    SourceSort.SEEDERS -> streams.sortedByDescending { it.seeders ?: -1 }
    SourceSort.SIZE -> streams.sortedByDescending { it.sizeBytes ?: -1 }
}

/**
 * The rows of Select a Source: the recommended source first -- always, whatever is filtered out and however the rest
 * is sorted -- then the filtered sources in the chosen order. [all] is every source found; [filtered] is the subset
 * the current filter keeps.
 */
fun orderSources(all: List<Stream>, filtered: List<Stream>, recommendedId: String?, sort: SourceSort): List<Stream> {
    val recommended = recommendedId?.let { id -> all.firstOrNull { it.id == id } }
    val rest = sortSources(filtered, sort).filter { it.id != recommended?.id }
    return if (recommended != null) listOf(recommended) + rest else rest
}
