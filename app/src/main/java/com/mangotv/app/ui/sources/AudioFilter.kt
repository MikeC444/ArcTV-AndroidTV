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

/** What the Audio drop-down on Select a Source can be set to. */
sealed interface AudioChoice {
    /** Follow the Speakers setting, with the next best when nothing matches (see [pickByAudio]). */
    data object Match : AudioChoice

    /** Every source. */
    data object All : AudioChoice

    /** Only sources with this layout: 2, 6 or 8, or null for the ones whose name doesn't say. */
    data class Layout(val bucket: Int?) : AudioChoice
}

/**
 * The drop-down's entries for this title: "match my speakers" (unless Automatic), "all", then each layout that some source of THIS title has,
 * stereo -> 7.1, then "not listed" for the sources that don't say. Empty when no source names its layout (nothing to filter by).
 */
fun audioChoices(streams: List<Stream>, mode: AudioChannelMode): List<AudioChoice> {
    val buckets = streams.map { audioBucket(it) }
    if (buckets.all { it == null }) return emptyList()
    return buildList {
        if (mode != AudioChannelMode.AUTO) add(AudioChoice.Match)
        add(AudioChoice.All)
        for (bucket in listOf(2, 6, 8)) if (bucket in buckets) add(AudioChoice.Layout(bucket))
        if (null in buckets) add(AudioChoice.Layout(null))
    }
}

/** The sources a choice lists. */
fun applyAudioChoice(streams: List<Stream>, mode: AudioChannelMode, choice: AudioChoice): List<Stream> = when (choice) {
    AudioChoice.Match -> pickByAudio(streams, mode).streams
    AudioChoice.All -> streams
    is AudioChoice.Layout -> streams.filter { audioBucket(it) == choice.bucket }
}

/** The choice used until the person picks one: their Speakers setting when it isn't Automatic, else everything. */
fun defaultAudioChoice(mode: AudioChannelMode): AudioChoice = if (mode == AudioChannelMode.AUTO) AudioChoice.All else AudioChoice.Match

private fun layoutLabel(bucket: Int?): String = if (bucket == null) "Not listed" else layoutName(bucket)

/** One row of the drop-down, with how many sources it lists. */
fun audioChoiceLabel(choice: AudioChoice, streams: List<Stream>, mode: AudioChannelMode): String {
    val count = applyAudioChoice(streams, mode, choice).size
    val name = when (choice) {
        AudioChoice.Match -> "Match my speakers"
        AudioChoice.All -> "All audio"
        is AudioChoice.Layout -> layoutLabel(choice.bucket)
    }
    return "$name ($count)"
}

/** The pill's text: "Audio: 5.1", "Audio: 7.1 (no 5.1 found)" (the next best was used), "Audio: All". */
fun audioButtonLabel(choice: AudioChoice, streams: List<Stream>, mode: AudioChannelMode): String = when (choice) {
    AudioChoice.Match -> audioPillLabel(mode, pickByAudio(streams, mode)) ?: "Audio: All"
    AudioChoice.All -> "Audio: All"
    is AudioChoice.Layout -> "Audio: ${layoutLabel(choice.bucket)}"
}
