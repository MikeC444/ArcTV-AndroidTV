package com.mangotv.app.data.torrent

import java.util.Locale

/** A subtitle file found next to the video inside a torrent. [language] is an ISO 639-1 code when the file name says one. */
data class TorrentSubtitleFile(val file: TorrentFileEntry, val language: String?, val label: String, val mimeType: String)

private val SUBTITLE_EXTENSIONS = mapOf(
    "srt" to "application/x-subrip",
    "ass" to "text/x-ssa",
    "ssa" to "text/x-ssa",
    "vtt" to "text/vtt"
)
private const val MAX_SUBTITLE_BYTES = 5L * 1024 * 1024
private const val MAX_SUBTITLES = 12
private val SUBTITLE_FOLDERS = setOf("subs", "sub", "subtitles", "subtitle", "srt")

private val LANGUAGES: Map<String, String> = mapOf(
    "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch",
    "sv" to "Swedish", "da" to "Danish", "no" to "Norwegian", "fi" to "Finnish", "pl" to "Polish", "cs" to "Czech", "hu" to "Hungarian",
    "ro" to "Romanian", "ru" to "Russian", "uk" to "Ukrainian", "tr" to "Turkish", "el" to "Greek", "he" to "Hebrew", "ar" to "Arabic",
    "fa" to "Persian", "hi" to "Hindi", "th" to "Thai", "vi" to "Vietnamese", "id" to "Indonesian", "ms" to "Malay", "ko" to "Korean",
    "ja" to "Japanese", "zh" to "Chinese", "bg" to "Bulgarian", "hr" to "Croatian", "sr" to "Serbian", "sk" to "Slovak", "sl" to "Slovenian"
)
// Names and three-letter codes as they appear in subtitle file names.
private val LANGUAGE_WORDS: Map<String, String> = buildMap {
    for ((code, name) in LANGUAGES) put(name.lowercase(Locale.ROOT), code)
    putAll(mapOf(
        "eng" to "en", "spa" to "es", "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de", "ita" to "it", "por" to "pt", "dut" to "nl",
        "nld" to "nl", "swe" to "sv", "dan" to "da", "nor" to "no", "fin" to "fi", "pol" to "pl", "cze" to "cs", "ces" to "cs", "hun" to "hu",
        "rum" to "ro", "ron" to "ro", "rus" to "ru", "ukr" to "uk", "tur" to "tr", "gre" to "el", "ell" to "el", "heb" to "he", "ara" to "ar",
        "per" to "fa", "fas" to "fa", "hin" to "hi", "tha" to "th", "vie" to "vi", "ind" to "id", "may" to "ms", "msa" to "ms", "kor" to "ko",
        "jpn" to "ja", "chi" to "zh", "zho" to "zh", "bul" to "bg", "hrv" to "hr", "srp" to "sr", "slo" to "sk", "slk" to "sk", "slv" to "sl",
        "brazilian" to "pt", "latino" to "es"
    ))
}

/** The language a subtitle file's name states ("2_English.srt", "Movie.en.srt", "Movie.eng.forced.srt"), or null. Two-letter codes must stand alone. */
fun subtitleLanguageOf(fileName: String): String? {
    val base = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
    val tokens = base.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    for (token in tokens.asReversed()) {
        LANGUAGE_WORDS[token]?.let { return it }
        if (token.length == 2 && token in LANGUAGES) return token
    }
    return null
}

private fun String.dir() = substringBeforeLast('/', "")
private fun String.fileBase() = substringAfterLast('/').substringBeforeLast('.')

/**
 * The subtitle files that belong to [video]: ones beside it, or in a `Subs` / `Subtitles` folder under its folder (a folder named after the
 * video inside that too, as scene releases do). In a torrent with several videos (a season pack) a subtitle must also match this video: its name
 * starts with the video's name, it sits in a folder named after the video, or it names the same episode. At most 12, smallest wasted effort: only
 * files up to 5 MB, and only formats the players read as text (srt, ass, ssa, vtt).
 */
fun selectSubtitleFiles(files: List<TorrentFileEntry>, video: TorrentFileEntry): List<TorrentSubtitleFile> {
    val videoDir = video.path.dir()
    val videoBase = video.path.fileBase().lowercase(Locale.ROOT)
    val severalVideos = files.count { it.size > 0 && isVideoFile(it) } > 1
    val videoEpisode = episodeOf(video.path)
    return files.asSequence()
        .filter { it.extension in SUBTITLE_EXTENSIONS && it.size in 1..MAX_SUBTITLE_BYTES }
        .filter { sub ->
            val subDir = sub.path.dir()
            val relative = when {
                subDir == videoDir -> ""
                videoDir.isEmpty() -> subDir
                subDir.startsWith("$videoDir/") -> subDir.removePrefix("$videoDir/")
                else -> return@filter false
            }
            val folders = if (relative.isEmpty()) emptyList() else relative.split('/')
            val allowedFolders = folders.isEmpty() || folders.first().lowercase(Locale.ROOT) in SUBTITLE_FOLDERS
            if (!allowedFolders) return@filter false
            if (!severalVideos) return@filter true
            val name = sub.path.fileBase().lowercase(Locale.ROOT)
            val inVideoNamedFolder = folders.any { it.lowercase(Locale.ROOT) == videoBase }
            val sameEpisode = videoEpisode != null && episodeOf(sub.path)?.let { it.second == videoEpisode.second && (it.first == null || videoEpisode.first == null || it.first == videoEpisode.first) } == true
            name.startsWith(videoBase) || inVideoNamedFolder || sameEpisode
        }
        .sortedWith(compareBy({ if (subtitleLanguageOf(it.name) == "en") 0 else 1 }, { it.path }))
        .take(MAX_SUBTITLES)
        .map { sub ->
            val language = subtitleLanguageOf(sub.name)
            val label = language?.let { LANGUAGES[it] } ?: sub.name.substringBeforeLast('.')
            TorrentSubtitleFile(sub, language, label, SUBTITLE_EXTENSIONS.getValue(sub.extension))
        }
        .toList()
}
