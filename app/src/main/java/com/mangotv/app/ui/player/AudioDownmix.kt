package com.mangotv.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** The most speakers' worth of sound the person wants sent out (Settings > Audio): what the device supports, stereo, or 5.1. */
enum class AudioChannelMode(val wire: String, val maxChannels: Int) {
    AUTO("auto", Int.MAX_VALUE),
    STEREO("stereo", 2),
    SURROUND_5_1("5.1", 6);

    companion object {
        fun fromWire(value: String?): AudioChannelMode = entries.firstOrNull { it.wire == value } ?: AUTO
    }
}

/**
 * Mixes 5.1 or 7.1 sound (16-bit) down to what [maxChannels] allows: stereo, or 5.1 from 7.1. Anything else (already within the limit,
 * other layouts, other sample formats) is left alone by reporting the processor inactive. Channel order is Android's: front left,
 * front right, centre, low-frequency, back left, back right, then (7.1 only) side left, side right.
 *
 * The mix follows the usual ITU shape (centre and surrounds at -3 dB) with a fixed gain that keeps dialogue audible; peaks that
 * still add past full scale are clipped. The low-frequency channel is dropped from a stereo mix.
 */
@OptIn(UnstableApi::class)
internal class DownmixAudioProcessor(private val maxChannels: () -> Int) : BaseAudioProcessor() {

    private var outChannels = 0
    private var inChannels = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val limit = maxChannels()
        val channels = inputAudioFormat.channelCount
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || (channels != 6 && channels != 8) || limit >= channels) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inChannels = channels
        outChannels = if (limit <= 2) 2 else 6
        if (outChannels >= channels) return AudioProcessor.AudioFormat.NOT_SET
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, outChannels, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * inChannels)
        val output = replaceOutputBuffer(frames * 2 * outChannels)
        val f = FloatArray(8)
        repeat(frames) {
            for (c in 0 until inChannels) f[c] = inputBuffer.short.toFloat()
            val sideL = if (inChannels == 8) f[6] else 0f
            val sideR = if (inChannels == 8) f[7] else 0f
            if (outChannels == 2) {
                output.putShort(clip((f[0] + CENTRE * f[2] + CENTRE * (f[4] + sideL)) * STEREO_GAIN))
                output.putShort(clip((f[1] + CENTRE * f[2] + CENTRE * (f[5] + sideR)) * STEREO_GAIN))
            } else {
                output.putShort(clip(f[0]))
                output.putShort(clip(f[1]))
                output.putShort(clip(f[2]))
                output.putShort(clip(f[3]))
                output.putShort(clip(CENTRE * (f[4] + sideL)))
                output.putShort(clip(CENTRE * (f[5] + sideR)))
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun clip(value: Float): Short = value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val CENTRE = 0.707f
        const val STEREO_GAIN = 0.7f
    }
}
