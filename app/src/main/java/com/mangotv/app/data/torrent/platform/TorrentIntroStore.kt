package com.mangotv.app.data.torrent.platform

import android.content.Context
import com.mangotv.app.data.torrent.isUpdatedInstall

/** Remembers, on this device, that the "Torrents are here" pop-up has been dealt with. */
class TorrentIntroStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("arctv_intros", Context.MODE_PRIVATE)

    @Volatile var shownThisSession = false
        private set

    init {
        // A fresh install never sees it (nothing changed for them), and a later update must not show it either.
        if (!prefs.contains(KEY) && !updated()) prefs.edit().putBoolean(KEY, true).apply()
    }

    val seen: Boolean get() = prefs.getBoolean(KEY, false)

    fun updated(): Boolean = try {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        isUpdatedInstall(info.firstInstallTime, info.lastUpdateTime)
    } catch (_: Exception) {
        false
    }

    /** Called when the pop-up goes on screen: whatever the person does with it, it is not shown again. */
    fun markShown() {
        shownThisSession = true
        prefs.edit().putBoolean(KEY, true).apply()
    }

    private companion object { const val KEY = "torrent_intro_seen" }
}
