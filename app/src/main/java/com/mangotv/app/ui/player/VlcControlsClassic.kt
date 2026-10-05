package com.mangotv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.theme.TextSecondary

/**
 * BACKUP of the original VLC controls (before the Netflix-style layout): the logo and speed above one row of icons (play / pause, back and
 * forward 10 s, elapsed time, the timeline, time left, then Audio, Subtitles, Speed, Next episode, Choose player and Change source).
 * Unchanged so it can be brought back by setting USE_CLASSIC_VLC_CONTROLS to true in VlcControls.kt.
 */
@Composable
internal fun VlcControlsClassic(m: VlcControlsModel, f: VlcControlFocus) {
    Box(modifier = Modifier.fillMaxSize()) {
        // A soft fade up from the bottom so the controls read over any picture, then the same one-row layout as the built-in player.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp)) {
            VlcTitle(content = m.content, episode = m.episode)
            Text(
                text = m.speedLabel + " speed",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroIconButton(
                    icon = if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (m.playing) "Pause" else "Play",
                    onClick = m.onPlayPause,
                    focusRequester = f.play,
                    focusDown = f.timeline,
                    onFocusChanged = { if (it) m.onFocused(f.play) },
                    showBackground = false,
                    borderColor = Color.White
                )
                Spacer(Modifier.width(10.dp))
                HeroIconButton(
                    icon = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    onClick = { m.onSeek(-SEEK_STEP_MS) },
                    focusRequester = f.rewind,
                    onFocusChanged = { if (it) m.onFocused(f.rewind) },
                    focusDown = f.timeline,
                    compact = true,
                    showBackground = false,
                    borderColor = Color.White
                )
                Spacer(Modifier.width(10.dp))
                HeroIconButton(
                    icon = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    onClick = { m.onSeek(SEEK_STEP_MS) },
                    focusRequester = f.forward,
                    onFocusChanged = { if (it) m.onFocused(f.forward) },
                    focusDown = f.timeline,
                    compact = true,
                    showBackground = false,
                    borderColor = Color.White
                )
                Spacer(Modifier.width(18.dp))
                Text(formatTimestamp(m.positionMs), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(14.dp))
                VlcTimeline(
                    fraction = if (m.lengthMs > 0) (m.positionMs.toFloat() / m.lengthMs).coerceIn(0f, 1f) else 0f,
                    focusRequester = f.timeline,
                    onSeek = m.onSeek,
                    onFocused = { m.onFocused(f.timeline) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(14.dp))
                Text(formatRightTime(m.positionMs, m.lengthMs, showRemaining = true), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(18.dp))
                if (m.showAudio) {
                    HeroIconButton(icon = Icons.Filled.VolumeUp, contentDescription = "Audio", onClick = m.onAudio, focusRequester = f.audio, onFocusChanged = { if (it) m.onFocused(f.audio) }, focusDown = f.timeline, compact = true, showBackground = false, borderColor = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                if (m.showSubtitles) {
                    HeroIconButton(icon = Icons.Filled.Subtitles, contentDescription = "Subtitles", onClick = m.onSubtitles, focusRequester = f.subtitles, onFocusChanged = { if (it) m.onFocused(f.subtitles) }, focusDown = f.timeline, compact = true, showBackground = false, borderColor = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                HeroIconButton(icon = Icons.Filled.Speed, contentDescription = "Playback speed", onClick = m.onSpeed, focusRequester = f.speed, onFocusChanged = { if (it) m.onFocused(f.speed) }, focusDown = f.timeline, compact = true, showBackground = false, borderColor = Color.White)
                Spacer(Modifier.width(8.dp))
                if (m.hasNextEpisode) {
                    HeroIconButton(icon = Icons.Filled.SkipNext, contentDescription = "Next episode", onClick = m.onNextEpisode, focusRequester = f.next, onFocusChanged = { if (it) m.onFocused(f.next) }, focusDown = f.timeline, compact = true, showBackground = false, borderColor = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                HeroIconButton(icon = Icons.Filled.OpenInNew, contentDescription = "Choose player", onClick = m.onChoosePlayer, focusRequester = f.choose, onFocusChanged = { if (it) m.onFocused(f.choose) }, focusDown = f.timeline, compact = true, showBackground = false, borderColor = Color.White)
                Spacer(Modifier.width(8.dp))
                HeroIconButton(icon = Icons.Filled.SwapHoriz, contentDescription = "Change source", onClick = m.onChangeSource, focusRequester = f.change, onFocusChanged = { if (it) m.onFocused(f.change) }, focusDown = f.timeline, compact = true, showBackground = false, borderColor = Color.White)
            }
        }
    }
}
