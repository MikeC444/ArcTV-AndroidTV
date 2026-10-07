package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFilesTest {
    private fun f(i: Int, path: String, size: Long = 50_000) = TorrentFileEntry(i, path, size)
    private val gb = 1024L * 1024 * 1024
    private fun paths(subs: List<TorrentSubtitleFile>) = subs.map { it.file.path }

    @Test fun languagesFromFileNames() {
        assertEquals("en", subtitleLanguageOf("2_English.srt"))
        assertEquals("en", subtitleLanguageOf("Movie.2024.en.srt"))
        assertEquals("es", subtitleLanguageOf("Movie.spa.forced.srt"))
        assertEquals("fr", subtitleLanguageOf("French.srt"))
        assertEquals("pt", subtitleLanguageOf("Brazilian.srt"))
        assertNull(subtitleLanguageOf("Movie.2024.1080p.srt"))
        assertNull(subtitleLanguageOf("sub1.srt"))
    }

    @Test fun besideAndInSubsFolderOfASingleMovie() {
        val video = f(0, "Movie/Movie.mkv", 4 * gb)
        val files = listOf(
            video, f(1, "Movie/Movie.srt"), f(2, "Movie/Subs/2_Spanish.srt"), f(3, "Movie/Subs/3_English.srt"),
            f(4, "Movie/Subs/Movie.idx"), f(5, "Movie/Subs/Movie.sub"), f(6, "Movie/readme.txt"), f(7, "Other/Foreign.srt"),
            f(8, "Movie/Extras/Commentary.srt")
        )
        val subs = selectSubtitleFiles(files, video)
        assertEquals(listOf("Movie/Subs/3_English.srt", "Movie/Movie.srt", "Movie/Subs/2_Spanish.srt").sorted(), paths(subs).sorted())
        assertEquals("English", subs.first().label) // English first
        assertEquals("application/x-subrip", subs.first().mimeType)
    }

    @Test fun seasonPackOnlyTakesTheMatchingEpisode() {
        val e1 = f(0, "Show.S01/Show.S01E01.mkv", gb)
        val e2 = f(1, "Show.S01/Show.S01E02.mkv", gb)
        val files = listOf(
            e1, e2,
            f(2, "Show.S01/Show.S01E01.en.srt"), f(3, "Show.S01/Show.S01E02.en.srt"), f(4, "Show.S01/Show.S01E02.es.srt"),
            f(5, "Show.S01/Subs/Show.S01E01/2_English.srt"), f(6, "Show.S01/Subs/Show.S01E02/2_English.srt"), f(7, "Show.S01/Subs/Show 1x02 French.srt")
        )
        assertEquals(
            listOf("Show.S01/Show.S01E02.en.srt", "Show.S01/Show.S01E02.es.srt", "Show.S01/Subs/Show 1x02 French.srt", "Show.S01/Subs/Show.S01E02/2_English.srt").sorted(),
            paths(selectSubtitleFiles(files, e2)).sorted()
        )
        assertEquals(
            listOf("Show.S01/Show.S01E01.en.srt", "Show.S01/Subs/Show.S01E01/2_English.srt").sorted(),
            paths(selectSubtitleFiles(files, e1)).sorted()
        )
    }

    @Test fun videoAtTheTopOfTheTorrent() {
        val video = f(0, "Movie.mkv", gb)
        val subs = selectSubtitleFiles(listOf(video, f(1, "Movie.en.srt"), f(2, "Subs/English.srt")), video)
        assertEquals(listOf("Movie.en.srt", "Subs/English.srt"), paths(subs))
    }

    @Test fun skipsHugeAndUnsupportedFilesAndCapsTheCount() {
        val video = f(0, "Movie.mkv", gb)
        val many = (1..20).map { f(it, "Subs/${it}_Lang$it.srt") }
        val files = listOf(video, f(30, "Movie.pgs.sup"), f(31, "Movie.huge.srt", 50L * 1024 * 1024), f(32, "Movie.empty.srt", 0)) + many
        val subs = selectSubtitleFiles(files, video)
        assertEquals(12, subs.size)
        assertEquals(true, subs.none { it.file.index in 30..32 })
    }
}
