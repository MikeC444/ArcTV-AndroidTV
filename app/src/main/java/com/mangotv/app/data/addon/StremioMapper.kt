package com.mangotv.app.data.addon

import com.mangotv.app.data.model.CastMember
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.DebridState
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Season
import com.mangotv.app.data.model.SourceHealth
import com.mangotv.app.data.model.Stream
import com.mangotv.app.data.torrent.trackersFromStremioSources

fun StremioMetaPreview.toContent(providerId: String): Content {
    val year = releaseInfo
        ?.takeWhile { it.isDigit() }
        ?.takeIf { it.length == 4 }
        ?.toIntOrNull()

    val runtimeMinutes = runtime
        ?.filter { it.isDigit() }
        ?.toIntOrNull()

    return Content(
        id = id,
        type = if (type == "series") ContentType.TV_SHOW else ContentType.MOVIE,
        title = name,
        description = description.orEmpty(),
        posterUrl = poster,
        backdropUrl = background ?: poster,
        logoUrl = logo,
        year = year,
        runtimeMinutes = runtimeMinutes,
        rating = imdbRating?.toDoubleOrNull(),
        genres = genres.orEmpty().map { Genre(id = it.lowercase(), name = it) },
        providerId = providerId
    )
}

fun StremioMeta.toContent(providerId: String): Content {
    val year = releaseInfo
        ?.takeWhile { it.isDigit() }
        ?.takeIf { it.length == 4 }
        ?.toIntOrNull()

    val runtimeMinutes = runtime
        ?.filter { it.isDigit() }
        ?.toIntOrNull()

    return Content(
        id = id,
        type = if (type == "series") ContentType.TV_SHOW else ContentType.MOVIE,
        title = name,
        description = description.orEmpty(),
        posterUrl = poster,
        backdropUrl = background ?: poster,
        logoUrl = logo,
        year = year,
        runtimeMinutes = runtimeMinutes,
        rating = imdbRating?.toDoubleOrNull(),
        genres = genres.orEmpty().map { Genre(id = it.lowercase(), name = it) },
        cast = cast.orEmpty().map { CastMember(name = it) },
        director = director?.takeIf { it.isNotEmpty() }?.joinToString(", "),
        providerId = providerId,
        seasons = videos.orEmpty().toSeasons()
    )
}

/**
 * Groups a series meta's flat video list into per-season, episode-ordered
 * [Season]s. Season 0 ("Specials") is dropped entirely — this app only
 * shows proper numbered seasons.
 */
private fun List<StremioVideo>.toSeasons(): List<Season> =
    filter { it.season != null && it.season != 0 && it.episode != null }
        .groupBy { it.season!! }
        .toSortedMap()
        .map { (seasonNumber, videos) ->
            Season(
                seasonNumber = seasonNumber,
                name = "Season $seasonNumber",
                episodes = videos.sortedBy { it.episode }.map { it.toEpisode() }
            )
        }

private fun StremioVideo.toEpisode(): Episode = Episode(
    id = id,
    seasonNumber = season ?: 0,
    episodeNumber = episode ?: 0,
    title = title ?: name ?: "Episode ${episode ?: 0}",
    description = overview ?: description.orEmpty(),
    thumbnailUrl = thumbnail,
    runtimeMinutes = null
)

// Real Stremio addons (Torrentio-style) embed quality/size/seeders as free
// text in the stream's title/name rather than structured fields, e.g.
// "Movie.2024.2160p.WEB-DL.DDP5.1.Atmos.x265-GROUP\n👤 1200 💾 23.6 GB". Every
// pattern below is independently optional — a miss just leaves that field
// null on the resulting Stream rather than failing the whole row.
private val RESOLUTION_4K = Regex("2160p|4K|UHD", RegexOption.IGNORE_CASE)
private val RESOLUTION_1080P = Regex("1080p", RegexOption.IGNORE_CASE)
private val RESOLUTION_720P = Regex("720p", RegexOption.IGNORE_CASE)
private val RESOLUTION_GENERIC = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
private val SOURCE_TAG = Regex("BluRay|BDRip|BRRip|WEB-?DL|WEBRip|HDTV|DVDRip|REMUX|CAM|TS", RegexOption.IGNORE_CASE)
private val CODEC_HEVC = Regex("x265|HEVC|H\\.?265", RegexOption.IGNORE_CASE)
private val CODEC_H264 = Regex("x264|H\\.?264|AVC", RegexOption.IGNORE_CASE)
private val CODEC_AV1 = Regex("AV1", RegexOption.IGNORE_CASE)
// Ordered so a more specific tag (e.g. "DDP7.1") wins over a bare channel
// count ("7.1") that's a substring of it -- Regex.find tries alternatives at
// the earliest position in the string first, and DDP7.1's "D" comes before
// where a standalone "7.1" alternative would otherwise start matching, so
// this ordering only matters for readability, not correctness. Bare channel
// counts (5.1/7.1/2.0) are word-bounded since they're common enough
// elsewhere (version numbers, aspect ratios) that an unbounded match could
// false-positive.
private val AUDIO_TAG = Regex(
    "Dual[- ]?Audio|Multi[- ]?Audio|" +
        "DDP?7\\.1(\\.Atmos)?|DD7\\.1|" +
        "DDP?5\\.1(\\.Atmos)?|DD5\\.1|" +
        "DTS-?HD(\\.MA)?|DTS:?X|DTS|TrueHD(\\.Atmos)?|Atmos|EAC3|AC3|" +
        "AAC(2\\.0|5\\.1|7\\.1)?|" +
        "\\b7\\.1\\b|\\b5\\.1\\b|\\b2\\.0\\b",
    RegexOption.IGNORE_CASE
)
// "[RD+] Torrentio" = cached on Real-Debrid; "[RD download] Torrentio" = not cached (the service has to fetch it first).
private val DEBRID_TAG = Regex("^\\s*\\[\\s*([A-Za-z]{2,3})\\s*(\\+|download)\\s*\\]", RegexOption.IGNORE_CASE)

fun parseDebridTag(name: String?): DebridState? {
    val match = DEBRID_TAG.find(name.orEmpty()) ?: return null
    return DebridState(service = match.groupValues[1].uppercase(), cached = match.groupValues[2] == "+")
}

private val SIZE_PATTERN = Regex("(\\d+(?:\\.\\d+)?)\\s?(GB|MB)", RegexOption.IGNORE_CASE)
private val SEEDERS_EMOJI = Regex("👤\\s?(\\d+)")
private val SEEDERS_WORD = Regex("(\\d+)\\s*(?:seeds?|peers?)\\b", RegexOption.IGNORE_CASE)

private fun formatSeederCount(count: Int): String =
    if (count >= 1000) "%.1fK".format(count / 1000.0) else count.toString()

// A channel layout written into a release name ("DDP5.1", "DTS-HD.MA.7.1", "AAC2.0", "6CH"). Not part of a longer number ("1.5.1", "H.264.5.1.1").
private val CHANNEL_LAYOUT = Regex("(?<![0-9])(?<![0-9]\\.)(7\\.1|5\\.1|2\\.0)(?![0-9])(?!\\.[0-9]\\b)")
private val CHANNEL_COUNT = Regex("(?<![0-9])(8|6|2)\\s?ch\\b", RegexOption.IGNORE_CASE)
private val STEREO_WORD = Regex("\\b(stereo|mono)\\b", RegexOption.IGNORE_CASE)
private val ATMOS_WORD = Regex("atmos", RegexOption.IGNORE_CASE)

/** True when a release's text says Dolby Atmos. */
fun detectAtmos(text: String): Boolean = ATMOS_WORD.containsMatchIn(text)

/**
 * The most channels a release's text says its audio has: 8 (7.1), 6 (5.1) or 2 (stereo), or null when it doesn't say. The highest wins when
 * several are listed (a multi-audio release); a bare "Atmos" with no layout counts as 8.
 */
fun detectAudioChannels(text: String): Int? {
    val found = mutableListOf<Int>()
    CHANNEL_LAYOUT.findAll(text).forEach { found += when (it.groupValues[1]) { "7.1" -> 8; "5.1" -> 6; else -> 2 } }
    CHANNEL_COUNT.findAll(text).forEach { found += it.groupValues[1].toInt() }
    if (STEREO_WORD.containsMatchIn(text)) found += 2
    found.maxOrNull()?.let { return it }
    return if (detectAtmos(text)) 8 else null
}

fun StremioStream.toStream(providerId: String, providerLabel: String): Stream {
    val haystack = listOfNotNull(title, name, description).joinToString("\n")

    val (resolutionTier, qualityBadge) = when {
        RESOLUTION_4K.containsMatchIn(haystack) -> ResolutionTier.UHD_4K to "4K"
        RESOLUTION_1080P.containsMatchIn(haystack) -> ResolutionTier.FHD_1080P to "1080p"
        RESOLUTION_720P.containsMatchIn(haystack) -> ResolutionTier.HD_720P to "720p"
        else -> {
            val generic = RESOLUTION_GENERIC.find(haystack)?.groupValues?.get(1)
            ResolutionTier.OTHER to (generic?.let { "${it}p" } ?: "SD")
        }
    }

    val sourceTag = SOURCE_TAG.find(haystack)?.value
    val codec = when {
        CODEC_HEVC.containsMatchIn(haystack) -> "HEVC"
        CODEC_H264.containsMatchIn(haystack) -> "H.264"
        CODEC_AV1.containsMatchIn(haystack) -> "AV1"
        else -> null
    }
    val audioTag = AUDIO_TAG.find(haystack)?.value
    val audioChannels = detectAudioChannels(haystack)
    val audioAtmos = detectAtmos(haystack)

    val sizeMatch = SIZE_PATTERN.find(haystack)
    val sizeLabel = sizeMatch?.value
    val sizeBytes = sizeMatch?.let {
        val amount = it.groupValues[1].toDoubleOrNull() ?: return@let null
        val unit = it.groupValues[2]
        val multiplier = if (unit.equals("GB", ignoreCase = true)) 1_000_000_000L else 1_000_000L
        (amount * multiplier).toLong()
    }

    val seeders = (SEEDERS_EMOJI.find(haystack) ?: SEEDERS_WORD.find(haystack))
        ?.groupValues?.get(1)?.toIntOrNull()
    val seedersLabel = seeders?.let { formatSeederCount(it) }
    val sourceHealth = seeders?.let {
        when {
            it >= 500 -> SourceHealth.VERY_HIGH
            it >= 100 -> SourceHealth.HIGH
            it >= 20 -> SourceHealth.GOOD
            else -> SourceHealth.LOW
        }
    }

    val releaseTitle = title?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Unknown Source"

    // The same torrent can be offered once per file (a season pack, one row per episode), so the file index is part of the id.
    val idSeed = infoHash?.let { hash -> fileIdx?.let { "$hash#$it" } ?: hash } ?: url ?: (title.orEmpty() + name.orEmpty())

    // Some addons prefix `name` with a bracketed tag (e.g. "[TB+] Torrentio")
    // that reads as noise in a compact provider label — strip it for display.
    val cleanedName = name?.replace(Regex("^\\[.*?\\]\\s*"), "")?.takeIf { it.isNotBlank() }

    return Stream(
        id = "$providerId:${idSeed.hashCode()}",
        providerId = providerId,
        providerLabel = cleanedName ?: providerLabel,
        resolutionTier = resolutionTier,
        qualityBadge = qualityBadge,
        releaseTitle = releaseTitle,
        sourceTag = sourceTag,
        codec = codec,
        audioTag = audioTag,
        audioChannels = audioChannels,
        audioAtmos = audioAtmos,
        sizeLabel = sizeLabel,
        sizeBytes = sizeBytes,
        seeders = seeders,
        seedersLabel = seedersLabel,
        sourceHealth = sourceHealth,
        url = url,
        infoHash = infoHash,
        fileIdx = fileIdx?.takeIf { it >= 0 },
        torrentFilename = behaviorHints?.filename?.takeIf { it.isNotBlank() },
        trackers = trackersFromStremioSources(sources),
        ytId = ytId,
        debrid = parseDebridTag(name)
    )
}
