package com.mangotv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Stream
import com.mangotv.app.data.torrent.TorrentStreamState
import com.mangotv.app.data.torrent.describeTorrentProgress
import com.mangotv.app.data.torrent.describeTorrentStall
import com.mangotv.app.data.torrent.platform.TorrentPlayback
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.theme.TextPrimary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private enum class TorrentStage { LOADING, PLAYING, FAILED }

/**
 * Plays [stream] through [playing]: unchanged when it is an ordinary link (direct, HLS, DASH, debrid), and for a torrent (a magnet link, a
 * .torrent file, or an info hash from an addon) by first starting the embedded torrent engine, showing its progress on the loading screen,
 * and then handing [playing] the same source with its address swapped for the engine's local one, so both players (built-in and VLC) play it
 * like any other link. Failures show their reason with a way to retry or pick another source. The torrent is stopped and its temporary files
 * deleted whenever this leaves the screen: the player is closed, the source changes, or the next episode starts.
 */
@Composable
fun TorrentSourceHost(
    content: Content,
    episode: Episode?,
    stream: Stream,
    season: Int?,
    episodeNumber: Int?,
    onChangeSource: () -> Unit,
    playing: @Composable (Stream) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { (context.applicationContext as MangoTvApplication).container.torrentStreamManager }
    val isTorrent = remember(stream.id) { manager.refFor(stream) != null }
    if (!isTorrent) {
        playing(stream)
        return
    }

    var attempt by remember(stream.id) { mutableIntStateOf(0) }
    var playback by remember(stream.id, attempt) { mutableStateOf<TorrentPlayback?>(null) }
    DisposableEffect(stream.id, attempt) {
        val started = manager.open(stream, season, episodeNumber)
        playback = started
        onDispose {
            started.close()
        }
    }

    val current = playback
    if (current == null) {
        PlayerLoadingScreen(content = content, episode = episode, busy = true, status = describeTorrentProgress(TorrentStreamState.Starting))
        return
    }
    val stage by remember(current) {
        current.state.map {
            when (it) {
                is TorrentStreamState.Playing -> TorrentStage.PLAYING
                is TorrentStreamState.Failed, TorrentStreamState.Closed -> TorrentStage.FAILED
                else -> TorrentStage.LOADING
            }
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = TorrentStage.LOADING)

    when (stage) {
        TorrentStage.LOADING -> {
            val state by current.state.collectAsStateWithLifecycle()
            PlayerLoadingScreen(content = content, episode = episode, busy = true, status = describeTorrentProgress(state))
        }
        TorrentStage.PLAYING -> {
            val url = (current.state.value as? TorrentStreamState.Playing)?.url
            if (url != null) {
                val local = remember(stream.id, url) { stream.copy(url = url) }
                Box(modifier = Modifier.fillMaxSize()) {
                    playing(local)
                    TorrentStallNote(current)
                }
            }
        }
        TorrentStage.FAILED -> {
            val failure = (current.state.value as? TorrentStreamState.Failed)?.error
            FullScreenErrorState(
                message = failure?.message ?: "Couldn't play this torrent.",
                onRetry = { attempt++ },
                secondaryActionLabel = "Choose a Different Source",
                onSecondaryAction = onChangeSource
            )
        }
    }
}

/** A small note over the picture while playback waits for torrent data; the player's own spinner shows alongside it. */
@Composable
private fun TorrentStallNote(playback: TorrentPlayback) {
    val state by playback.state.collectAsStateWithLifecycle()
    val note = (state as? TorrentStreamState.Playing)?.let { describeTorrentStall(it.stats) } ?: return
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomStart) {
        Text(
            text = note,
            color = TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
