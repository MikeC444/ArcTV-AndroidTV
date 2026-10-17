package com.mangotv.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** The shortest time between two up / down moves while a D-pad key is held (a press that is not a repeat is never held back). */
const val VERTICAL_REPEAT_INTERVAL_MS = 220L

/**
 * Whether a D-pad up / down key event may move focus: always for a fresh press ([repeatCount] 0), and for a held key's repeats only once [intervalMs] has passed
 * since the last move that was let through. Without it a held key fires a move every few milliseconds and the page flies, and stalls, on a Fire TV.
 */
fun allowVerticalMove(repeatCount: Int, nowMs: Long, lastMoveMs: Long, intervalMs: Long = VERTICAL_REPEAT_INTERVAL_MS): Boolean =
    repeatCount == 0 || nowMs - lastMoveMs >= intervalMs

/** Slows how fast a held D-pad up / down key can scroll the screen under this modifier (Home and the catalogue grids). Left / right and fresh presses are untouched. */
fun Modifier.limitVerticalKeyRepeat(intervalMs: Long = VERTICAL_REPEAT_INTERVAL_MS): Modifier = composed {
    var lastMoveMs by remember { mutableLongStateOf(0L) }
    onPreviewKeyEvent { event ->
        if ((event.key == Key.DirectionUp || event.key == Key.DirectionDown) && event.type == KeyEventType.KeyDown) {
            val native = event.nativeKeyEvent
            val now = native.eventTime
            if (!allowVerticalMove(native.repeatCount, now, lastMoveMs, intervalMs)) {
                true // swallowed: this repeat comes too soon after the last move
            } else {
                lastMoveMs = now
                false
            }
        } else {
            false
        }
    }
}
