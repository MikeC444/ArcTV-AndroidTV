package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Stream
import com.mangotv.app.ui.player.AudioChannelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFilterTest {

    private fun stream(id: String, channels: Int?) = Stream(
        id = id, providerId = "p", providerLabel = "P", resolutionTier = ResolutionTier.FHD_1080P, qualityBadge = "1080p",
        releaseTitle = id, audioChannels = channels
    )

    private val stereo = stream("stereo", 2)
    private val five = stream("five", 6)
    private val seven = stream("seven", 8)
    private val unknown = stream("unknown", null)
    private fun ids(pick: AudioPick) = pick.streams.map { it.id }

    @Test
    fun `automatic lists everything`() {
        val pick = pickByAudio(listOf(stereo, five, seven, unknown), AudioChannelMode.AUTO)
        assertEquals(listOf("stereo", "five", "seven", "unknown"), ids(pick))
        assertNull(audioPillLabel(AudioChannelMode.AUTO, pick))
    }

    @Test
    fun `5_1 lists only the 5_1 sources when there are some`() {
        val pick = pickByAudio(listOf(stereo, five, seven, unknown), AudioChannelMode.SURROUND_5_1)
        assertEquals(listOf("five"), ids(pick))
        assertTrue(pick.exact)
        assertEquals("Audio: 5.1", audioPillLabel(AudioChannelMode.SURROUND_5_1, pick))
    }

    @Test
    fun `5_1 falls back to 7_1, then to stereo`() {
        val toSeven = pickByAudio(listOf(stereo, seven, unknown), AudioChannelMode.SURROUND_5_1)
        assertEquals(listOf("seven"), ids(toSeven))
        assertFalse(toSeven.exact)
        assertEquals("Audio: 7.1 (no 5.1 found)", audioPillLabel(AudioChannelMode.SURROUND_5_1, toSeven))
        val toStereo = pickByAudio(listOf(stereo, unknown), AudioChannelMode.SURROUND_5_1)
        assertEquals(listOf("stereo"), ids(toStereo))
        assertEquals("Audio: Stereo (no 5.1 found)", audioPillLabel(AudioChannelMode.SURROUND_5_1, toStereo))
    }

    @Test
    fun `stereo prefers stereo, then 5_1, then 7_1`() {
        assertEquals(listOf("stereo"), ids(pickByAudio(listOf(stereo, five, seven), AudioChannelMode.STEREO)))
        assertEquals(listOf("five"), ids(pickByAudio(listOf(five, seven, unknown), AudioChannelMode.STEREO)))
        assertEquals(listOf("seven"), ids(pickByAudio(listOf(seven, unknown), AudioChannelMode.STEREO)))
    }

    @Test
    fun `when no source names its layout everything is listed and there is no pill`() {
        val pick = pickByAudio(listOf(unknown, stream("also", null)), AudioChannelMode.SURROUND_5_1)
        assertEquals(listOf("unknown", "also"), ids(pick))
        assertNull(audioPillLabel(AudioChannelMode.SURROUND_5_1, pick))
    }

    @Test
    fun `layouts are bucketed`() {
        assertEquals(2, audioBucket(stream("a", 1)))
        assertEquals(6, audioBucket(stream("b", 3)))
        assertEquals(8, audioBucket(stream("c", 12)))
        assertNull(audioBucket(unknown))
    }

    @Test
    fun `the drop-down offers match, all and only the layouts this title has`() {
        val streams = listOf(stereo, five, unknown, stream("five2", 6))
        assertEquals(
            listOf(AudioChoice.Match, AudioChoice.All, AudioChoice.Layout(2), AudioChoice.Layout(6), AudioChoice.Layout(null)),
            audioChoices(streams, AudioChannelMode.SURROUND_5_1)
        )
        assertEquals(
            listOf(AudioChoice.All, AudioChoice.Layout(2), AudioChoice.Layout(6), AudioChoice.Layout(null)),
            audioChoices(streams, AudioChannelMode.AUTO)
        )
        assertEquals(emptyList<AudioChoice>(), audioChoices(listOf(unknown), AudioChannelMode.SURROUND_5_1))
    }

    @Test
    fun `each choice lists its own sources`() {
        val streams = listOf(stereo, five, seven, unknown)
        val mode = AudioChannelMode.SURROUND_5_1
        assertEquals(listOf("five"), applyAudioChoice(streams, mode, AudioChoice.Match).map { it.id })
        assertEquals(4, applyAudioChoice(streams, mode, AudioChoice.All).size)
        assertEquals(listOf("seven"), applyAudioChoice(streams, mode, AudioChoice.Layout(8)).map { it.id })
        assertEquals(listOf("unknown"), applyAudioChoice(streams, mode, AudioChoice.Layout(null)).map { it.id })
    }

    @Test
    fun `the default follows the speakers setting and the labels count sources`() {
        assertEquals(AudioChoice.All, defaultAudioChoice(AudioChannelMode.AUTO))
        assertEquals(AudioChoice.Match, defaultAudioChoice(AudioChannelMode.STEREO))
        val streams = listOf(stereo, seven, unknown)
        assertEquals("Audio: 7.1 (no 5.1 found)", audioButtonLabel(AudioChoice.Match, streams, AudioChannelMode.SURROUND_5_1))
        assertEquals("Audio: All", audioButtonLabel(AudioChoice.All, streams, AudioChannelMode.SURROUND_5_1))
        assertEquals("Audio: Not listed", audioButtonLabel(AudioChoice.Layout(null), streams, AudioChannelMode.AUTO))
        assertEquals("7.1 (1)", audioChoiceLabel(AudioChoice.Layout(8), streams, AudioChannelMode.AUTO))
        assertEquals("All audio (3)", audioChoiceLabel(AudioChoice.All, streams, AudioChannelMode.AUTO))
    }
}
