package com.mangotv.app.ui.settings

/**
 * A tiny hand-off slot for "open Settings on the Arc TV Plus tab" (the Plus popup's "Take me there"), the same shape as PendingDetailCache:
 * the caller sets it just before navigating to Settings, and SettingsScreen consumes it once when it opens, so a later plain visit to
 * Settings still starts on Account.
 */
object PendingSettingsTab {
    private var plus = false

    fun openPlus() {
        plus = true
    }

    fun takePlus(): Boolean = plus.also { plus = false }

    private var plusSettings = false

    fun openPlusSettings() {
        plusSettings = true
    }

    fun takePlusSettings(): Boolean = plusSettings.also { plusSettings = false }
}
