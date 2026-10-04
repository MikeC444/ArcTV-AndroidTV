package com.mangotv.app.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mangotv.app.ui.components.TvFocusSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * The full bottom control row: transport (play/pause, rewind10, forward10)
 * on the left, the timeline filling the center, and the subtitle/audio/
 * quality/settings/next-episode icon cluster on the right — all in one row,
 * matching the reference layout.
 *
 * The timeline sits in natural left/right tab order between forward10 and
 * the icon row (default Compose focus search finds it on its own, since
 * the intervening TimeText labels aren't focusable) — this is the primary,
 * discoverable way to reach it: RIGHT from forward10 or LEFT from the
 * first icon moves focus onto the timeline. Merely being focused doesn't
 * capture LEFT/RIGHT, though — the user has to press DPAD_CENTER/Enter to
 * select it and enter scrub mode (root key interceptor), so LEFT/RIGHT can
 * still move focus past the timeline onto the next button instead of
 * getting stuck the instant it's highlighted. Every button also pins
 * focusDown to the timeline as a shortcut, since there's nothing visually
 * below this row for the default search to find on its own.
 */
@Composable
fun PlayerBottomControls(
    exoPlayer: ExoPlayer,
    phase: PlaybackPhase,
    showNextEpisode: Boolean,
    showRemaining: Boolean,
    onToggleRemaining: () -> Unit,
    showSubtitles: Boolean,
    showAudio: Boolean,
    showQuality: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onQuality: () -> Unit,
    onSettings: () -> Unit,
    onNextEpisode: () -> Unit,
    onFocusZoneChanged: (PlayerFocusZone) -> Unit,
    modifier: Modifier = Modifier,
    isTimelineScrubbing: Boolean = false,
    playPauseFocusRequester: FocusRequester? = null,
    rewindFocusRequester: FocusRequester? = null,
    forwardFocusRequester: FocusRequester? = null,
    subtitleFocusRequester: FocusRequester? = null,
    audioFocusRequester: FocusRequester? = null,
    qualityFocusRequester: FocusRequester? = null,
    settingsFocusRequester: FocusRequester? = null,
    nextEpisodeFocusRequester: FocusRequester? = null,
    timelineFocusRequester: FocusRequester? = null
) {
    val isPlaying = phase is PlaybackPhase.Playing
    val onTransportFocused: (Boolean) -> Unit = { if (it) onFocusZoneChanged(PlayerFocusZone.TRANSPORT) }
    val onIconRowFocused: (Boolean) -> Unit = { if (it) onFocusZoneChanged(PlayerFocusZone.ICON_ROW) }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeroIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            focusRequester = playPauseFocusRequester,
            focusDown = timelineFocusRequester,
            onFocusChanged = onTransportFocused,
            showBackground = false,
            borderColor = Color.White
        )
        Spacer(Modifier.width(10.dp))
        HeroIconButton(
            icon = Icons.Filled.Replay10,
            contentDescription = "Rewind 10 seconds",
            onClick = { onSeek(-10_000) },
            focusRequester = rewindFocusRequester,
            focusDown = timelineFocusRequester,
            onFocusChanged = onTransportFocused,
            compact = true,
            showBackground = false,
            borderColor = Color.White
        )
        Spacer(Modifier.width(10.dp))
        HeroIconButton(
            icon = Icons.Filled.Forward10,
            contentDescription = "Forward 10 seconds",
            onClick = { onSeek(10_000) },
            focusRequester = forwardFocusRequester,
            focusDown = timelineFocusRequester,
            onFocusChanged = onTransportFocused,
            compact = true,
            showBackground = false,
            borderColor = Color.White
        )

        Spacer(Modifier.width(18.dp))
        TimeText(exoPlayer = exoPlayer, phase = phase, useDuration = false)
        Spacer(Modifier.width(14.dp))

        PlayerTimeline(
            exoPlayer = exoPlayer,
            phase = phase,
            isScrubbing = isTimelineScrubbing,
            onFocusChanged = { focused -> if (focused) onFocusZoneChanged(PlayerFocusZone.TIMELINE) },
            modifier = Modifier.weight(1f),
            focusRequester = timelineFocusRequester,
            focusUp = playPauseFocusRequester
        )

        Spacer(Modifier.width(14.dp))
        RightTime(
            exoPlayer = exoPlayer,
            phase = phase,
            showRemaining = showRemaining,
            onToggle = onToggleRemaining,
            focusDown = timelineFocusRequester,
            onFocusChanged = onIconRowFocused
        )
        Spacer(Modifier.width(18.dp))

        if (showSubtitles) {
            HeroIconButton(
                icon = Icons.Filled.Subtitles,
                contentDescription = "Subtitles",
                onClick = onSubtitles,
                focusRequester = subtitleFocusRequester,
                focusDown = timelineFocusRequester,
                onFocusChanged = onIconRowFocused,
                compact = true,
                showBackground = false,
                borderColor = Color.White
            )
            Spacer(Modifier.width(8.dp))
        }
        if (showAudio) {
            HeroIconButton(
                icon = Icons.Filled.VolumeUp,
                contentDescription = "Audio",
                onClick = onAudio,
                focusRequester = audioFocusRequester,
                focusDown = timelineFocusRequester,
                onFocusChanged = onIconRowFocused,
                compact = true,
                showBackground = false,
                borderColor = Color.White
            )
            Spacer(Modifier.width(8.dp))
        }
        if (showQuality) {
            HeroIconButton(
                icon = Icons.Filled.HighQuality,
                contentDescription = "Quality",
                onClick = onQuality,
                focusRequester = qualityFocusRequester,
                focusDown = timelineFocusRequester,
                onFocusChanged = onIconRowFocused,
                compact = true,
                showBackground = false,
                borderColor = Color.White
            )
            Spacer(Modifier.width(8.dp))
        }
        HeroIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Player settings",
            onClick = onSettings,
            focusRequester = settingsFocusRequester,
            focusDown = timelineFocusRequester,
            onFocusChanged = onIconRowFocused,
            compact = true,
            showBackground = false,
            borderColor = Color.White
        )
        if (showNextEpisode) {
            Spacer(Modifier.width(8.dp))
            HeroIconButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = "Next episode",
                onClick = onNextEpisode,
                focusRequester = nextEpisodeFocusRequester,
                focusDown = timelineFocusRequester,
                onFocusChanged = onIconRowFocused,
                compact = true,
                showBackground = false,
                borderColor = Color.White
            )
        }
    }
}

/**
 * Polls independently so a tick only recomposes this one small Text rather
 * than the whole control row (which also hosts 8 focusable buttons).
 */
@Composable
private fun TimeText(exoPlayer: ExoPlayer, phase: PlaybackPhase, useDuration: Boolean) {
    var valueMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(phase, useDuration) {
        while (true) {
            valueMs = (if (useDuration) exoPlayer.duration else exoPlayer.currentPosition).coerceAtLeast(0)
            delay(500)
        }
    }

    Text(text = formatTimestamp(valueMs), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
}

/** The right-hand time: time left ("-12:34") by default, the total length once pressed; the choice is remembered (as on the web app). */
@Composable
private fun RightTime(
    exoPlayer: ExoPlayer,
    phase: PlaybackPhase,
    showRemaining: Boolean,
    onToggle: () -> Unit,
    focusDown: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phase) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0)
            durationMs = exoPlayer.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    TvFocusSurface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = Color.Transparent,
        borderColor = Color.White,
        focusedScale = 1f,
        focusedElevation = 0f,
        focusDown = focusDown,
        onFocusChanged = onFocusChanged,
        bringIntoViewOnFocus = false
    ) {
        Text(
            text = formatRightTime(positionMs, durationMs, showRemaining),
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

internal fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
