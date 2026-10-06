package com.mangotv.app.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
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
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
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
private const val TORRENT_READ_TIMEOUT_MINUTES = 2L

@OptIn(UnstableApi::class)
fun buildExoPlayer(
    context: Context,
    preferences: PlayerPreferences,
    audioOutput: AudioOutputSettings,
    dolbyVision: DolbyVisionSwitch,
    // True for a torrent served from the app's own local address: a piece can take a while to arrive, so the connection is given far longer
    // than the 10 seconds OkHttp allows by default before a slow read counts as a failure (the torrent engine reports a dead torrent itself).
    localTorrent: Boolean = false
): ExoPlayer {
    val client = OkHttpClient.Builder().apply {
        if (localTorrent) readTimeout(TORRENT_READ_TIMEOUT_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
    }.build()
    val httpDataSourceFactory = OkHttpDataSource.Factory(client)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    // Decoder fallback: when the first-choice hardware decoder for a track can't start (some Fire TV audio decoders
    // accept a format on paper, e.g. AAC "Main" profile, then fail when asked to play it), try the next decoder the
    // device offers -- usually the software one -- instead of giving up with "Unable to play this source".
    // Audio passthrough off (Advanced settings in the player): the TV is never offered the raw Dolby/DTS stream, so a TV or receiver that
    // claims support it doesn't really have gives sound anyway. The switch is read live; PlayerScreen re-prepares playback when it flips.
    val renderersFactory = SwitchableRenderersFactory(context, audioOutput)
        .setEnableDecoderFallback(true)
        .setMediaCodecSelector(dolbyVisionAwareSelector(dolbyVision))
        // ON = the FFmpeg audio renderer (media3-ffmpeg-decoder) is tried only after the device's own decoders, so a track the device
        // can't decode (DTS, DTS-HD, TrueHD on most TV boxes) is decoded in software instead of staying silent.
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    val player = ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()

    var parametersBuilder = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !preferences.subtitlesEnabled)
        // Settings > Audio: prefer a track in the chosen language, and (stereo / 5.1) one that already fits the speakers when the file has it.
        .setMaxAudioChannelCount(audioOutput.channelMode.maxChannels)
    if (preferences.defaultAudioLanguage != null) {
        parametersBuilder = parametersBuilder.setPreferredAudioLanguage(preferences.defaultAudioLanguage)
    }
    if (preferences.subtitlesEnabled && preferences.defaultSubtitleLanguage != null) {
        parametersBuilder = parametersBuilder.setPreferredTextLanguage(preferences.defaultSubtitleLanguage)
    }
    player.trackSelectionParameters = parametersBuilder.build()

    return player
}

/**
 * Whether Dolby Vision files may use a Dolby Vision decoder; off plays them as the HDR10 picture underneath (plain HEVC decoders). On to
 * begin with; the retry in [PlayerListenerBridge] turns it off for one playback when a Dolby Vision file fails to decode. Read live.
 */
class DolbyVisionSwitch(@Volatile var enabled: Boolean)

/**
 * Dolby Vision files list the device's Dolby Vision decoders first and its plain HEVC decoders after them, so a device with no (working) Dolby
 * Vision decoder plays the HDR10 base layer; with the switch off only the HEVC decoders are offered. Every other format is untouched.
 */
@OptIn(UnstableApi::class)
private fun dolbyVisionAwareSelector(dolbyVision: DolbyVisionSwitch) = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
    val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    if (mimeType != MimeTypes.VIDEO_DOLBY_VISION) {
        infos
    } else {
        val hevc = MediaCodecSelector.DEFAULT.getDecoderInfos(MimeTypes.VIDEO_H265, requiresSecureDecoder, requiresTunnelingDecoder)
        (if (dolbyVision.enabled) infos + hevc else hevc).distinctBy { it.name }
    }
}

/**
 * What this playback may send to the TV, read live: whether raw Dolby/DTS audio may be passed through, and the most channels wanted.
 * Flipping either takes effect on the next (re)prepare.
 */
class AudioOutputSettings(@Volatile var passthrough: Boolean, @Volatile var channelMode: AudioChannelMode)

/** The default renderers, with an audio output that follows [settings]: it refuses encoded (non-PCM) formats the person doesn't want passed through, and mixes surround sound down. */
@OptIn(UnstableApi::class)
private class SwitchableRenderersFactory(context: Context, private val settings: AudioOutputSettings) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
        SwitchableAudioSink(
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(DownmixAudioProcessor { settings.channelMode.maxChannels }))
                .build(),
            settings
        )
}

@OptIn(UnstableApi::class)
private class SwitchableAudioSink(delegate: AudioSink, private val settings: AudioOutputSettings) : ForwardingAudioSink(delegate) {
    // An encoded stream can't be mixed down here, so it is only passed through when that is wanted and it stays within the channel limit
    // (a stream whose channel count is unknown is allowed).
    private fun blocked(format: Format): Boolean {
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW) return false
        if (!settings.passthrough) return true
        return format.channelCount != Format.NO_VALUE && format.channelCount > settings.channelMode.maxChannels
    }

    override fun supportsFormat(format: Format): Boolean = !blocked(format) && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (blocked(format)) AudioSink.SINK_FORMAT_UNSUPPORTED else super.getFormatSupport(format)
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
    private val player: Player? = null,
    // Shared with the player's Dolby Vision switch: turned off by the retry below when a Dolby Vision file fails to decode.
    private val dolbyVision: DolbyVisionSwitch? = null
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
        if (retryWithoutDolbyVision(error)) return
        onPhaseChanged(
            PlaybackPhase.Error(if (isFormatFailure(error)) PlaybackErrorType.UNSUPPORTED_SOURCE else PlaybackErrorType.UNKNOWN, describePlaybackError(error))
        )
    }

    private var triedWithoutDolbyVision = false

    /**
     * A Dolby Vision file the device fails to decode often plays as its HDR10 base layer: once per session, turn Dolby Vision off and prepare
     * again from the same spot instead of showing an error. False when it isn't that case (not a format failure, not Dolby Vision, already tried).
     */
    private fun retryWithoutDolbyVision(error: PlaybackException): Boolean {
        val player = player ?: return false
        val switch = dolbyVision ?: return false
        if (triedWithoutDolbyVision || !switch.enabled || !isFormatFailure(error)) return false
        val isDolbyVision = player.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_VIDEO && group.isSelected &&
                (0 until group.length).any { group.getTrackFormat(it).sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION }
        }
        if (!isDolbyVision) return false
        triedWithoutDolbyVision = true
        switch.enabled = false
        val item = player.currentMediaItem ?: return false
        val position = player.currentPosition
        player.setMediaItem(item, position)
        player.prepare()
        player.playWhenReady = true
        return true
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

/**
 * True when the player could not decode or read the source's own format (a codec or profile the device has no decoder for, a container it
 * can't parse), as opposed to a network or other failure -- the case VLC's engine can often still play (the error screen's "Other Players" button leads to it).
 */
internal fun isFormatFailure(error: PlaybackException): Boolean = when (error.errorCode) {
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> true
    else -> false
}
