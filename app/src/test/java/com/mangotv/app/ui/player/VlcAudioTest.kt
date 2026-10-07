package com.mangotv.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VlcAudioTest {
    private val encodings = listOf(5, 6, 7)

    @Test fun stereoSettingStaysStereo() {
        assertEquals(VlcAudioDevice.Stereo, vlcAudioDevice(AudioChannelMode.STEREO, passthrough = true, supportedEncodings = encodings))
    }

    @Test fun passthroughOnSendsTheFormatsTheTvTakes() {
        assertEquals(VlcAudioDevice.Encoded(encodings), vlcAudioDevice(AudioChannelMode.AUTO, true, encodings))
        assertEquals(VlcAudioDevice.Encoded(encodings), vlcAudioDevice(AudioChannelMode.SURROUND_5_1, true, encodings))
    }

    @Test fun otherwiseSurroundPcmNotVlcsStereoDefault() {
        assertEquals(VlcAudioDevice.Pcm, vlcAudioDevice(AudioChannelMode.AUTO, passthrough = false, supportedEncodings = encodings))
        assertEquals(VlcAudioDevice.Pcm, vlcAudioDevice(AudioChannelMode.AUTO, passthrough = true, supportedEncodings = emptyList()))
        assertEquals(VlcAudioDevice.Pcm, vlcAudioDevice(AudioChannelMode.DOLBY_ATMOS, false, emptyList()))
    }
}
