package com.mangotv.app.data.torrent

import java.util.Locale

/** One file inside a torrent: its index in the torrent's own file list, its path (folders joined by '/') and its size in bytes. */
data class TorrentFileEntry(val index: Int, val path: String, val size: Long) {
    val name: String get() = path.substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

/**
 * What the caller knows about which file it wants. [fileIdx] is the index an addon names (Stremio's `fileIdx`), [filename] a file name hint
 * (Stremio's `behaviorHints.filename`), [season] / [episode] the episode being played, for a torrent that holds a whole season.
 */
data class FileHint(
    val fileIdx: Int? = null,
    val filename: String? = null,
    val season: Int? = null,
    val episode: Int? = null
)

sealed interface FileSelection {
    data class Found(val file: TorrentFileEntry) : FileSelection
    /** No video file at all (archives, disc images and so on cannot be streamed). */
    data object NoVideo : FileSelection
    /** A multi-video torrent in which the wanted episode is not present. */
    data object EpisodeNotFound : FileSelection
}

private val VIDEO_EXTENSIONS = setOf(
    "mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "mts", "webm", "mpg", "mpeg", "flv", "ogv", "3gp", "divx", "vob"
)
private val SAMPLE_NAME = Regex("(^|[^a-z0-9])(sample|trailer|featurette|extras?|bonus|behind[ ._-]the[ ._-]scenes)([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
private const val SAMPLE_MAX_BYTES = 250L * 1024 * 1024
private val SEASON_EPISODE = Regex("(?<![a-z0-9])s(\\d{1,2})[ ._-]?e(\\d{1,3})(?![0-9])", RegexOption.IGNORE_CASE)
private val X_FORMAT = Regex("(?<![a-z0-9])(\\d{1,2})x(\\d{2,3})(?![0-9a-z])", RegexOption.IGNORE_CASE)
private val EPISODE_ONLY = Regex("(?<![a-z0-9])e(?:p(?:isode)?)?[ ._-]?(\\d{1,3})(?![0-9])", RegexOption.IGNORE_CASE)
private val SEASON_FOLDER = Regex("(?<![a-z0-9])season[ ._-]?(\\d{1,2})(?![0-9])", RegexOption.IGNORE_CASE)

fun isVideoFile(file: TorrentFileEntry): Boolean = file.extension in VIDEO_EXTENSIONS

private fun looksLikeExtra(file: TorrentFileEntry): Boolean =
    file.size < SAMPLE_MAX_BYTES && SAMPLE_NAME.containsMatchIn(file.path)

/** The (season, episode) a file's path says it is, or null. A path with only an episode number reports season null. */
internal fun episodeOf(path: String): Pair<Int?, Int>? {
    val name = path.substringAfterLast('/')
    SEASON_EPISODE.find(name)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
    X_FORMAT.find(name)?.let { return it.groupValues[1].toInt() to it.groupValues[2].toInt() }
    val folderSeason = SEASON_FOLDER.find(path.substringBeforeLast('/', ""))?.groupValues?.get(1)?.toInt()
    EPISODE_ONLY.find(name)?.let { return folderSeason to it.groupValues[1].toInt() }
    return null
}

/**
 * Picks the file to play out of a torrent's file list: the file an addon named, else the one with the hinted file name, else (for an
 * episode) the file whose name says that season and episode, else the biggest video that is not a sample or extra.
 */
fun selectVideoFile(files: List<TorrentFileEntry>, hint: FileHint = FileHint()): FileSelection {
    val videos = files.filter { it.size > 0 && isVideoFile(it) }
    if (videos.isEmpty()) return FileSelection.NoVideo

    hint.fileIdx?.let { idx -> videos.firstOrNull { it.index == idx }?.let { return FileSelection.Found(it) } }
    hint.filename?.takeIf { it.isNotBlank() }?.let { wanted ->
        val base = wanted.substringAfterLast('/').lowercase(Locale.ROOT)
        videos.filter { it.name.lowercase(Locale.ROOT) == base }.maxByOrNull { it.size }?.let { return FileSelection.Found(it) }
    }

    val real = videos.filterNot { looksLikeExtra(it) }.ifEmpty { videos }
    if (hint.episode != null && real.size > 1) {
        val matches = real.filter { file ->
            val found = episodeOf(file.path) ?: return@filter false
            found.second == hint.episode && (hint.season == null || found.first == null || found.first == hint.season)
        }
        return matches.maxByOrNull { it.size }?.let { FileSelection.Found(it) } ?: FileSelection.EpisodeNotFound
    }
    return FileSelection.Found(real.maxBy { it.size })
}
