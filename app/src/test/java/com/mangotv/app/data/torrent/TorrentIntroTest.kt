package com.mangotv.app.data.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentIntroTest {
    @Test fun freshInstallIsNotAnUpdate() {
        assertFalse(isUpdatedInstall(1_000_000, 1_000_000))
        assertFalse(isUpdatedInstall(1_000_000, 1_030_000)) // install finishing a moment after it started
        assertTrue(isUpdatedInstall(1_000_000, 9_000_000))
    }

    @Test fun dueOnlyOnceAfterAnUpdate() {
        assertTrue(torrentIntroDue(alreadySeen = false, updatedInstall = true, shownThisSession = false))
        assertFalse(torrentIntroDue(alreadySeen = true, updatedInstall = true, shownThisSession = false))
        assertFalse(torrentIntroDue(alreadySeen = false, updatedInstall = false, shownThisSession = false))
        assertFalse(torrentIntroDue(alreadySeen = false, updatedInstall = true, shownThisSession = true))
    }
}
