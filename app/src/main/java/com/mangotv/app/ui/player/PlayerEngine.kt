package com.mangotv.app.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mangotv.app.data.model.PlayerPreferences
import okhttp3.OkHttpClient

/**
 * Builds the ExoPlayer instance for one playback session. Uses OkHttp (via
 * media3-datasource-okhttp) instead of Media3's default HTTP stack purely
 * for consistency with the rest of the app's networking, which is all
 * OkHttp-based (see StremioAddonClient).
 *
 * [preferences] seeds the session's starting subtitle state: disabled
 * entirely when subtitlesEnabled is false (the same TRACK_TYPE_TEXT-disable
 * mechanism TrackOptions.selectSubtitleTrack's synthetic "Off" option
 * uses), otherwise left enabled with defaultSubtitleLanguage (if set) as a
 * preferred-language hint for whichever embedded text tracks this stream
 * turns out to have. Either way this is just the session's starting point
 * -- the in-player Subtitles menu (SubtitlesMenu/selectSubtitleTrack) can
 * still override it once tracks are known, exactly like it always could.
 */
@OptIn(UnstableApi::class)
fun buildExoPlayer(context: Context, preferences: PlayerPreferences): ExoPlayer {
    val httpDataSourceFactory = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    // Decoder fallback: when the first-choice hardware decoder for a track can't start (some Fire TV audio decoders
    // accept a format on paper, e.g. AAC "Main" profile, then fail when asked to play it), try the next decoder the
    // device offers -- usually the software one -- instead of giving up with "Unable to play this source".
    val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    val player = ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()

    var parametersBuilder = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !preferences.subtitlesEnabled)
    if (preferences.subtitlesEnabled && preferences.defaultSubtitleLanguage != null) {
        parametersBuilder = parametersBuilder.setPreferredTextLanguage(preferences.defaultSubtitleLanguage)
    }
    player.trackSelectionParameters = parametersBuilder.build()

    return player
}

/**
 * Translates raw ExoPlayer callbacks into this app's own [PlaybackPhase]
 * model, plus [onTracksChanged] so the ViewModel can derive the audio/
 * subtitle/quality option lists shown in Phase 3's menus without ever
 * holding a live player reference itself.
 */
class PlayerListenerBridge(
    private val onPhaseChanged: (PlaybackPhase) -> Unit,
    private val onTracksChangedCallback: (Tracks) -> Unit = {},
    private val player: Player? = null
) : Player.Listener {

    // Audio tracks that already failed to decode in this session, so the automatic switch below can't loop.
    private val failedAudioGroups = mutableSetOf<TrackGroup>()

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> onPhaseChanged(PlaybackPhase.Buffering)
            Player.STATE_ENDED -> onPhaseChanged(PlaybackPhase.Ended)
            else -> Unit // STATE_READY/STATE_IDLE handled via onIsPlayingChanged/onPlayWhenReadyChanged below
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) onPhaseChanged(PlaybackPhase.Playing)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) onPhaseChanged(PlaybackPhase.Paused)
    }

    override fun onPlayerError(error: PlaybackException) {
        if (switchToOtherAudioTrack(error)) return
        onPhaseChanged(
            PlaybackPhase.Error(PlaybackErrorType.UNKNOWN, describePlaybackError(error))
        )
    }

    /**
     * A device whose decoder can't handle one audio track (e.g. AAC "Main" profile on a Fire TV's AAC decoder) often
     * handles another one in the same file. When the audio decoder fails, pick the next playable audio track and
     * carry on from the same position instead of showing an error; false when there is nothing else to try.
     */
    private fun switchToOtherAudioTrack(error: PlaybackException): Boolean {
        val player = player ?: return false
        val audioFailure = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        if (!audioFailure) return false
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val failing = audioGroups.firstOrNull { it.isSelected } ?: return false
        // Only a failure of the audio side is handled here (the message names MediaCodecAudioRenderer / AudioTrack); a video one still shows the error.
        if (error.message?.contains("Audio") != true) return false
        failedAudioGroups += failing.mediaTrackGroup
        val next = audioGroups.firstOrNull { it.mediaTrackGroup !in failedAudioGroups && it.isTrackSupported(0) } ?: return false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0))
            .build()
        player.prepare()
        player.playWhenReady = true
        return true
    }

    override fun onTracksChanged(tracks: Tracks) {
        onTracksChangedCallback(tracks)
    }
}

/** The player's message plus its error code (and the underlying cause when there is one), so a failure on a device is diagnosable from a photo of the screen. */
internal fun describePlaybackError(error: PlaybackException): String {
    val base = error.message ?: "The selected stream could not be played."
    val cause = error.cause?.message?.takeIf { it.isNotBlank() && it !in base }
    return buildString {
        append(base)
        if (cause != null) append(" (").append(cause).append(')')
        append(" [").append(error.errorCodeName).append(']')
    }
}
