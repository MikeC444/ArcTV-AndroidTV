package com.mangotv.app.data.torrent

/** Installing over a running copy (an update) leaves the last-update time later than the first-install time; a fresh install has them equal. */
fun isUpdatedInstall(firstInstallTimeMs: Long, lastUpdateTimeMs: Long): Boolean = lastUpdateTimeMs - firstInstallTimeMs > 60_000L

/**
 * Whether the one-off "Torrents are here" pop-up is due: only for someone who updated (not a fresh install, who has nothing to compare it to),
 * only once ([alreadySeen] is recorded the first time it is on screen, or at once on a fresh install), and once per launch.
 */
fun torrentIntroDue(alreadySeen: Boolean, updatedInstall: Boolean, shownThisSession: Boolean): Boolean =
    !alreadySeen && updatedInstall && !shownThisSession
