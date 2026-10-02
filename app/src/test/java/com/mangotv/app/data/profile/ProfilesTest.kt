package com.mangotv.app.data.profile

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.network.profileHeaderValue
import com.mangotv.app.data.provider.blockedGenreSet
import com.mangotv.app.data.provider.isBlockedBy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {

    @After
    fun resetActiveProfile() = ActiveProfile.reset()

    private fun profile(id: String, default: Boolean = false, kind: String = KIND_ADULT, pin: Boolean = false) =
        Profile(id = id, name = id, avatar = "fox", kind = kind, hasPin = pin, isDefault = default)

    private fun movie(vararg genres: String) = Content(
        id = "tt1", type = ContentType.MOVIE, title = "T", description = "", posterUrl = null, backdropUrl = null,
        genres = genres.map { Genre(id = it, name = it) }
    )

    @Test
    fun theProfileHeaderIsLeftOffForTheAccountsOwnProfile() {
        assertNull(profileHeaderValue("/user/watchlist", "main"))
        assertNull(profileHeaderValue("/user/settings", ActiveProfile.DEFAULT_ID))
    }

    @Test
    fun everyLibraryCallCarriesTheActiveProfile() {
        for (path in listOf("/user/watchlist", "/user/watch-progress", "/user/continue-watching", "/user/history", "/user/settings", "/user/addons", "/user/feedback")) {
            assertEquals(path, "p_abc", profileHeaderValue(path, "p_abc"))
        }
    }

    @Test
    fun accountLevelCallsNeverCarryAProfile() {
        for (path in listOf("/user/me", "/user/plus", "/user/plus/checkout", "/user/profiles", "/user/profiles/p_abc/verify-pin", "/user/profiles/p_abc")) {
            assertNull(path, profileHeaderValue(path, "p_abc"))
        }
    }

    @Test
    fun aPathPrefixInFrontOfTheApiDoesNotConfuseIt() {
        assertEquals("p_abc", profileHeaderValue("/api/v1/user/watchlist", "p_abc"))
        assertNull(profileHeaderValue("/api/v1/user/profiles", "p_abc"))
        // authentication and anything outside /user/ is not profile data
        assertNull(profileHeaderValue("/auth/refresh", "p_abc"))
        // a title that merely starts like an account-level path is still library data
        assertEquals("p_abc", profileHeaderValue("/user/mementos", "p_abc"))
    }

    @Test
    fun theActiveProfileIsRememberedAndKidsFollowsIt() {
        assertEquals("main", ActiveProfile.id)
        assertFalse(ActiveProfile.kids.value)
        ActiveProfile.set("p_kid", kids = true)
        assertEquals("p_kid", ActiveProfile.id)
        assertTrue(ActiveProfile.kids.value)
        ActiveProfile.reset()
        assertEquals("main", ActiveProfile.id)
        assertFalse(ActiveProfile.kids.value)
    }

    @Test
    fun theLaunchPickerNeedsPlusMoreThanOneProfileAndNoChoiceYet() {
        assertTrue(needsProfilePicker(supported = true, plus = true, profileCount = 2, chosen = false))
        assertFalse("already chose this launch", needsProfilePicker(true, true, 2, chosen = true))
        assertFalse("only one profile", needsProfilePicker(true, true, 1, false))
        assertFalse("no Plus", needsProfilePicker(true, false, 2, false))
        assertFalse("older backend", needsProfilePicker(false, true, 2, false))
    }

    @Test
    fun withoutPlusOnlyTheAccountsOwnProfileIsUsable() {
        val all = listOf(profile("main", default = true), profile("p_1"), profile("p_2", kind = KIND_KIDS))
        assertEquals(all, visibleProfiles(all, plus = true))
        assertEquals(listOf("main"), visibleProfiles(all, plus = false).map { it.id })
    }

    @Test
    fun aPinIsExactlyFourDigits() {
        assertTrue(isValidPin("0000"))
        assertTrue(isValidPin("1234"))
        for (bad in listOf("", "123", "12345", "12a4", " 123", "١٢٣٤")) assertFalse("'$bad'", isValidPin(bad))
    }

    @Test
    fun profileKindsAndTheLimit() {
        assertTrue(profile("p", kind = KIND_KIDS).isKids)
        assertFalse(profile("p").isKids)
        assertEquals(5, PROFILE_LIMIT)
        assertEquals(24, PROFILE_NAME_MAX)
        assertEquals(4, PIN_LENGTH)
    }

    @Test
    fun theAvatarIdsAreTheOnesTheWebAppAndBackendUse() {
        assertEquals(
            listOf("sunrise", "ocean", "forest", "violet", "ember", "mint", "astro", "monster", "fox", "robot", "wave", "bolt"),
            AVATARS.map { it.id }
        )
        assertEquals("fox", avatarById("fox").id)
        assertEquals("an unknown id still draws something", AVATARS.first().id, avatarById("nope").id)
    }

    @Test
    fun theKidsGenresHideTheMatureTitlesAndNothingElse() {
        val blocked = blockedGenreSet(KIDS_BLOCKED_GENRES)
        for (genre in listOf("Horror", "Thriller", "Crime", "War", "Mystery", "Film-Noir", "Adult", "horror")) {
            assertTrue(genre, movie(genre).isBlockedBy(blocked))
        }
        assertTrue("one blocked genre among others", movie("Comedy", "Horror").isBlockedBy(blocked))
        for (genre in listOf("Animation", "Family", "Comedy", "Adventure", "Fantasy")) assertFalse(genre, movie(genre).isBlockedBy(blocked))
        assertFalse("no genres can't be matched", movie().isBlockedBy(blocked))
    }
}
