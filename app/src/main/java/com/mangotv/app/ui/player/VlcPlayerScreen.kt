package com.mangotv.app.ui.player

import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.player.overlay.MenuOptionRow
import com.mangotv.app.ui.player.overlay.MenuOverlayScaffold
import com.mangotv.app.ui.player.overlay.PlayerChoiceCard
import com.mangotv.app.ui.player.overlay.PlayerChoiceOption
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale

internal const val SEEK_STEP_MS = 10_000L
private const val FIRST_REPORT_MS = 4_000L
private const val SEEK_APPLY_DELAY_MS = 350L
private const val OFFER_FOCUS_DELAY_MS = 3_000L
private const val REPORT_INTERVAL_MS = 15_000L
private const val CONTROLS_HIDE_MS = 5_000L
private val SPEEDS = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)

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
    content: Content,
    episode: Episode?,
    url: String,
    startPositionMs: Long,
    preferences: PlayerPreferences,
    onReportProgress: (positionMs: Long, durationMs: Long, completed: Boolean) -> Unit,
    // The Choose player card's other rows: back to the built-in player from the given position, or another app (true when one opened).
    onPlayWithBuiltIn: (positionMs: Long) -> Unit,
    onOpenExternal: () -> Boolean,
    // Called when the card's built-in or VLC row is played, so the title remembers it.
    onRememberPlayer: (PreferredPlayer) -> Unit,
    // The episode after this one (null for a movie or the last episode) and how to start it: it is offered in the last minute, and counted
    // down to after the end when Auto Play Next Episode is on, exactly as in the built-in player.
    next: NextEpisode?,
    onNextEpisode: (season: Int, episode: Int) -> Unit,
    onChangeSource: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    val libVlc = remember { LibVLC(context, arrayListOf("--http-reconnect", "--network-caching=2000", "--no-drop-late-frames", "--no-skip-frames")) }
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
    var showChoice by remember { mutableStateOf(false) }
    // True once the first picture plays: until then the loading screen (backdrop and logo) covers the video.
    var started by remember { mutableStateOf(false) }
    // True for the rest of a confirm press that only revealed the controls, so its repeat / release can't click the button it landed on.
    var revealingKeyHeld by remember { mutableStateOf(false) }
    // The control the cursor was last on: it returns there after a menu closes, but not after the controls hid (then it starts on Play).
    var lastFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var upNext by remember { mutableStateOf<NextEpisode?>(null) }
    var offerNext by remember { mutableStateOf(false) }
    var offerFocused by remember { mutableStateOf(false) }
    // Up or BACK on the focused Next episode button dismisses it for the rest of this episode.
    var offerDismissed by remember { mutableStateOf(false) }
    val nextOfferFocus = remember { FocusRequester() }
    // Settings > Audio: passthrough and the speaker layout, read once when playback starts.
    val audioPassthrough = remember { DevicePlayerPrefs.audioPassthrough(context) }
    val audioMode = remember { DevicePlayerPrefs.audioChannelMode(context) }
    var audioTracks by remember { mutableStateOf<List<VlcTrack>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<VlcTrack>>(emptyList()) }
    var selectedAudio by remember { mutableIntStateOf(-1) }
    var selectedSubtitle by remember { mutableIntStateOf(-1) }
    var preferencesApplied by remember { mutableStateOf(false) }

    var speedIndex by remember { mutableIntStateOf(1) }
    var seekText by remember { mutableStateOf<String?>(null) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    var scrubOrigin by remember { mutableStateOf<Long?>(null) }
    val rootFocus = remember { FocusRequester() }
    val focus = remember { VlcControlFocus() }

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
    // Seeking while a key is held: each press only moves the target (the timeline and the pill follow at once); VLC is asked to jump once,
    // a moment after the last press. Jumping on every press blocked the screen with a backlog of seeks, which left the pill stuck up.
    fun seekBy(deltaMs: Long) {
        val length = lengthMs.takeIf { it > 0 } ?: mediaPlayer.length
        val origin = scrubOrigin ?: mediaPlayer.time.also { scrubOrigin = it }
        val base = scrubTarget ?: origin
        var target = (base + deltaMs).coerceAtLeast(0)
        if (length > 1_000) target = target.coerceAtMost(length - 1_000)
        scrubTarget = target
        positionMs = target
        val moved = target - origin
        val seconds = kotlin.math.abs(moved) / 1000
        seekText = (if (moved < 0) "−" else "+") + (if (seconds >= 60) "${seconds / 60} min ${seconds % 60} s" else "${seconds}s")
        bump()
    }
    fun dismissOffer() {
        offerDismissed = true
        offerFocused = false
        runCatching { rootFocus.requestFocus() }
    }
    fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.size
        mediaPlayer.setRate(SPEEDS[speedIndex])
        bump()
    }

    // The first picture: pick up where the person was, and apply the account's audio / subtitle language preferences once tracks are known.
    DisposableEffect(mediaPlayer) {
        mediaPlayer.setEventListener(object : MediaPlayer.EventListener {
            override fun onEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true
                    started = true
                    buffering = false
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
                    if (next != null && preferences.autoplayNextEpisode) upNext = next
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

    fun loadMedia() {
        // Settings > Audio. Passthrough sends Dolby / DTS to the TV untouched (VLC only does it when the device says it can). Stereo uses VLC's
        // OpenSL ES output, which plays two channels, so surround is mixed down; VLC cannot cap at 5.1 or 7.1, those play at the file's layout.
        val stereoOnly = audioMode == AudioChannelMode.STEREO
        if (stereoOnly) mediaPlayer.setAudioOutput("opensles_android")
        mediaPlayer.setAudioDigitalOutputEnabled(audioPassthrough && !stereoOnly)
        val media = Media(libVlc, Uri.parse(url))
        // The device's hardware decoder (VLC still falls back to its own software decoder if the hardware one can't take the video).
        media.setHWDecoderEnabled(true, false)
        // Open the file at the spot rather than jumping once it plays: a jump mid-stream can land between key frames and show a broken picture.
        if (startPositionMs > 5_000) media.addOption(":start-time=${startPositionMs / 1000.0}")
        mediaPlayer.setMedia(media)
        media.release()
        mediaPlayer.play()
    }

    LaunchedEffect(viewReady) {
        if (viewReady) loadMedia()
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

    // The one real seek, SEEK_APPLY_DELAY_MS after the last press (the effect restarts on every new target).
    LaunchedEffect(scrubTarget) {
        val target = scrubTarget ?: return@LaunchedEffect
        delay(SEEK_APPLY_DELAY_MS)
        mediaPlayer.setTime(target)
        scrubTarget = null
        scrubOrigin = null
    }

    LaunchedEffect(seekText) {
        if (seekText != null) {
            delay(900)
            seekText = null
        }
    }

    // Next episode: a few seconds after the button appears (last minute, controls hidden) the cursor lands on it, so one OK plays the next
    // episode. The short wait stops an OK pressed for something else from skipping the episode. LEFT / RIGHT still seek, UP or BACK dismisses it.
    fun offerShowing() = offerNext && next != null && upNext == null && !offerDismissed && !controlsVisible && menu == null && !showChoice && failure == null
    LaunchedEffect(offerNext, offerDismissed, controlsVisible, upNext, menu, showChoice, failure) {
        if (offerShowing()) {
            delay(OFFER_FOCUS_DELAY_MS)
            if (offerShowing()) runCatching { nextOfferFocus.requestFocus() }
        } else {
            offerFocused = false
        }
    }

    // The timeline, and Continue Watching while playing (first after a few seconds, then every 15 s).
    LaunchedEffect(playing) {
        var sinceReport = REPORT_INTERVAL_MS - FIRST_REPORT_MS
        while (true) {
            positionMs = scrubTarget ?: mediaPlayer.time.coerceAtLeast(0)
            if (lengthMs <= 0) lengthMs = mediaPlayer.length.coerceAtLeast(0)
            offerNext = offerNextEpisode(positionMs, lengthMs, next != null)
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
    LaunchedEffect(controlsVisible, interactionTick, menu, showChoice) {
        if (controlsVisible && menu == null && !showChoice) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, menu, showChoice) {
        if (!controlsVisible) {
            // The controls went away (inactivity or BACK): the next time they appear the cursor starts on Play / Pause again.
            lastFocus = null
            if (menu == null && !showChoice) runCatching { rootFocus.requestFocus() }
        } else if (menu == null && !showChoice) {
            // Back from a menu: onto the control that opened it, not Play / Pause.
            val last = lastFocus
            val restored = last != null && runCatching { last.requestFocus() }.isSuccess
            if (!restored) runCatching { focus.play.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val isConfirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (revealingKeyHeld && isConfirm) {
                    if (event.type == KeyEventType.KeyUp) revealingKeyHeld = false
                    return@onPreviewKeyEvent true
                }
                // BACK with the controls showing hides them at once, on the key press itself (not left to the system's back handling, which
                // took a second press); with them hidden BACK goes on to leave the player. Menus, the card and Up next close themselves.
                if (event.key == Key.Back && event.type == KeyEventType.KeyDown && controlsVisible && menu == null && !showChoice && upNext == null && !offerFocused) {
                    controlsVisible = false
                    return@onPreviewKeyEvent true
                }
                // The focused Next episode button: OK plays it, LEFT / RIGHT keep seeking, UP or BACK dismiss it, play / pause still pauses.
                if (offerFocused && upNext == null) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { next?.let { onNextEpisode(it.season, it.episode) }; return@onPreviewKeyEvent true }
                            Key.DirectionLeft -> { seekBy(-SEEK_STEP_MS); return@onPreviewKeyEvent true }
                            Key.DirectionRight -> { seekBy(SEEK_STEP_MS); return@onPreviewKeyEvent true }
                            Key.DirectionUp, Key.Back -> { dismissOffer(); return@onPreviewKeyEvent true }
                            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePlay(); return@onPreviewKeyEvent true }
                            else -> Unit
                        }
                    }
                    return@onPreviewKeyEvent false
                }
                // While the Up next card has focus, it owns the keys.
                if (offerFocused || upNext != null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown || menu != null || showChoice || failure != null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePlay(); true }
                    else -> if (!controlsVisible) {
                        when (event.key) {
                            // OK with the controls hidden only brings them up; pausing is a second press, on the Play / Pause button.
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { controlsVisible = true; revealingKeyHeld = true; true }
                            Key.DirectionLeft -> { seekBy(-SEEK_STEP_MS); true }
                            Key.DirectionRight -> { seekBy(SEEK_STEP_MS); true }
                            Key.DirectionDown -> {
                                // Down reaches the Next episode button while it is offered; otherwise it shows the controls.
                                if (offerNext && next != null) runCatching { nextOfferFocus.requestFocus() } else controlsVisible = true
                                true
                            }
                            Key.DirectionUp -> { controlsVisible = true; true }
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

        if (!started && failure == null) {
            PlayerLoadingScreen(content = content, episode = episode, busy = true)
        }

        if (buffering && started && failure == null) {
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

        seekText?.let { text ->
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        if (controlsVisible && failure == null) {
            val controls = VlcControlsModel(
                content = content,
                episode = episode,
                playing = playing,
                positionMs = positionMs,
                lengthMs = lengthMs,
                speedLabel = SPEEDS[speedIndex].toString().removeSuffix(".0") + "x",
                showAudio = audioTracks.size > 1,
                showSubtitles = subtitleTracks.size > 1,
                hasNextEpisode = next != null,
                onPlayPause = ::togglePlay,
                onSeek = ::seekBy,
                onAudio = { menu = VlcMenu.AUDIO },
                onSubtitles = { menu = VlcMenu.SUBTITLES },
                onSpeed = ::cycleSpeed,
                onNextEpisode = { next?.let { onNextEpisode(it.season, it.episode) } },
                onChoosePlayer = { showChoice = true },
                onChangeSource = onChangeSource,
                onBack = onBack,
                onFocused = { lastFocus = it }
            )
            if (USE_CLASSIC_VLC_CONTROLS) VlcControlsClassic(controls, focus) else VlcControlsNetflix(controls, focus)
        }

        if (offerShowing()) {
            NextEpisodeOffer(
                next = next,
                onGo = { onNextEpisode(next.season, next.episode) },
                focusRequester = nextOfferFocus,
                modifier = Modifier.align(Alignment.BottomEnd).onFocusChanged { offerFocused = it.hasFocus }
            )
        }
        upNext?.let { target ->
            UpNextCard(next = target, onGo = { onNextEpisode(target.season, target.episode) }, onCancel = { upNext = null })
        }

        if (showChoice) {
            PlayerChoiceCard(
                externalAvailable = hasExternalPlayer(context, url),
                initial = PlayerChoiceOption.VLC,
                onPlay = { option ->
                    when (option) {
                        PlayerChoiceOption.BUILT_IN -> {
                            onRememberPlayer(PreferredPlayer.BUILT_IN)
                            showChoice = false
                            onPlayWithBuiltIn(mediaPlayer.time.coerceAtLeast(0))
                        }
                        PlayerChoiceOption.VLC -> {
                            onRememberPlayer(PreferredPlayer.VLC)
                            showChoice = false
                        }
                        PlayerChoiceOption.EXTERNAL -> {
                            mediaPlayer.pause()
                            if (onOpenExternal()) showChoice = false
                        }
                    }
                },
                onCancel = { showChoice = false }
            )
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
            upNext != null -> upNext = null
            offerFocused -> dismissOffer()
            showChoice -> showChoice = false
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

/**
 * The timeline. Merely landing on it does not capture LEFT / RIGHT, so the cursor can move past it to the next button; press OK to
 * start scrubbing (the bar turns accent-coloured and shows a knob), then LEFT / RIGHT seek 10 seconds a press (hold to keep going).
 * OK again, UP, DOWN or moving off it ends scrubbing.
 */
@Composable
internal fun VlcTimeline(fraction: Float, focusRequester: FocusRequester, onSeek: (Long) -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    val barHeight = if (scrubbing) 8.dp else if (focused) 7.dp else 5.dp
    Box(
        modifier = modifier
            .height(24.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused() else scrubbing = false
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { scrubbing = !scrubbing; true }
                    Key.DirectionLeft -> if (scrubbing) { onSeek(-SEEK_STEP_MS); true } else false
                    Key.DirectionRight -> if (scrubbing) { onSeek(SEEK_STEP_MS); true } else false
                    Key.DirectionUp, Key.DirectionDown -> { scrubbing = false; false }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(barHeight).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp)))
        Box(modifier = Modifier.fillMaxWidth(fraction).height(barHeight).background(if (scrubbing || focused) ArcAccent else Color.White, RoundedCornerShape(4.dp)))
        if (scrubbing) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * fraction - 8.dp)
                        .size(16.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}

/**
 * Bottom left of the controls: the title's logo when it has one (its name otherwise), and "S1 E2 • title" for an episode.
 */
@Composable
internal fun VlcTitle(content: Content, episode: Episode?) {
    if (content.logoUrl != null) {
        AsyncImage(
            model = content.logoUrl,
            contentDescription = content.title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.height(56.dp).widthIn(max = 280.dp)
        )
    } else {
        Text(content.title, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
    }
    if (episode != null) {
        Text(
            text = "S${episode.seasonNumber} E${episode.episodeNumber} • ${episode.title}",
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
