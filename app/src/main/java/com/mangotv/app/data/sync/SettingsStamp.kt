package com.mangotv.app.data.sync

/**
 * The account keeps the settings write with the later timestamp, and the timestamp is this device's own clock. A device whose clock is behind
 * what the account last saw (or an account last written by a device whose clock runs ahead) would have every change refused and the old values
 * handed straight back, so a switch just turned on snapped off again. These keep a change the person has just made ahead of what the account
 * already holds.
 */
object SettingsStamp {
    /** The time to send a change with: now, but never at or before the latest time the account has reported. */
    fun next(nowMs: Long, accountMs: Long): Long = maxOf(nowMs, accountMs + 1)

    /** Whether the account kept a later write than the one just sent (so the change was refused and the answer holds the old values). */
    fun refused(sentMs: Long, accountMs: Long?): Boolean = accountMs != null && accountMs > sentMs
}
