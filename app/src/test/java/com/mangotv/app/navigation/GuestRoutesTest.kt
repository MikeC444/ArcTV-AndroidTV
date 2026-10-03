package com.mangotv.app.navigation

import com.mangotv.app.data.model.ContentType
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.navItemsForGuest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestRoutesTest {

    @Test
    fun `play, my list and settings need an account`() {
        assertTrue(routeNeedsAccount(MangoRoutes.MY_LIST))
        assertTrue(routeNeedsAccount(MangoRoutes.SETTINGS))
        assertTrue(routeNeedsAccount(MangoRoutes.SETTINGS_ADD_ADDON))
        assertTrue(routeNeedsAccount(MangoRoutes.sources("p", ContentType.MOVIE, "tt1")))
        assertTrue(routeNeedsAccount(MangoRoutes.player("p", ContentType.TV_SHOW, "tt1", 1, 2, "stream")))
    }

    @Test
    fun `everything else can be browsed as a guest`() {
        listOf(
            MangoRoutes.HOME, MangoRoutes.MOVIES, MangoRoutes.TV_SHOWS, MangoRoutes.SEARCH,
            MangoRoutes.detail("p", ContentType.MOVIE, "tt1")
        ).forEach { assertFalse(it, routeNeedsAccount(it)) }
    }

    @Test
    fun `a guest sees Sign In where Settings would be, in the same place`() {
        val guest = navItemsForGuest(MangoNavItems)
        assertEquals(MangoNavItems.size, guest.size)
        assertEquals("Sign In", guest.last())
        assertEquals(MangoNavItems.dropLast(1), guest.dropLast(1))
        assertEquals(MangoRoutes.AUTH_START, routeForNavLabel("Sign In"))
    }
}
