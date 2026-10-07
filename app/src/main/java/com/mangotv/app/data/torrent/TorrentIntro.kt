package com.mangotv.app.data.torrent

/**
 * The one-off "Addons now support torrents" pop-up is for accounts that already existed when this version was first opened on the device
 * ([eligible]); it is due until that user has clicked it away once ([alreadySeen]), and at most once per launch.
 */
fun torrentIntroDue(eligible: Boolean, alreadySeen: Boolean, shownThisSession: Boolean): Boolean =
    eligible && !alreadySeen && !shownThisSession
