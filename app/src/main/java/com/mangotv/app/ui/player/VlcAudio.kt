package com.mangotv.app.ui.player

import android.content.Context
import android.media.AudioFormat
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
    val summary = "VLC audio: $device (speakers=$mode, passthrough=$passthrough)"
    Log.w("ArcAudio", summary)
    // Temporary diagnostic: shown on screen so the choice can be read without Logcat.
    android.widget.Toast.makeText(context, summary, android.widget.Toast.LENGTH_LONG).show()
    when (device) {
        VlcAudioDevice.Stereo -> player.setAudioOutput("opensles_android")
        VlcAudioDevice.Pcm -> player.setAudioOutputDevice("pcm")
        is VlcAudioDevice.Encoded -> player.forceAudioDigitalEncodings(device.encodings.toIntArray())
    }
}
