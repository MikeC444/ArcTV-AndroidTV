package com.mangotv.app.ui.player

import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.player.overlay.MenuOptionRow
import com.mangotv.app.ui.player.overlay.MenuOverlayScaffold
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale

private const val SEEK_STEP_MS = 10_000L
private const val FIRST_REPORT_MS = 4_000L
private const val REPORT_INTERVAL_MS = 15_000L
private const val CONTROLS_HIDE_MS = 5_000L

private enum class VlcMenu { AUDIO, SUBTITLES }

private data class VlcTrack(val id: Int, val name: String)

/**
 * Plays [url] with VLC's own engine (LibVLC), the fallback for a source the built-in player cannot decode: it ships FFmpeg's software
 * decoders for nearly every codec, so it plays formats the device's chips have no decoder for (at the cost of more CPU on big video).
 *
 * A deliberately small player of its own rather than a second engine behind the built-in player's screen: play / pause, 10-second
 * seeks, a timeline, and audio and subtitle track lists. It keeps Continue Watching working through [onReportProgress], and starts at
 * [startPositionMs] so switching over from the built-in player (or resuming) picks up where the person was.
 *
 * Keys with the controls hidden: OK / play-pause toggles playback, LEFT / RIGHT seek 10 s, any other D-pad key shows the controls.
 */
@Composable
fun VlcPlaybackContent(
    title: String,
    url: String,
    startPositionMs: Long,
    preferences: PlayerPreferences,
    onReportProgress: (positionMs: Long, durationMs: Long, completed: Boolean) -> Unit,
    onChangeSource: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    val libVlc = remember { LibVLC(context, arrayListOf("--http-reconnect", "--network-caching=2000")) }
    val mediaPlayer = remember { MediaPlayer(libVlc) }

    var viewReady by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var lengthMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<VlcMenu?>(null) }
    var audioTracks by remember { mutableStateOf<List<VlcTrack>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<VlcTrack>>(emptyList()) }
    var selectedAudio by remember { mutableIntStateOf(-1) }
    var selectedSubtitle by remember { mutableIntStateOf(-1) }
    var startApplied by remember { mutableStateOf(false) }
    var preferencesApplied by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun bump() { interactionTick++ }
    fun refreshTracks() {
        audioTracks = mediaPlayer.audioTracks?.map { VlcTrack(it.id, it.name) }.orEmpty().filter { it.id >= 0 }
        subtitleTracks = mediaPlayer.spuTracks?.map { VlcTrack(it.id, it.name) }.orEmpty()
        selectedAudio = mediaPlayer.audioTrack
        selectedSubtitle = mediaPlayer.spuTrack
    }
    fun togglePlay() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
        bump()
    }
    fun seekBy(deltaMs: Long) {
        val length = mediaPlayer.length
        val target = (mediaPlayer.time + deltaMs).coerceAtLeast(0)
        mediaPlayer.setTime(if (length > 0) target.coerceAtMost(length - 1_000) else target)
        positionMs = mediaPlayer.time
        bump()
    }

    // The first picture: pick up where the person was, and apply the account's audio / subtitle language preferences once tracks are known.
    DisposableEffect(mediaPlayer) {
        mediaPlayer.setEventListener(object : MediaPlayer.EventListener {
            override fun onEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true
                    buffering = false
                    if (!startApplied) {
                        startApplied = true
                        if (startPositionMs > 5_000) mediaPlayer.setTime(startPositionMs)
                    }
                }
                MediaPlayer.Event.Paused -> {
                    playing = false
                    onReportProgress(mediaPlayer.time, mediaPlayer.length, false)
                }
                MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
                MediaPlayer.Event.LengthChanged -> lengthMs = event.lengthChanged
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> refreshTracks()
                MediaPlayer.Event.EndReached -> {
                    playing = false
                    onReportProgress(mediaPlayer.length, mediaPlayer.length, true)
                }
                MediaPlayer.Event.EncounteredError -> failure = "VLC could not play this source either."
            }
            }
        })
        onDispose {
            mediaPlayer.setEventListener(null)
            if (mediaPlayer.length > 0) onReportProgress(mediaPlayer.time, mediaPlayer.length, false)
            mediaPlayer.detachViews()
            // Stopping and releasing can block, so not on the main thread.
            Thread {
                mediaPlayer.stop()
                mediaPlayer.release()
                libVlc.release()
            }.start()
        }
    }

    LaunchedEffect(viewReady) {
        if (!viewReady) return@LaunchedEffect
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        mediaPlayer.setMedia(media)
        media.release()
        mediaPlayer.play()
    }

    // Language preferences: match a track's name against the language's English name, the way VLC labels them ("Track 1 - [English]").
    LaunchedEffect(audioTracks, subtitleTracks) {
        if (preferencesApplied || audioTracks.isEmpty()) return@LaunchedEffect
        preferencesApplied = true
        fun matching(tracks: List<VlcTrack>, code: String?): VlcTrack? {
            if (code == null) return null
            val language = Locale(code).getDisplayLanguage(Locale.ENGLISH)
            return tracks.firstOrNull { it.name.contains(language, ignoreCase = true) }
        }
        matching(audioTracks, preferences.defaultAudioLanguage)?.let { mediaPlayer.setAudioTrack(it.id) }
        if (!preferences.subtitlesEnabled) {
            mediaPlayer.setSpuTrack(-1)
        } else {
            matching(subtitleTracks, preferences.defaultSubtitleLanguage)?.let { mediaPlayer.setSpuTrack(it.id) }
        }
        refreshTracks()
    }

    // The timeline, and Continue Watching while playing (first after a few seconds, then every 15 s).
    LaunchedEffect(playing) {
        var sinceReport = REPORT_INTERVAL_MS - FIRST_REPORT_MS
        while (true) {
            positionMs = mediaPlayer.time.coerceAtLeast(0)
            if (lengthMs <= 0) lengthMs = mediaPlayer.length.coerceAtLeast(0)
            delay(500)
            if (playing) {
                sinceReport += 500
                if (sinceReport >= REPORT_INTERVAL_MS) {
                    sinceReport = 0
                    onReportProgress(mediaPlayer.time, mediaPlayer.length, false)
                }
            }
        }
    }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Controls hide a few seconds after the last key, unless a menu is open.
    LaunchedEffect(controlsVisible, interactionTick, menu) {
        if (controlsVisible && menu == null) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, menu) {
        if (menu == null) runCatching { if (controlsVisible) playFocus.requestFocus() else rootFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || menu != null || failure != null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePlay(); true }
                    else -> if (!controlsVisible) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { togglePlay(); controlsVisible = true; true }
                            Key.DirectionLeft -> { seekBy(-SEEK_STEP_MS); true }
                            Key.DirectionRight -> { seekBy(SEEK_STEP_MS); true }
                            Key.DirectionUp, Key.DirectionDown -> { controlsVisible = true; true }
                            else -> false
                        }
                    } else {
                        bump()
                        false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    layout.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    mediaPlayer.attachViews(layout, null, true, false)
                    mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                    viewReady = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (buffering && failure == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp), color = Color.White)
        }

        failure?.let { message ->
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Unable to play this source", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MangoButton(text = "Change Source", icon = Icons.Filled.SwapHoriz, onClick = onChangeSource, style = MangoButtonStyle.GLASS, borderColor = Color.White)
                    MangoButton(text = "Back", icon = Icons.Filled.ArrowBack, onClick = onBack, style = MangoButtonStyle.GLASS, borderColor = Color.White)
                }
            }
        }

        if (controlsVisible && failure == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 40.dp, vertical = 24.dp)
            ) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTimestamp(positionMs), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f).height(5.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))) {
                        val fraction = if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
                        Box(modifier = Modifier.fillMaxWidth(fraction).height(5.dp).background(Color.White, RoundedCornerShape(3.dp)))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(formatTimestamp(lengthMs), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MangoButton(
                        text = if (playing) "Pause" else "Play",
                        icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        onClick = ::togglePlay,
                        style = MangoButtonStyle.GLASS,
                        focusRequester = playFocus,
                        borderColor = Color.White,
                        compact = true
                    )
                    MangoButton(text = "10s", icon = Icons.Filled.Replay10, onClick = { seekBy(-SEEK_STEP_MS) }, style = MangoButtonStyle.GLASS, borderColor = Color.White, compact = true)
                    MangoButton(text = "10s", icon = Icons.Filled.Forward10, onClick = { seekBy(SEEK_STEP_MS) }, style = MangoButtonStyle.GLASS, borderColor = Color.White, compact = true)
                    Spacer(Modifier.weight(1f))
                    if (audioTracks.size > 1) {
                        MangoButton(text = "Audio", icon = Icons.Filled.VolumeUp, onClick = { menu = VlcMenu.AUDIO }, style = MangoButtonStyle.GLASS, borderColor = Color.White, compact = true)
                    }
                    if (subtitleTracks.size > 1) {
                        MangoButton(text = "Subtitles", icon = Icons.Filled.Subtitles, onClick = { menu = VlcMenu.SUBTITLES }, style = MangoButtonStyle.GLASS, borderColor = Color.White, compact = true)
                    }
                    MangoButton(text = "Change Source", icon = Icons.Filled.SwapHoriz, onClick = onChangeSource, style = MangoButtonStyle.GLASS, borderColor = Color.White, compact = true)
                }
            }
        }

        when (menu) {
            VlcMenu.AUDIO -> VlcTrackMenu("Audio", audioTracks, selectedAudio) { track ->
                mediaPlayer.setAudioTrack(track.id)
                refreshTracks()
                menu = null
            }
            VlcMenu.SUBTITLES -> VlcTrackMenu("Subtitles", subtitleTracks, selectedSubtitle) { track ->
                mediaPlayer.setSpuTrack(track.id)
                refreshTracks()
                menu = null
            }
            null -> Unit
        }
    }

    BackHandler {
        when {
            menu != null -> menu = null
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }
}

@Composable
private fun VlcTrackMenu(title: String, tracks: List<VlcTrack>, selectedId: Int, onSelect: (VlcTrack) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    MenuOverlayScaffold(title = title) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                MenuOptionRow(
                    label = if (track.id < 0) "Off" else track.name,
                    isSelected = track.id == selectedId,
                    onClick = { onSelect(track) },
                    focusRequester = if (index == 0) firstFocus else null
                )
            }
        }
    }
}
