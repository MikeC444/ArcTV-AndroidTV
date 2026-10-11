package com.mangotv.app.data.addon

import com.mangotv.app.data.model.AddonManifest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonSubtitlesTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private fun manifest(text: String) = json.decodeFromString(AddonManifest.serializer(), text)

    @Test
    fun `an addon that lists subtitles as a plain resource is asked`() {
        val m = manifest("""{"id":"a","name":"A","resources":["subtitles"],"types":["movie","series"]}""")
        assertTrue(m.offersSubtitles("movie", "tt1"))
        assertTrue(m.offersSubtitles("series", "tt1:1:2"))
    }

    @Test
    fun `an addon without the subtitles resource is not asked, even with no resources listed`() {
        assertFalse(manifest("""{"id":"a","name":"Cinemeta","resources":["catalog","meta"]}""").offersSubtitles("movie", "tt1"))
        assertFalse(manifest("""{"id":"a","name":"A"}""").offersSubtitles("movie", "tt1"))
        assertFalse(manifest("""{"id":"a","name":"A","resources":[{"name":"stream","types":["movie"]}]}""").offersSubtitles("movie", "tt1"))
    }

    @Test
    fun `a scoped resource only answers for its types and id prefixes`() {
        val m = manifest("""{"id":"a","name":"A","resources":[{"name":"subtitles","types":["movie"],"idPrefixes":["tt"]}]}""")
        assertTrue(m.offersSubtitles("movie", "tt1"))
        assertFalse(m.offersSubtitles("series", "tt1:1:1"))
        assertFalse(m.offersSubtitles("movie", "kitsu:1"))
    }

    @Test
    fun `the manifest's own types and id prefixes apply when the resource says nothing`() {
        val m = manifest("""{"id":"a","name":"A","types":["series"],"idPrefixes":["tt"],"resources":["subtitles"]}""")
        assertTrue(m.offersSubtitles("series", "tt9:1:1"))
        assertFalse(m.offersSubtitles("movie", "tt9"))
    }

    @Test
    fun `the answer is read, ignoring what the app doesn't use`() {
        val r = json.decodeFromString(
            StremioSubtitlesResponse.serializer(),
            """{"subtitles":[{"id":"1","url":"https://x/a.srt","lang":"eng","extra":1},{"url":"https://x/b.srt","lang":"fre"}],"cacheMaxAge":60}"""
        )
        assertEquals(2, r.subtitles.size)
        assertEquals("eng", r.subtitles[0].lang)
    }

    @Test
    fun `language codes of every spelling give the same two-letter key`() {
        assertEquals("en", languageKey("eng"))
        assertEquals("en", languageKey("en"))
        assertEquals("en", languageKey("en-US"))
        assertEquals("pt", languageKey("pt-BR"))
        assertEquals("fr", languageKey("fre"))
        assertEquals("fr", languageKey("fra"))
        assertEquals("de", languageKey("ger"))
        assertEquals("es", languageKey("Spanish"))
        assertNull(languageKey(""))
        assertNull(languageKey(null))
    }

    @Test
    fun `a language is shown by name, and by its code when it isn't known`() {
        assertEquals("English", languageDisplayName("eng"))
        assertEquals("French", languageDisplayName("fre"))
        assertEquals("XYZ", languageDisplayName("xyz"))
        assertEquals("Unknown", languageDisplayName(null))
    }

    @Test
    fun `only files with a web address are listed, once each`() {
        val list = listOf(
            StremioSubtitle(url = "https://x/a.srt", lang = "eng"),
            StremioSubtitle(url = "https://x/a.srt", lang = "eng"),
            StremioSubtitle(url = "ftp://x/b.srt", lang = "eng"),
            StremioSubtitle(url = null, lang = "eng"),
            StremioSubtitle(url = "http://x/c.srt", lang = "fre")
        ).toAddonSubtitles("Subs")
        assertEquals(listOf("https://x/a.srt", "http://x/c.srt"), list.map { it.url })
        assertEquals("English", list[0].languageName)
        assertEquals("Subs", list[0].addonName)
    }

    @Test
    fun `the preferred language comes first and each language is kept short`() {
        fun sub(lang: String, n: Int) = AddonSubtitle("A", lang, languageDisplayName(lang), "https://x/$lang$n.srt")
        val found = listOf(sub("fre", 1), sub("eng", 1), sub("eng", 2), sub("eng", 3), sub("spa", 1))
        val ranked = rankSubtitles(found, "es", perLanguage = 2)
        assertEquals(listOf("https://x/spa1.srt", "https://x/eng1.srt", "https://x/eng2.srt", "https://x/fre1.srt"), ranked.map { it.url })
    }
}
