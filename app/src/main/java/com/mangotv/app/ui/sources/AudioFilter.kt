package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.Stream
import com.mangotv.app.ui.player.AudioChannelMode

/**
 * Which sources Select a Source shows for the person's Speakers setting (Settings > Audio).
 *
 * [streams] is what to list; [channels] is the layout they match (2, 6 or 8; null when nothing could be matched and everything is
 * listed); [exact] is false when no source had the wanted layout and the next best was used instead.
 */
data class AudioPick(val streams: List<Stream>, val channels: Int?, val exact: Boolean)

/** A release's layout bucket: 2 (stereo or mono), 6 (up to 5.1), 8 (7.1 and up), or null when its name doesn't say. */
fun audioBucket(stream: Stream): Int? = stream.audioChannels?.let { if (it <= 2) 2 else if (it <= 6) 6 else 8 }

/**
 * Stereo wants stereo, then 5.1, then 7.1 (both mix down fine). 5.1 wants 5.1, then 7.1 (mixes down to 5.1), then stereo. The first layout
 * that any source has is what is shown; sources whose name doesn't say are only listed when nothing could be matched at all. Automatic
 * shows everything.
 */
fun pickByAudio(streams: List<Stream>, mode: AudioChannelMode): AudioPick {
    val order = when (mode) {
        AudioChannelMode.AUTO -> return AudioPick(streams, null, true)
        AudioChannelMode.STEREO -> listOf(2, 6, 8)
        AudioChannelMode.SURROUND_5_1 -> listOf(6, 8, 2)
    }
    order.forEachIndexed { index, bucket ->
        val matching = streams.filter { audioBucket(it) == bucket }
        if (matching.isNotEmpty()) return AudioPick(matching, bucket, exact = index == 0)
    }
    return AudioPick(streams, null, true)
}

private fun layoutName(channels: Int): String = when (channels) { 2 -> "Stereo"; 6 -> "5.1"; else -> "7.1" }

/**
 * The filter pill's text, or null when there is nothing to say (Automatic, or no source names its layout so nothing was filtered):
 * "Audio: 5.1", or "Audio: 7.1 (no 5.1 found)" when the next best was used.
 */
fun audioPillLabel(mode: AudioChannelMode, pick: AudioPick): String? {
    val channels = pick.channels ?: return null
    if (mode == AudioChannelMode.AUTO) return null
    val wanted = layoutName(if (mode == AudioChannelMode.STEREO) 2 else 6)
    return if (pick.exact) "Audio: ${layoutName(channels)}" else "Audio: ${layoutName(channels)} (no $wanted found)"
}
