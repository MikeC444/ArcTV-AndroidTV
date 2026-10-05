package com.mangotv.app.ui.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Tracks
import com.mangotv.app.data.audio.LocalUiSoundPlayer
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.data.model.Stream
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.player.overlay.AdvancedSettingsPanel
import com.mangotv.app.ui.player.overlay.AudioInfoPanel
import com.mangotv.app.ui.player.overlay.AudioTrackMenu
import com.mangotv.app.ui.player.overlay.PlaybackErrorOverlay
import com.mangotv.app.ui.player.overlay.PlayerChoiceCard
import com.mangotv.app.ui.player.overlay.PlayerChoiceOption
import com.mangotv.app.ui.player.overlay.PlaybackSpeedMenu
import com.mangotv.app.ui.player.overlay.QualityMenu
import com.mangotv.app.ui.player.overlay.SettingsPanel
import com.mangotv.app.ui.player.overlay.SourceInfoPanel
import com.mangotv.app.ui.player.overlay.SubtitlesMenu
import kotlin.math.abs
import org.videolan.libvlc.util.VLCUtil
import kotlinx.coroutines.delay

/** How often a progress report fires while actively playing (Milestone 8) -- frequent enough that another device's Continue Watching stays reasonably current, infrequent enough not to flood the network on every position tick. */
/** The first position is saved this long after playback starts, then every [PROGRESS_REPORT_INTERVAL_MS] (also on pause and on leaving). */
/** Holding LEFT / RIGHT on the timeline moves this far each step, one step every [HOLD_SEEK_STEP_INTERVAL_MS]. */
private const val HOLD_SEEK_STEP_MS = 10_000L
private const val HOLD_SEEK_STEP_INTERVAL_MS = 250L
private const val FIRST_PROGRESS_REPORT_MS = 4_000L
private const val PROGRESS_REPORT_INTERVAL_MS = 15_000L

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onChangeSource: () -> Unit,
    onNextEpisode: (season: Int, episode: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val screenState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackPhase by viewModel.playbackPhase.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val subtitleTracks by viewModel.subtitleTracks.collectAsStateWithLifecycle()
    val qualityOptions by viewModel.qualityOptions.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = screenState) {
            is PlayerScreenUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                    color = Color.White
                )
            }
            is PlayerScreenUiState.Error -> {
                // Reachable now that Detail can jump straight here on Resume,
                // skipping the Sources picker (see DetailScreen's
                // navigateToPlayback) -- if the source it remembered has
                // since gone missing, Retry alone would just fail the exact
                // same way forever, so this also offers a way back to the
                // picker instead of a dead end.
                FullScreenErrorState(
                    message = state.message,
                    onRetry = viewModel::load,
                    secondaryActionLabel = "Choose a Different Source",
                    onSecondaryAction = onChangeSource
                )
            }
            is PlayerScreenUiState.Ready -> {
                val streamUrl = state.stream.url
                val titleKey = viewModel.titleKey
                val displayTitle = listOfNotNull(
                    state.content.title,
                    state.episode?.let { "S${it.seasonNumber}E${it.episodeNumber}" }
                ).joinToString(" ")
                // Which player starts: the one this title remembers, else the default (VLC's engine unless Settings > Player says otherwise).
                // Only for a source with a direct link and a chip LibVLC runs on; everything else uses the built-in player.
                val vlcUsable = remember { VLCUtil.hasCompatibleCPU(context) }
                val resumeAtStart = remember(state.stream.id) { viewModel.resumePositionMs() }
                val startsInVlc = remember(state.stream.id) {
                    streamUrl != null && vlcUsable && DevicePlayerPrefs.playerFor(context, titleKey) == PreferredPlayer.VLC
                }
                // The position VLC starts from (null while the built-in player is the one playing), and where the built-in player starts.
                var vlcStart by remember(state.stream.id) { mutableStateOf(if (startsInVlc) (resumeAtStart ?: 0L) else null) }
                var builtInStart by remember(state.stream.id) { mutableStateOf(resumeAtStart) }
                val rememberPlayer: (PreferredPlayer) -> Unit = { player -> DevicePlayerPrefs.setTitlePlayer(context, titleKey, player) }
                val currentVlcStart = vlcStart
                if (currentVlcStart != null && streamUrl != null) {
                    VlcPlaybackContent(
                        title = displayTitle,
                        url = streamUrl,
                        startPositionMs = currentVlcStart,
                        preferences = preferences,
                        onReportProgress = viewModel::reportProgress,
                        onPlayWithBuiltIn = { positionMs ->
                            builtInStart = positionMs.takeIf { it > 0 } ?: builtInStart
                            vlcStart = null
                        },
                        onOpenExternal = {
                            val opened = openInExternalPlayer(context, streamUrl, displayTitle)
                            viewModel.recordExternalPlayer(false, opened, null, "external")
                            opened
                        },
                        onRememberPlayer = rememberPlayer,
                        onChangeSource = onChangeSource,
                        onBack = onBack
                    )
                } else PlaybackContent(
                    content = state.content,
                    episode = state.episode,
                    stream = state.stream,
                    resumePositionMs = builtInStart,
                    phase = playbackPhase,
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                    qualityOptions = qualityOptions,
                    preferences = preferences,
                    onPhaseChanged = viewModel::onPlaybackPhaseChanged,
                    onTracksChanged = viewModel::onTracksChanged,
                    onAutoplayChange = viewModel::setAutoplayNextEpisode,
                    onSkipIntroChange = viewModel::setSkipIntroEnabled,
                    onReportProgress = viewModel::reportProgress,
                    onExternalPlayerChosen = viewModel::recordExternalPlayer,
                    onBack = onBack,
                    onChangeSource = onChangeSource,
                    onNextEpisode = onNextEpisode,
                    onUseVlcEngine = { positionMs -> vlcStart = positionMs },
                    vlcAvailable = vlcUsable,
                    onRememberPlayer = rememberPlayer
                )
            }
        }
    }
}

@Composable
private fun PlaybackContent(
    content: Content,
    episode: Episode?,
    stream: Stream,
    resumePositionMs: Long?,
    phase: PlaybackPhase,
    audioTracks: List<AudioTrackOption>,
    subtitleTracks: List<SubtitleTrackOption>,
    qualityOptions: List<QualityOption>,
    preferences: PlayerPreferences,
    onPhaseChanged: (PlaybackPhase) -> Unit,
    onTracksChanged: (Tracks) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onSkipIntroChange: (Boolean) -> Unit,
    onReportProgress: (positionMs: Long, durationMs: Long, completed: Boolean) -> Unit,
    onExternalPlayerChosen: (fromError: Boolean, opened: Boolean, errorMessage: String?, engine: String) -> Unit,
    onBack: () -> Unit,
    onChangeSource: () -> Unit,
    onNextEpisode: (season: Int, episode: Int) -> Unit,
    onUseVlcEngine: (positionMs: Long) -> Unit,
    vlcAvailable: Boolean,
    onRememberPlayer: (PreferredPlayer) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Captured once at first composition, same as exoPlayer itself below --
    // a later change to Subtitles settings only takes effect on the next
    // playback session (leaving/re-entering the player), not live mid-session.
    val audioOutput = remember { AudioOutputSettings(DevicePlayerPrefs.audioPassthrough(context), DevicePlayerPrefs.audioChannelMode(context)) }
    // Always on to begin with; turned off for this playback only by the automatic retry in PlayerListenerBridge when a Dolby Vision file fails.
    val dolbyVisionSwitch = remember { DolbyVisionSwitch(true) }
    val exoPlayer = remember { buildExoPlayer(context, preferences, audioOutput, dolbyVisionSwitch) }
    val uiSoundPlayer = LocalUiSoundPlayer.current

    // Opening: the loading screen stays until the first picture plays (and behind the resume question).
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(phase) { if (phase is PlaybackPhase.Playing) started = true }
    var showRemaining by remember { mutableStateOf(DevicePlayerPrefs.showRemaining(context)) }
    var audioPassthrough by remember { mutableStateOf(DevicePlayerPrefs.audioPassthrough(context)) }
    // The episode after this one, offered in the last minute and counted down to after the end (when Auto Play Next Episode is on).
    val next = remember(content, episode) { nextEpisodeAfter(content.seasons, episode?.seasonNumber, episode?.episodeNumber) }
    var upNext by remember { mutableStateOf<NextEpisode?>(null) }
    var offerNext by remember { mutableStateOf(false) }
    val nextOfferFocusRequester = remember { FocusRequester() }

    // Without this, Fire TV's system screensaver/idle timeout kicks in
    // during playback the same as it would over any other idle screen --
    // it has no way to know a video is actively playing here (this player
    // is a plain Compose UI over ExoPlayer, not something driving a system
    // media session it could key off of). Scoped to exactly this
    // composable's lifetime (mounted for as long as there's real playback
    // content on screen, cleared on dispose below whenever PlayerScreen
    // leaves composition) rather than to play/pause state -- a paused
    // player (e.g. while a settings panel is open) shouldn't let the
    // screensaver interrupt the session either.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(exoPlayer) {
        val listener = PlayerListenerBridge(onPhaseChanged, onTracksChanged, exoPlayer, dolbyVisionSwitch)
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            // Final "stopped playback" progress report -- read before
            // release(), since currentPosition/duration are no longer
            // meaningful afterward. Not a coroutine context, which is
            // exactly why onReportProgress (-> PlayerViewModel.reportProgress
            // -> ContinueWatchingSyncRepository.reportProgress) is a plain,
            // non-suspend call all the way down.
            if (exoPlayer.duration > 0) {
                onReportProgress(exoPlayer.currentPosition, exoPlayer.duration, false)
            }
            exoPlayer.release()
        }
    }

    // Periodic-while-playing, plus one-shot reports on pause and on
    // natural completion -- the "sensible update strategy" Milestone 8
    // calls for, instead of a report per position tick. Restarts (and so
    // stops the periodic loop) every time phase changes, since phase is a
    // plain parameter value re-passed down on each recomposition.
    LaunchedEffect(phase) {
        when (phase) {
            is PlaybackPhase.Playing -> {
                delay(FIRST_PROGRESS_REPORT_MS)
                while (true) {
                    onReportProgress(exoPlayer.currentPosition, exoPlayer.duration, false)
                    delay(PROGRESS_REPORT_INTERVAL_MS)
                }
            }
            is PlaybackPhase.Paused -> onReportProgress(exoPlayer.currentPosition, exoPlayer.duration, false)
            is PlaybackPhase.Ended -> onReportProgress(exoPlayer.duration, exoPlayer.duration, true)
            else -> Unit
        }
    }

    // Pause (not release) when the Activity stops — simpler and faster to
    // resume than a full release/reprepare; the DisposableEffect above
    // still handles a genuine release when this screen leaves composition.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startPlayback() {
        val mediaItem = stream.toMediaItemOrNull()
        if (mediaItem == null) {
            onPhaseChanged(
                PlaybackPhase.Error(
                    PlaybackErrorType.TORRENT_UNSUPPORTED,
                    "This source requires torrent streaming, which isn't supported yet. Try a different source."
                )
            )
        } else {
            if (resumePositionMs != null && resumePositionMs > 0) {
                exoPlayer.setMediaItem(mediaItem, resumePositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } else {
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    LaunchedEffect(stream.id) {
        startPlayback()
        // A part-watched title carries on from where it was left; a spot at the very end starts over.
        val resume = resumePositionMs
        if (resume != null && resume > 0 && exoPlayer.mediaItemCount > 0) {
            var waitedMs = 0L
            while (exoPlayer.duration <= 0 && waitedMs < 20_000L) {
                delay(100)
                waitedMs += 100
            }
            if (exoPlayer.duration > 0 && !shouldOfferResume(resume, exoPlayer.duration)) exoPlayer.seekTo(0)
        }
    }

    // Next episode: offered for the last minute, and after the end counted down to (Auto Play Next Episode on).
    LaunchedEffect(phase, next) {
        while (true) {
            offerNext = offerNextEpisode(exoPlayer.currentPosition, exoPlayer.duration, next != null)
            delay(1_000L)
        }
    }
    LaunchedEffect(phase) {
        if (phase is PlaybackPhase.Ended && next != null && preferences.autoplayNextEpisode) upNext = next
    }
    fun goToNextEpisode(target: NextEpisode) = onNextEpisode(target.season, target.episode)

    // -- Controls visibility, focus zone, and the interaction-resets-the-
    // -- auto-hide-timer bookkeeping.
    var controlsVisible by remember { mutableStateOf(false) }
    // True for the remainder of a single physical confirm-key press that
    // revealed hidden controls (see the onPreviewKeyEvent handler below) --
    // covers the window between that KeyDown and its eventual KeyUp, during
    // which focus moves onto the newly-revealed Play/Pause button. Without
    // this, a press held just long enough for Android's own key-repeat to
    // fire after that focus move would leak a repeat KeyDown straight to
    // the button (which sees it as a fresh press, having never seen the
    // original KeyDown this screen's root already consumed), and the
    // eventual release would then land on it too and fire its own onClick
    // -- toggling playback right after a press whose whole intent was only
    // to reveal the controls. Same underlying hazard as TvFocusSurface's
    // own onLongClick doc describes: Compose routes key events by current
    // focus, not by which node originally claimed the press.
    var revealingKeyHeld by remember { mutableStateOf(false) }
    var focusZone by remember { mutableStateOf(PlayerFocusZone.NONE) }
    var interactionTick by remember { mutableIntStateOf(0) }
    fun bumpInteraction() { interactionTick++ }
    fun onFocusZoneChanged(zone: PlayerFocusZone) {
        focusZone = zone
        bumpInteraction()
    }

    // Merely focusing the timeline no longer captures LEFT/RIGHT — the user
    // has to actively select it (DPAD_CENTER/Enter) to start scrubbing, or
    // LEFT/RIGHT would fall through to normal focus traversal and never let
    // the user move focus past the timeline to reach the icon row. Reset
    // whenever focus leaves the timeline by any path (UP, or controls
    // hiding -- which is also how a single BACK press clears this, see
    // BackHandler's own comment below), so a stale "still scrubbing" state
    // can never survive a zone change.
    var timelineScrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(focusZone) {
        if (focusZone != PlayerFocusZone.TIMELINE) timelineScrubbing = false
    }

    // A stack, not a single nullable value: Subtitles/Audio/Quality/Source
    // Info can be reached either directly from their own bottom-row icon
    // (dismissing straight back to plain controls) or via Settings
    // (dismissing back to Settings instead) — popping one level handles
    // both without hardcoding where each menu "returns to".
    var overlayStack by remember { mutableStateOf<List<PlayerOverlay>>(emptyList()) }
    val activeOverlay = overlayStack.lastOrNull()
    fun pushOverlay(overlay: PlayerOverlay) {
        overlayStack = overlayStack + overlay
        bumpInteraction()
    }
    fun popOverlay() {
        overlayStack = overlayStack.dropLast(1)
        bumpInteraction()
    }

    // The last speed chosen is kept for every title on this device.
    var playbackSpeed by remember { mutableFloatStateOf(DevicePlayerPrefs.speed(context)) }
    fun changePlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        DevicePlayerPrefs.setSpeed(context, speed)
        bumpInteraction()
    }
    LaunchedEffect(stream.id) { exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed) }

    // Only worth a menu when there's a real choice — matches the spec's
    // "don't show a fake quality/options menu" instruction. Subtitles
    // always has a synthetic "Off" entry (see toSubtitleTrackOptions), so
    // size > 1 means at least one real track exists; audio can't be "off"
    // so size > 1 means more than the single track already playing.
    val showSubtitles = subtitleTracks.size > 1
    val showAudio = audioTracks.size > 1
    val showQuality = qualityOptions.count { it.trackGroup != null } > 1
    val subtitleLabel = subtitleTracks.firstOrNull { it.isSelected }?.label ?: "Off"
    val audioLabel = audioTracks.firstOrNull { it.isSelected }?.label
        ?: if (audioTracks.isEmpty()) "Can't be changed for this source here" else "Auto"
    val qualityLabel = qualityOptions.firstOrNull { it.isSelected }?.label ?: "Auto"

    val rootFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val choosePlayerFocusRequester = remember { FocusRequester() }
    val errorExternalFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }
    // The control the cursor was last on: after a menu closes, or the controls hide and come back, it returns there instead of to Play / Pause.
    var lastControlFocus by remember { mutableStateOf<FocusRequester?>(null) }
    fun focusLastControl() {
        val last = lastControlFocus
        // requestFocus() throws when that control isn't on screen any more (e.g. the Next episode button), so success means it worked.
        val restored = last != null && runCatching { last.requestFocus() }.isSuccess
        if (!restored) runCatching { playPauseFocusRequester.requestFocus() }
    }

    // "Choose player": the built-in player, VLC's engine inside Arc TV, or another app on the device. Only for a source with a direct link, and
    // nothing happens until the person picks one and presses Play. Where it was asked from is kept for the usage report (the error screen's
    // button points at the built-in player, not taste).
    val canChoosePlayer = stream.url != null
    var choiceFromError by remember { mutableStateOf(false) }
    fun askPlayerChoice(fromError: Boolean) {
        choiceFromError = fromError
        // No player app on this device: the card leaves that row out, and that is worth knowing about too.
        val url = stream.url
        if (url != null && !hasExternalPlayer(context, url)) {
            onExternalPlayerChosen(fromError, false, (phase as? PlaybackPhase.Error)?.message.takeIf { fromError }, "external")
        }
        pushOverlay(PlayerOverlay.PLAYER_CHOICE)
    }
    fun playWith(option: PlayerChoiceOption) {
        val url = stream.url ?: return
        val message = (phase as? PlaybackPhase.Error)?.message.takeIf { choiceFromError }
        when (option) {
            // The normal player: from the error screen that means trying again.
            PlayerChoiceOption.BUILT_IN -> {
                onRememberPlayer(PreferredPlayer.BUILT_IN)
                popOverlay()
                if (choiceFromError) startPlayback() else focusLastControl()
            }
            // VLC's engine takes over from where playback was (or the saved resume point).
            PlayerChoiceOption.VLC -> {
                onExternalPlayerChosen(choiceFromError, true, message, "vlc")
                onRememberPlayer(PreferredPlayer.VLC)
                popOverlay()
                onUseVlcEngine(exoPlayer.currentPosition.takeIf { it > 0 } ?: resumePositionMs ?: 0L)
            }
            PlayerChoiceOption.EXTERNAL -> {
                val title = listOfNotNull(content.title, episode?.let { "S${it.seasonNumber}E${it.episodeNumber}" }).joinToString(" ")
                exoPlayer.pause()
                val opened = openInExternalPlayer(context, url, title)
                onExternalPlayerChosen(choiceFromError, opened, message, "external")
                if (opened) popOverlay()
            }
        }
    }

    // Moves focus in both directions: onto play/pause when controls appear
    // (or they'd stay stuck on the invisible root anchor and be visible but
    // unreachable), and back onto the root anchor when controls hide again
    // (their buttons leave composition, so focus would otherwise be lost
    // entirely and stop receiving key events at all). Also covers the very
    // first focus request on initial composition, since controlsVisible
    // starts false.
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            focusLastControl()
        } else {
            focusZone = PlayerFocusZone.NONE
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Auto-hide after a few seconds of inactivity — re-armed by any bumped
    // interaction (seeking, toggling play/pause, moving focus) so it never
    // fires while the user is actively using the controls, and suspended
    // entirely while a menu is open (it shouldn't vanish while the user is
    // reading options).
    LaunchedEffect(controlsVisible, interactionTick, activeOverlay) {
        if (controlsVisible && activeOverlay == null) {
            delay(4000)
            controlsVisible = false
        }
    }

    var seekIndicatorText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekIndicatorText) {
        if (seekIndicatorText != null) {
            delay(900)
            seekIndicatorText = null
        }
    }

    var playPauseFlashVisible by remember { mutableStateOf(false) }
    var playPauseFlashIsPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(playPauseFlashVisible) {
        if (playPauseFlashVisible) {
            delay(700)
            playPauseFlashVisible = false
        }
    }

    fun togglePlayPause() {
        val willPlay = phase !is PlaybackPhase.Playing
        if (willPlay) exoPlayer.play() else exoPlayer.pause()
        playPauseFlashIsPlaying = willPlay
        playPauseFlashVisible = true
        bumpInteraction()
    }

    fun seekPillText(deltaMs: Long): String {
        val seconds = abs(deltaMs) / 1000
        val amount = if (seconds >= 60) "${seconds / 60} min ${seconds % 60} s" else "$seconds seconds"
        return if (deltaMs < 0) "«« $amount" else "$amount »»"
    }

    fun clampedSeekTarget(targetMs: Long): Long {
        val duration = exoPlayer.duration
        return if (duration > 0) targetMs.coerceIn(0, duration) else targetMs.coerceAtLeast(0)
    }

    // Explicit rewind/forward button clicks — a flat, immediate 10s seek,
    // distinct from the accelerating hold gesture below (which is driven by
    // held D-pad LEFT/RIGHT, not a click).
    fun seekByClick(deltaMs: Long) {
        exoPlayer.seekTo(clampedSeekTarget(exoPlayer.currentPosition + deltaMs))
        seekIndicatorText = seekPillText(deltaMs)
        bumpInteraction()
    }

    // Preview-then-commit-on-release hold-to-seek: KeyDown repeats only
    // grow the pending delta and update the pill (no seekTo() per tick, to
    // avoid stutter from repeated flush/rebuffer); KeyUp commits once. This
    // also means a single tap (KeyDown then immediate KeyUp) still performs
    // a normal flat 10s seek, since pendingSeekDeltaMs starts at ±10s.
    var seekAnchorMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekDeltaMs by remember { mutableStateOf(0L) }

    // Holding LEFT / RIGHT on the timeline scrubs at a steady pace, one 10-second step every [HOLD_SEEK_STEP_INTERVAL_MS], for as long as
    // the key is held (no top speed and no 2-minute limit, just the ends of the video); the jump is made when the key is released.
    var lastHoldStepAtMs by remember { mutableStateOf(0L) }

    fun beginOrContinueHoldSeek(direction: Int, isFreshPress: Boolean) {
        val now = SystemClock.uptimeMillis()
        val anchor = seekAnchorMs
        if (isFreshPress || anchor == null) {
            seekAnchorMs = exoPlayer.currentPosition
            pendingSeekDeltaMs = 10_000L * direction
            lastHoldStepAtMs = now
        } else if (now - lastHoldStepAtMs >= HOLD_SEEK_STEP_INTERVAL_MS) {
            lastHoldStepAtMs = now
            val target = clampedSeekTarget(anchor + pendingSeekDeltaMs + HOLD_SEEK_STEP_MS * direction)
            pendingSeekDeltaMs = target - anchor
        }
        seekIndicatorText = seekPillText(pendingSeekDeltaMs)
        bumpInteraction()
    }

    fun commitHoldSeek() {
        val anchor = seekAnchorMs ?: return
        exoPlayer.seekTo(clampedSeekTarget(anchor + pendingSeekDeltaMs))
        seekAnchorMs = null
        pendingSeekDeltaMs = 0L
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // See revealingKeyHeld's own declaration for why this has to
                // come first and unconditionally own the rest of the
                // physical press it started, however focus has since moved.
                if (revealingKeyHeld) {
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        if (event.type == KeyEventType.KeyUp) revealingKeyHeld = false
                        return@onPreviewKeyEvent true
                    }
                }
                // The Fire TV remote's dedicated Play/Pause hardware button
                // is a transport control, not a D-pad direction — it should
                // always work (menu open or not, controls hidden or not),
                // so it's handled before anything else below gets a say.
                if (event.key == Key.MediaPlayPause || event.key == Key.MediaPlay || event.key == Key.MediaPause) {
                    if (event.type == KeyEventType.KeyDown) {
                        val isPlaying = phase is PlaybackPhase.Playing
                        val shouldToggle = event.key == Key.MediaPlayPause ||
                            (event.key == Key.MediaPlay && !isPlaying) ||
                            (event.key == Key.MediaPause && isPlaying)
                        if (shouldToggle) togglePlayPause()
                    }
                    return@onPreviewKeyEvent true
                }
                // Down reaches the "Next episode" button while the controls are hidden.
                if (offerNext && next != null && upNext == null && !controlsVisible && activeOverlay == null &&
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                ) {
                    runCatching { nextOfferFocusRequester.requestFocus() }
                    return@onPreviewKeyEvent true
                }
                // While a menu is open, it owns all key handling itself
                // (its own list navigation/selection) — don't fight it with
                // the player's own global seek/reveal shortcuts.
                if (activeOverlay != null) return@onPreviewKeyEvent false
                // Any D-pad direction, or a confirm press, wakes the controls
                // when they're hidden -- previously only DPAD_CENTER/Enter
                // did, so pressing e.g. UP or LEFT while watching silently
                // did nothing instead of bringing up the timeline/transport
                // row the way every other TV player does. Deliberately just
                // reveals on this first press rather than also performing
                // that key's normal action (a seek, a focus move, a
                // play/pause toggle) -- nothing below (seekEligible in
                // particular) can act on it yet anyway, since every control
                // is still off screen and unfocusable at this point. This
                // check is deliberately the ONLY thing gating a confirm
                // press while hidden -- it doesn't look at focusZone the way
                // the Key.DirectionCenter/Enter case further down does for
                // the controls-already-visible case, so a confirm press here
                // can never fall through to that later logic and act on
                // whatever zone happened to be focused before controls last
                // hid (which was the actual cause of a confirm press
                // instantly toggling play/pause instead of just revealing
                // controls the first time).
                if (!controlsVisible &&
                    event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                        event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    controlsVisible = true
                    // Only a confirm key needs the rest of its own press
                    // tracked -- see revealingKeyHeld's own doc. A plain
                    // direction key has no "click" semantics on release for
                    // any control to misfire, so there's nothing to guard.
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        revealingKeyHeld = true
                    }
                    return@onPreviewKeyEvent true
                }
                // LEFT/RIGHT only seeks while the timeline itself has focus
                // AND the user has actively selected it (DPAD_CENTER/Enter,
                // toggled below) — deliberately not just "has focus", so
                // LEFT/RIGHT can still move focus past the timeline onto the
                // next button instead of getting stuck seeking the instant
                // it's merely highlighted. Rewind10/Forward10 remain
                // reachable as explicit buttons (a click, not a D-pad
                // direction) regardless of scrub state.
                val seekEligible = focusZone == PlayerFocusZone.TIMELINE && timelineScrubbing
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (!seekEligible) return@onPreviewKeyEvent false
                        val direction = if (event.key == Key.DirectionLeft) -1 else 1
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                beginOrContinueHoldSeek(direction, isFreshPress = event.nativeKeyEvent.repeatCount == 0)
                                true
                            }
                            KeyEventType.KeyUp -> {
                                commitHoldSeek()
                                true
                            }
                            else -> false
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when {
                            event.type != KeyEventType.KeyDown -> false
                            // Selecting the timeline toggles scrub mode
                            // in-place, rather than firing a click (it has
                            // no onClick — TvFocusSurface isn't used here
                            // since LEFT/RIGHT, not a click, is what drives
                            // seeking once selected). The controls-hidden
                            // case is already handled above, before this
                            // whole `when` block, so focusZone here is only
                            // ever read while controls are visible -- every
                            // other zone (transport, icon row, top bar) is
                            // deliberately left unconsumed (false) so the
                            // actually-focused button's own click handling
                            // gets the event instead of this intercepting it.
                            focusZone == PlayerFocusZone.TIMELINE -> {
                                timelineScrubbing = !timelineScrubbing
                                bumpInteraction()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
    ) {
        PlayerSurface(exoPlayer = exoPlayer, modifier = Modifier.fillMaxSize())

        if (!started && phase !is PlaybackPhase.Error) {
            PlayerLoadingScreen(content = content, episode = episode, busy = true)
        }

        if (phase is PlaybackPhase.Buffering && started) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White
            )
        }

        if (phase is PlaybackPhase.Error) {
            PlaybackErrorOverlay(
                message = phase.message,
                onTryAgain = ::startPlayback,
                onChangeSource = onChangeSource,
                onChoosePlayer = if (canChoosePlayer) ({ askPlayerChoice(fromError = true) }) else null,
                choosePlayerFocusRequester = errorExternalFocusRequester,
                onBack = onBack
            )
        }

        seekIndicatorText?.let { text ->
            SeekIndicatorPill(text = text, modifier = Modifier.align(Alignment.Center))
        }

        if (playPauseFlashVisible) {
            PlayPauseIndicator(isPlaying = playPauseFlashIsPlaying, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlayerControlsScrim(modifier = Modifier.fillMaxSize())

                Column(modifier = Modifier.fillMaxSize()) {
                    PlayerTopBar(
                        content = content,
                        episode = episode,
                        onBack = onBack,
                        backFocusRequester = backFocusRequester,
                        backFocusDown = playPauseFocusRequester,
                        onBackFocusChanged = { focused ->
                            if (focused) {
                                onFocusZoneChanged(PlayerFocusZone.TOP_BAR)
                                lastControlFocus = backFocusRequester
                            }
                        }
                    )

                    Spacer(Modifier.weight(1f))

                    PlayerBottomControls(
                        exoPlayer = exoPlayer,
                        phase = phase,
                        showNextEpisode = next != null,
                        showRemaining = showRemaining,
                        onToggleRemaining = {
                            showRemaining = !showRemaining
                            DevicePlayerPrefs.setShowRemaining(context, showRemaining)
                            bumpInteraction()
                        },
                        showSubtitles = showSubtitles,
                        showAudio = showAudio,
                        showQuality = showQuality,
                        showChoosePlayer = canChoosePlayer,
                        onPlayPause = ::togglePlayPause,
                        onSeek = ::seekByClick,
                        onSubtitles = { pushOverlay(PlayerOverlay.SUBTITLES) },
                        onAudio = { pushOverlay(PlayerOverlay.AUDIO) },
                        onQuality = { pushOverlay(PlayerOverlay.QUALITY) },
                        onChoosePlayer = { askPlayerChoice(fromError = false) },
                        onSettings = { pushOverlay(PlayerOverlay.SETTINGS) },
                        onNextEpisode = { next?.let { goToNextEpisode(it) } },
                        onFocusZoneChanged = ::onFocusZoneChanged,
                        isTimelineScrubbing = timelineScrubbing,
                        playPauseFocusRequester = playPauseFocusRequester,
                        rewindFocusRequester = rewindFocusRequester,
                        forwardFocusRequester = forwardFocusRequester,
                        subtitleFocusRequester = subtitleFocusRequester,
                        audioFocusRequester = audioFocusRequester,
                        qualityFocusRequester = qualityFocusRequester,
                        choosePlayerFocusRequester = choosePlayerFocusRequester,
                        settingsFocusRequester = settingsFocusRequester,
                        nextEpisodeFocusRequester = nextEpisodeFocusRequester,
                        timelineFocusRequester = timelineFocusRequester,
                        onControlFocused = { lastControlFocus = it }
                    )
                }
            }
        }

        if (offerNext && next != null && upNext == null && activeOverlay == null && phase !is PlaybackPhase.Error) {
            NextEpisodeOffer(
                next = next,
                onGo = { goToNextEpisode(next) },
                focusRequester = nextOfferFocusRequester,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        upNext?.let { target ->
            UpNextCard(next = target, onGo = { goToNextEpisode(target) }, onCancel = { upNext = null })
        }

        when (activeOverlay) {
            PlayerOverlay.SUBTITLES -> SubtitlesMenu(
                options = subtitleTracks,
                onSelect = { option -> exoPlayer.selectSubtitleTrack(option); popOverlay() }
            )
            PlayerOverlay.AUDIO -> AudioTrackMenu(
                options = audioTracks,
                onSelect = { option -> exoPlayer.selectAudioTrack(option); popOverlay() }
            )
            PlayerOverlay.QUALITY -> QualityMenu(
                options = qualityOptions,
                onSelect = { option -> exoPlayer.selectQuality(option); popOverlay() }
            )
            PlayerOverlay.SETTINGS -> SettingsPanel(
                playbackSpeed = playbackSpeed,
                preferences = preferences,
                onAutoplayChange = onAutoplayChange,
                showSubtitles = showSubtitles,
                showAudio = showAudio,
                showQuality = showQuality,
                subtitleLabel = subtitleLabel,
                audioLabel = audioLabel,
                qualityLabel = qualityLabel,
                onOpenSubtitles = { pushOverlay(PlayerOverlay.SUBTITLES) },
                onOpenAudio = { pushOverlay(PlayerOverlay.AUDIO) },
                onOpenAudioInfo = { pushOverlay(PlayerOverlay.AUDIO_INFO) },
                onOpenQuality = { pushOverlay(PlayerOverlay.QUALITY) },
                onOpenPlaybackSpeed = { pushOverlay(PlayerOverlay.PLAYBACK_SPEED) },
                onOpenAdvanced = { pushOverlay(PlayerOverlay.ADVANCED) }
            )
            PlayerOverlay.AUDIO_INFO -> AudioInfoPanel(trackLabel = audioTracks.firstOrNull { it.isSelected }?.label)
            PlayerOverlay.PLAYBACK_SPEED -> PlaybackSpeedMenu(
                playbackSpeed = playbackSpeed,
                onSelect = { speed -> changePlaybackSpeed(speed); popOverlay() }
            )
            PlayerOverlay.ADVANCED -> AdvancedSettingsPanel(
                skipIntroEnabled = preferences.skipIntroEnabled,
                onSkipIntroChange = onSkipIntroChange,
                audioPassthrough = audioPassthrough,
                onAudioPassthroughChange = { enabled ->
                    audioPassthrough = enabled
                    DevicePlayerPrefs.setAudioPassthrough(context, enabled)
                    audioOutput.passthrough = enabled
                    // The audio output is chosen when playback is prepared, so prepare again from the same spot (a brief rebuffer).
                    exoPlayer.currentMediaItem?.let { item ->
                        val position = exoPlayer.currentPosition
                        val play = exoPlayer.playWhenReady
                        exoPlayer.setMediaItem(item, position)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = play
                    }
                },
                onOpenSourceInfo = { pushOverlay(PlayerOverlay.SOURCE_INFO) },
                onChangeSource = onChangeSource
            )
            PlayerOverlay.SOURCE_INFO -> SourceInfoPanel(
                stream = stream,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks
            )
            PlayerOverlay.PLAYER_CHOICE -> PlayerChoiceCard(
                externalAvailable = stream.url?.let { hasExternalPlayer(context, it) } == true,
                vlcAvailable = vlcAvailable,
                initial = if (choiceFromError) PlayerChoiceOption.VLC else PlayerChoiceOption.BUILT_IN,
                onPlay = ::playWith,
                onCancel = {
                    popOverlay()
                    // Back onto whatever asked: the error screen's button, else the control last used.
                    if (overlayStack.isEmpty()) {
                        if (choiceFromError) runCatching { errorExternalFocusRequester.requestFocus() } else focusLastControl()
                    }
                }
            )
            null -> Unit
        }
    }

    BackHandler {
        uiSoundPlayer?.playBack()
        when {
            // No separate "just exit scrub mode" branch here on purpose --
            // timelineScrubbing can only ever be true while controlsVisible
            // already is (scrubbing requires the timeline to be focused,
            // which requires controls to be showing), so a dedicated branch
            // ahead of controlsVisible below meant a single BACK press while
            // scrubbing silently exited scrub mode with no visible change,
            // and hiding the controls needed a second press. Falling
            // straight into controlsVisible = false instead hides
            // everything in one press, and still clears scrubbing as a
            // side effect (controlsVisible's own LaunchedEffect resets
            // focusZone to NONE, which timelineScrubbing's LaunchedEffect
            // above reacts to).
            overlayStack.isNotEmpty() -> {
                popOverlay()
                // Only once the whole stack is closed (not a single level of
                // a nested menu, e.g. Advanced -> Settings) — landing back
                // on a known, predictable button rather than wherever focus
                // happens to end up once the overlay leaves composition.
                // Settings/Playback Speed/Advanced back to each other
                // re-focus their own first row on remount instead.
                if (overlayStack.isEmpty()) {
                    focusLastControl()
                }
            }
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }
}
