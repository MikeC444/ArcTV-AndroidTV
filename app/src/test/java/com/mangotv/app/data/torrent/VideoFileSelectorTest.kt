package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFileSelectorTest {
    private val gb = 1024L * 1024 * 1024
    private fun f(i: Int, path: String, size: Long) = TorrentFileEntry(i, path, size)
    private fun pick(files: List<TorrentFileEntry>, hint: FileHint = FileHint()): Int? =
        (selectVideoFile(files, hint) as? FileSelection.Found)?.file?.index

    @Test fun singleFileMovie() {
        assertEquals(0, pick(listOf(f(0, "Movie.2024.1080p.mkv", 4 * gb))))
    }

    @Test fun picksBiggestVideoSkippingSamplesAndExtras() {
        val files = listOf(
            f(0, "Movie/Sample/sample.mkv", 50_000_000),
            f(1, "Movie/Movie.mkv", 8 * gb),
            f(2, "Movie/Extras/Making of.mkv", 900_000_000),
            f(3, "Movie/Movie.nfo", 2_000),
            f(4, "Movie/Movie.srt", 90_000),
            f(5, "Movie/Movie.Trailer.mp4", 90_000_000)
        )
        assertEquals(1, pick(files))
    }

    @Test fun addonFileIdxWinsWhenItIsAVideo() {
        val files = listOf(f(0, "a.mkv", 5 * gb), f(1, "b.mkv", 1 * gb))
        assertEquals(1, pick(files, FileHint(fileIdx = 1)))
        assertEquals(0, pick(files, FileHint(fileIdx = 7))) // out of range: falls back to the biggest
    }

    @Test fun filenameHintMatchesBaseNameCaseInsensitively() {
        val files = listOf(f(0, "Pack/A.mkv", 5 * gb), f(1, "Pack/Sub/B.MKV", 1 * gb))
        assertEquals(1, pick(files, FileHint(filename = "b.mkv")))
    }

    @Test fun seasonPackPicksTheRequestedEpisode() {
        val files = (1..10).map { f(it - 1, "Show.S02.1080p/Show.S02E%02d.1080p.mkv".format(it), (1 + it) * 100_000_000L) } +
            f(10, "Show.S02.1080p/Show.S02E03.sample.mkv", 30_000_000)
        assertEquals(2, pick(files, FileHint(season = 2, episode = 3)))
        assertEquals(9, pick(files, FileHint(season = 2, episode = 10)))
    }

    @Test fun episodeFormatsAreRecognised() {
        assertEquals(2 to 5, episodeOf("x/Show 2x05 720p.mkv"))
        assertEquals(1 to 12, episodeOf("Show.s01e12.mkv"))
        assertEquals(3 to 4, episodeOf("Season 3/Episode 04.mkv"))
        assertEquals(null, episodeOf("Movie.1920x1080.mkv"))
        assertEquals(null, episodeOf("Movie.2024.1080p.x264.mkv"))
    }

    @Test fun wrongSeasonIsNotAMatch() {
        val files = listOf(f(0, "Show.S01E01.mkv", gb), f(1, "Show.S01E02.mkv", gb))
        assertEquals(FileSelection.EpisodeNotFound, selectVideoFile(files, FileHint(season = 2, episode = 1)))
        assertEquals(1, pick(files, FileHint(season = 1, episode = 2)))
    }

    @Test fun singleVideoIsPlayedEvenWhenEpisodeIsRequested() {
        assertEquals(0, pick(listOf(f(0, "Some Episode Release.mkv", gb)), FileHint(season = 4, episode = 9)))
    }

    @Test fun noVideoFiles() {
        val files = listOf(f(0, "movie.iso", 8 * gb), f(1, "movie.rar", gb), f(2, "readme.txt", 10))
        assertEquals(FileSelection.NoVideo, selectVideoFile(files))
        assertEquals(FileSelection.NoVideo, selectVideoFile(emptyList()))
    }

    @Test fun onlyASampleIsStillPlayable() {
        assertTrue(pick(listOf(f(0, "sample.mkv", 20_000_000))) == 0)
    }
}
