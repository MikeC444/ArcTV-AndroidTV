package com.mangotv.app.data.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentIntroTest {
    @Test fun dueOnlyForEligibleUsersWhoHaveNotSeenItOncePerLaunch() {
        assertTrue(torrentIntroDue(eligible = true, alreadySeen = false, shownThisSession = false))
        assertFalse(torrentIntroDue(eligible = false, alreadySeen = false, shownThisSession = false))
        assertFalse(torrentIntroDue(eligible = true, alreadySeen = true, shownThisSession = false))
        assertFalse(torrentIntroDue(eligible = true, alreadySeen = false, shownThisSession = true))
    }
}
