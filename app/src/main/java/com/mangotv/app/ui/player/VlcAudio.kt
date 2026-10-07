package com.mangotv.app.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import org.videolan.libvlc.MediaPlayer

/** Where VLC's engine sends its sound. */
sealed interface VlcAudioDevice {
    /** Two channels only (VLC's own default). */
    data object Stereo : VlcAudioDevice
    /** Plain surround (up to 8 channels) as PCM; the system mixes it down if the TV can't take it. */
    data object Pcm : VlcAudioDevice
    /** Surround as PCM, and these Dolby / DTS formats ([AudioFormat] encodings) sent to the TV or receiver untouched. */
    data class Encoded(val encodings: List<Int>) : VlcAudioDevice
}

/**
 * Which of VLC's outputs to use. LibVLC starts on a "stereo" output and only leaves it by itself when the TV reports its formats a particular
 * way over HDMI; on a TV or soundbar that doesn't (many Fire TV set-ups), VLC kept playing two channels whatever the Passthrough setting said.
 * So anything but the Stereo setting picks a surround output explicitly: with Passthrough on, the formats [supportedEncodings] says the TV
 * takes (the same list the built-in player works from), otherwise plain surround PCM.
 */
fun vlcAudioDevice(mode: AudioChannelMode, passthrough: Boolean, supportedEncodings: List<Int>): VlcAudioDevice = when {
    mode == AudioChannelMode.STEREO -> VlcAudioDevice.Stereo
    passthrough && supportedEncodings.isNotEmpty() -> VlcAudioDevice.Encoded(supportedEncodings)
    else -> VlcAudioDevice.Pcm
}

/** The Dolby / DTS encodings LibVLC can pass through, in [AudioFormat] numbers. */
private val PASSTHROUGH_CANDIDATES = listOf(
    AudioFormat.ENCODING_AC3,
    AudioFormat.ENCODING_E_AC3,
    AudioFormat.ENCODING_DTS,
    AudioFormat.ENCODING_DTS_HD,
    AudioFormat.ENCODING_DOLBY_TRUEHD
)

/** What this TV takes untouched, from Media3's capability check (which also knows Fire TV's own "Dolby Digital Plus" setting). */
@androidx.annotation.OptIn(UnstableApi::class)
fun supportedPassthroughEncodings(context: Context): List<Int> = try {
    val capabilities = AudioCapabilities.getCapabilities(context)
    PASSTHROUGH_CANDIDATES.filter { capabilities.supportsEncoding(it) }
} catch (e: Exception) {
    emptyList()
}

/** Applies [vlcAudioDevice] to [player]; call before the media starts. */
fun configureVlcAudio(player: MediaPlayer, context: Context, mode: AudioChannelMode, passthrough: Boolean) {
    val device = vlcAudioDevice(mode, passthrough, if (passthrough) supportedPassthroughEncodings(context) else emptyList())
    Log.d("ArcAudio", "VLC audio: $device (speakers=$mode, passthrough=$passthrough)")
    when (device) {
        VlcAudioDevice.Stereo -> player.setAudioOutput("opensles_android")
        VlcAudioDevice.Pcm -> player.setAudioOutputDevice("pcm")
        is VlcAudioDevice.Encoded -> player.forceAudioDigitalEncodings(device.encodings.toIntArray())
    }
}

@Volatile private var surroundPrimed = false

/**
 * Opens a silent 5.1 output for a moment, once per run of the app. On the Fire TV Stick 4K Max, VLC played stereo until the built-in player had
 * played something (that player's output switches the TV or soundbar into surround), after which VLC's surround worked. Doing the same
 * briefly before VLC starts makes the first VLC playback behave like the second. Blocks for a few hundred milliseconds; call off the main thread.
 */
fun primeSurroundOutput() {
    if (surroundPrimed) return
    surroundPrimed = true
    var track: AudioTrack? = null
    try {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1)
            .build()
        val bytes = 48000 * 6 * 2 / 4 // a quarter of a second of silence
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setAudioFormat(format)
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        track.write(ByteArray(bytes), 0, bytes)
        Thread.sleep(400)
        Log.d("ArcAudio", "Primed a 5.1 output")
    } catch (e: Exception) {
        Log.w("ArcAudio", "Could not prime a 5.1 output", e)
    } finally {
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
    }
}
