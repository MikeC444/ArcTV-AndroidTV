package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.Stream

/**
 * 0 = starts at once (cached, or not a debrid link at all); 1 = the debrid service still has to fetch the file first,
 * which can take minutes.
 */
fun cacheRank(stream: Stream): Int = if (stream.debrid != null && !stream.debrid.cached) 1 else 0

/**
 * The "Quality" order: sources that start at once come before ones a debrid service still has to fetch, then the
 * sharpest resolution, then the most seeders. A 720p that is ready beats a 4K that makes you wait minutes.
 */
private val qualityOrder: Comparator<Stream> =
    compareBy<Stream> { cacheRank(it) }
        .thenBy { it.resolutionTier.ordinal }
        .thenByDescending { it.seeders ?: -1 }

/**
 * Arc TV Plus "Smart source picking": a source worth starting without asking. It must start at once (cached, or no debrid wait) and have a
 * link to play: a direct or debrid link, or a torrent (info hash or magnet) that is not known to have no seeders.
 */
fun isSurePick(stream: Stream): Boolean {
    if (cacheRank(stream) != 0) return false
    if (stream.url != null) return true
    return stream.infoHash != null && (stream.seeders ?: 1) > 0
}

/** The source Select a Source marks "Recommended": the first of [streams] in the Quality order. */
fun recommendedStreamId(streams: List<Stream>): String? = streams.sortedWith(qualityOrder).firstOrNull()?.id

/** The order the Select a Source list uses for [sort]; ties keep the order the sources arrived in. */
fun sortSources(streams: List<Stream>, sort: SourceSort): List<Stream> = when (sort) {
    SourceSort.QUALITY -> streams.sortedWith(qualityOrder)
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
