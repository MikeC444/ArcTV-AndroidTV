package com.mangotv.app.data.cast

import com.mangotv.app.data.model.CastMember
import com.mangotv.app.data.network.CastEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CastMergeTest {

    private val tmdb = listOf(
        CastEntryDto("Tim Robbins", "Andy Dufresne", "https://image.tmdb.org/t/p/w185/a.jpg"),
        CastEntryDto("Morgan Freeman", "Ellis Boyd 'Red' Redding", null),
        CastEntryDto("Zoë Saldaña", "Neytiri", "https://image.tmdb.org/t/p/w185/z.jpg")
    )

    @Test
    fun `adds photos and characters to the addon's names, keeping its order`() {
        val merged = mergeCast(listOf(CastMember("Morgan Freeman"), CastMember("tim robbins"), CastMember("Unknown Actor")), tmdb)
        assertEquals(listOf("Morgan Freeman", "tim robbins", "Unknown Actor"), merged.map { it.name })
        assertEquals(CastMember("tim robbins", "Andy Dufresne", "https://image.tmdb.org/t/p/w185/a.jpg"), merged[1])
        assertEquals(CastMember("Morgan Freeman", "Ellis Boyd 'Red' Redding", null), merged[0])
        assertEquals(CastMember("Unknown Actor"), merged[2])
    }

    @Test
    fun `matches names ignoring accents and punctuation`() {
        assertEquals("https://image.tmdb.org/t/p/w185/z.jpg", mergeCast(listOf(CastMember("Zoe Saldana")), tmdb)[0].photoUrl)
        assertEquals("Andy Dufresne", mergeCast(listOf(CastMember("Tim  Robbins.")), tmdb)[0].role)
    }

    @Test
    fun `never overwrites what the addon sent`() {
        val addon = listOf(CastMember("Tim Robbins", role = "Andy", photoUrl = "https://cinemeta.example/tim.jpg"))
        assertEquals(addon, mergeCast(addon, tmdb))
    }

    @Test
    fun `fills only the missing half when the addon sent one`() {
        val merged = mergeCast(listOf(CastMember("Tim Robbins", photoUrl = "https://cinemeta.example/tim.jpg")), tmdb)
        assertEquals(CastMember("Tim Robbins", "Andy Dufresne", "https://cinemeta.example/tim.jpg"), merged[0])
    }

    @Test
    fun `uses the TMDB list when the addon sent none`() {
        val merged = mergeCast(emptyList(), tmdb)
        assertEquals(3, merged.size)
        assertEquals(CastMember("Tim Robbins", "Andy Dufresne", "https://image.tmdb.org/t/p/w185/a.jpg"), merged[0])
    }

    @Test
    fun `leaves the addon's list untouched when TMDB sent nothing`() {
        val addon = listOf(CastMember("A"))
        assertSame(addon, mergeCast(addon, emptyList()))
    }
}
