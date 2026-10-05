package com.mangotv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * The VLC player's controls in the style of a streaming-app TV player: back and seek icons top left, the title's logo and episode top
 * right, and along the bottom a round Play / Pause button, the elapsed time, a thin timeline and the time left, with a centred row of pills
 * under it (Audio, Subtitles, Next episode, Player, Sources). The original layout is kept in VlcControlsClassic.kt.
 */
@Composable
internal fun VlcControlsNetflix(m: VlcControlsModel, f: VlcControlFocus) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Soft fades at the top and bottom so the controls read over any picture.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(250.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
        )

        // Top left: back, and the 10-second seeks.
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = m.onBack,
                focusRequester = f.back,
                focusDown = f.play,
                onFocusChanged = { if (it) m.onFocused(f.back) },
                compact = true,
                showBackground = false,
                borderColor = Color.White,
                clickSound = ClickSound.BACK
            )
            HeroIconButton(
                icon = Icons.Filled.Replay10,
                contentDescription = "Rewind 10 seconds",
                onClick = { m.onSeek(-SEEK_STEP_MS) },
                focusRequester = f.rewind,
                focusDown = f.play,
                onFocusChanged = { if (it) m.onFocused(f.rewind) },
                compact = true,
                showBackground = false,
                borderColor = Color.White
            )
            HeroIconButton(
                icon = Icons.Filled.Forward10,
                contentDescription = "Forward 10 seconds",
                onClick = { m.onSeek(SEEK_STEP_MS) },
                focusRequester = f.forward,
                focusDown = f.play,
                onFocusChanged = { if (it) m.onFocused(f.forward) },
                compact = true,
                showBackground = false,
                borderColor = Color.White
            )
        }

        // Top right: the logo (or name), and "S1 E2 • title" for an episode.
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 28.dp),
            horizontalAlignment = Alignment.End
        ) {
            VlcTitleEnd(content = m.content, episode = m.episode)
        }

        // Bottom: the big Play / Pause button beside the timeline, and the pills under it.
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvFocusSurface(
                    onClick = m.onPlayPause,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    backgroundColor = Color.White,
                    borderColor = ArcAccent,
                    focusedElevation = 0f,
                    focusRequester = f.play,
                    focusUp = f.back,
                    onFocusChanged = { if (it) m.onFocused(f.play) },
                    bringIntoViewOnFocus = false
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (m.playing) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Text(formatTimestamp(m.positionMs), color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(14.dp))
                VlcTimeline(
                    fraction = if (m.lengthMs > 0) (m.positionMs.toFloat() / m.lengthMs).coerceIn(0f, 1f) else 0f,
                    focusRequester = f.timeline,
                    onSeek = m.onSeek,
                    onFocused = { m.onFocused(f.timeline) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(14.dp))
                Text(formatRightTime(m.positionMs, m.lengthMs, showRemaining = true), color = TextPrimary, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (m.showAudio) VlcPill("Audio", Icons.Filled.VolumeUp, m.onAudio, f.audio, f.play, m.onFocused)
                if (m.showSubtitles) VlcPill("Subtitles", Icons.Filled.Subtitles, m.onSubtitles, f.subtitles, f.play, m.onFocused)
                if (m.hasNextEpisode) VlcPill("Next episode", Icons.Filled.SkipNext, m.onNextEpisode, f.next, f.play, m.onFocused)
                VlcPill("Player", Icons.Filled.OpenInNew, m.onChoosePlayer, f.choose, f.play, m.onFocused)
                VlcPill("Sources", Icons.Filled.SwapHoriz, m.onChangeSource, f.change, f.play, m.onFocused)
            }
        }
    }
}

/** One rounded pill under the timeline. */
@Composable
private fun VlcPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    requester: FocusRequester,
    focusUp: FocusRequester,
    onFocused: (FocusRequester) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        backgroundColor = if (focused) Color.White else Color(0xFF5A5A5A).copy(alpha = 0.85f),
        borderColor = Color.White,
        focusedScale = 1.06f,
        focusedElevation = 0f,
        focusRequester = requester,
        focusUp = focusUp,
        onFocusChanged = {
            focused = it
            if (it) onFocused(requester)
        },
        bringIntoViewOnFocus = false
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = if (focused) Color.Black else Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/** Top right: the title's logo (its name when there is none), right-aligned, and "S1 E2 • title" for an episode. */
@Composable
private fun VlcTitleEnd(content: Content, episode: Episode?) {
    if (content.logoUrl != null) {
        AsyncImage(
            model = content.logoUrl,
            contentDescription = content.title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.height(56.dp).widthIn(max = 280.dp)
        )
    } else {
        Text(content.title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.End)
    }
    if (episode != null) {
        Text(
            text = "S${episode.seasonNumber} E${episode.episodeNumber} • ${episode.title}",
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End
        )
    }
}
