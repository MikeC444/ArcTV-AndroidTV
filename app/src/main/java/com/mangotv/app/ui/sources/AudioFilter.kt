package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.Stream
import com.mangotv.app.ui.player.AudioChannelMode

/** The audio types a source can be filtered by (Select a Source's Audio drop-down, and the Speakers setting). */
enum class AudioKind(val label: String) {
    STEREO("Stereo"),
    SURROUND_5_1("5.1"),
    SURROUND_7_1("7.1"),
    ATMOS("Atmos")
}

/** A release's layout bucket: 2 (stereo or mono), 6 (up to 5.1), 8 (7.1 and up), or null when its name doesn't say. */
fun audioBucket(stream: Stream): Int? = stream.audioChannels?.let { if (it <= 2) 2 else if (it <= 6) 6 else 8 }

/** Whether [stream]'s name says it is this kind. A release can be two (an "Atmos" 5.1 release is both). */
fun matchesKind(stream: Stream, kind: AudioKind): Boolean = when (kind) {
    AudioKind.STEREO -> audioBucket(stream) == 2
    AudioKind.SURROUND_5_1 -> audioBucket(stream) == 6
    AudioKind.SURROUND_7_1 -> audioBucket(stream) == 8
    AudioKind.ATMOS -> stream.audioAtmos
}

/** True when the release's name says nothing about its audio. */
fun audioUnlisted(stream: Stream): Boolean = audioBucket(stream) == null && !stream.audioAtmos

/**
 * The kinds a Speakers setting wants, best first: the exact one, then the closest ones (a bigger layout mixes down fine, so it comes before
 * a smaller one). Stereo -> 5.1 -> 7.1; 5.1 -> 7.1 -> stereo; 7.1 -> 5.1 -> stereo; Atmos -> 7.1 -> 5.1 -> stereo. Automatic wants nothing in particular.
 */
fun kindsFor(mode: AudioChannelMode): List<AudioKind> = when (mode) {
    AudioChannelMode.AUTO -> emptyList()
    AudioChannelMode.STEREO -> listOf(AudioKind.STEREO, AudioKind.SURROUND_5_1, AudioKind.SURROUND_7_1)
    AudioChannelMode.SURROUND_5_1 -> listOf(AudioKind.SURROUND_5_1, AudioKind.SURROUND_7_1, AudioKind.STEREO)
    AudioChannelMode.SURROUND_7_1 -> listOf(AudioKind.SURROUND_7_1, AudioKind.SURROUND_5_1, AudioKind.STEREO)
    AudioChannelMode.DOLBY_ATMOS -> listOf(AudioKind.ATMOS, AudioKind.SURROUND_7_1, AudioKind.SURROUND_5_1, AudioKind.STEREO)
}

/**
 * Which sources Select a Source lists for the person's Speakers setting.
 *
 * [streams] is what to list; [kind] is the type they match (null when nothing could be matched and everything is listed); [exact] is
 * false when no source was the wanted type and the next best was used instead.
 */
data class AudioPick(val streams: List<Stream>, val kind: AudioKind?, val exact: Boolean)

/**
 * The first kind in [kindsFor] that any source is, with those sources; sources whose name doesn't say are only listed when nothing could
 * be matched at all. Automatic lists everything.
 */
fun pickByAudio(streams: List<Stream>, mode: AudioChannelMode): AudioPick {
    kindsFor(mode).forEachIndexed { index, kind ->
        val matching = streams.filter { matchesKind(it, kind) }
        if (matching.isNotEmpty()) return AudioPick(matching, kind, exact = index == 0)
    }
    return AudioPick(streams, null, true)
}

/**
 * The pill's text when the Speakers setting is doing the filtering, or null when there is nothing to say (Automatic, or nothing was
 * matched): "Audio: 5.1", or "Audio: 7.1 (no 5.1 found)" when the next best was used.
 */
fun audioPillLabel(mode: AudioChannelMode, pick: AudioPick): String? {
    val kind = pick.kind ?: return null
    val wanted = kindsFor(mode).firstOrNull() ?: return null
    return if (pick.exact) "Audio: ${kind.label}" else "Audio: ${kind.label} (no ${wanted.label} found)"
}

/** What the Audio drop-down on Select a Source can be set to. */
sealed interface AudioChoice {
    /** Follow the Speakers setting, with the next best when nothing matches (see [pickByAudio]). */
    data object Match : AudioChoice

    /** Every source. */
    data object All : AudioChoice

    /** Only sources of this kind. */
    data class OfKind(val kind: AudioKind) : AudioChoice

    /** Only the sources whose name doesn't say. */
    data object Unlisted : AudioChoice
}

/**
 * The drop-down's entries for this title: "match my speakers" (unless Automatic), "all", then each kind that some source of THIS title is
 * (stereo, 5.1, 7.1, Atmos), then "not listed" for the sources that don't say. Empty when no source names its audio (nothing to filter by).
 */
fun audioChoices(streams: List<Stream>, mode: AudioChannelMode): List<AudioChoice> {
    if (streams.all { audioUnlisted(it) }) return emptyList()
    return buildList {
        if (mode != AudioChannelMode.AUTO) add(AudioChoice.Match)
        add(AudioChoice.All)
        for (kind in AudioKind.entries) if (streams.any { matchesKind(it, kind) }) add(AudioChoice.OfKind(kind))
        if (streams.any { audioUnlisted(it) }) add(AudioChoice.Unlisted)
    }
}

/** The sources a choice lists. */
fun applyAudioChoice(streams: List<Stream>, mode: AudioChannelMode, choice: AudioChoice): List<Stream> = when (choice) {
    AudioChoice.Match -> pickByAudio(streams, mode).streams
    AudioChoice.All -> streams
    is AudioChoice.OfKind -> streams.filter { matchesKind(it, choice.kind) }
    AudioChoice.Unlisted -> streams.filter { audioUnlisted(it) }
}

/** The choice used until the person picks one: their Speakers setting when it isn't Automatic, else everything. */
fun defaultAudioChoice(mode: AudioChannelMode): AudioChoice = if (mode == AudioChannelMode.AUTO) AudioChoice.All else AudioChoice.Match

/** One row of the drop-down, with how many sources it lists. */
fun audioChoiceLabel(choice: AudioChoice, streams: List<Stream>, mode: AudioChannelMode): String {
    val count = applyAudioChoice(streams, mode, choice).size
    val name = when (choice) {
        AudioChoice.Match -> "Match my speakers"
        AudioChoice.All -> "All audio"
        is AudioChoice.OfKind -> choice.kind.label
        AudioChoice.Unlisted -> "Not listed"
    }
    return "$name ($count)"
}

/** The pill's text: "Audio: 5.1", "Audio: 7.1 (no 5.1 found)" (the next best was used), "Audio: All". */
fun audioButtonLabel(choice: AudioChoice, streams: List<Stream>, mode: AudioChannelMode): String = when (choice) {
    AudioChoice.Match -> audioPillLabel(mode, pickByAudio(streams, mode)) ?: "Audio: All"
    AudioChoice.All -> "Audio: All"
    is AudioChoice.OfKind -> "Audio: ${choice.kind.label}"
    AudioChoice.Unlisted -> "Audio: Not listed"
}
