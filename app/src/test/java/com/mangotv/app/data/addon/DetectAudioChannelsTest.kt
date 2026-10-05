package com.mangotv.app.data.addon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectAudioChannelsTest {
    @Test
    fun `reads a layout from a release name`() {
        assertEquals(6, detectAudioChannels("Movie.2160p.DDP5.1.Atmos.HEVC"))
        assertEquals(6, detectAudioChannels("Movie.DD+.5.1"))
        assertEquals(8, detectAudioChannels("Movie.DTS-HD.MA.7.1.1080p"))
        assertEquals(2, detectAudioChannels("Movie.1080p.AAC2.0.x264"))
        assertEquals(6, detectAudioChannels("Movie.6CH.AAC"))
        assertEquals(2, detectAudioChannels("Movie stereo mix"))
    }

    @Test
    fun `the highest layout wins and a bare Atmos counts as 7_1`() {
        assertEquals(6, detectAudioChannels("Movie AAC2.0 DDP5.1 multi"))
        assertEquals(8, detectAudioChannels("Movie.BluRay.Atmos"))
        assertEquals(6, detectAudioChannels("Movie.DDP5.1.Atmos"))
    }

    @Test
    fun `says nothing when the name does not say, and ignores numbers that are not layouts`() {
        assertNull(detectAudioChannels("Movie.DTS-HD.MA.x265"))
        assertNull(detectAudioChannels("Movie.H.264.5.1.1"))
        assertNull(detectAudioChannels("Movie v1.5.1 x264"))
        assertNull(detectAudioChannels("Movie.S01E05.1080p"))
    }
}
