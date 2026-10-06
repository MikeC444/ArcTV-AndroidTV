package com.mangotv.app.ui.sources

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mangotv.app.data.player.matchLastSource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Stream
import com.mangotv.app.data.model.StreamLookup
import com.mangotv.app.data.model.StreamReport
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.ui.player.AudioChannelMode
import com.mangotv.app.ui.player.DevicePlayerPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

/** One installed addon and what it answered; [lookup] is null while it is still being asked. */
data class AddonLookupRow(val name: String, val lookup: StreamLookup?)

sealed interface SourcesUiState {
    data object Loading : SourcesUiState
    data class Loaded(
        val content: Content,
        val streams: List<Stream>,
        val recommendedStreamId: String?,
        // Every installed addon and what it answered, in install order -- backs the "what each addon answered" lines
        // and the empty-state hint, so a missing source is never a mystery.
        val addons: List<AddonLookupRow> = emptyList(),
        val season: Int?,
        val episode: Int?,
        // Non-null only when this exact title/episode is already resumable
        // (Continue Watching) and the source it was last watched on is
        // still present in this fresh streams fetch -- SourcesScreen reads
        // this to skip straight to Player instead of showing the picker,
        // so resuming a title never makes the user choose a source again.
        val autoSelectStream: Stream? = null,
        // True while at least one active provider's getStreams() call is
        // still in flight. Lets the picker render whatever it already has
        // instead of waiting for every provider to answer before showing
        // anything -- see load()'s own comment for why. SourcesScreen uses
        // this to keep the "no sources" empty state (with its "try
        // installing more addons" prompt) from flashing before slower
        // providers have had a chance to reply.
        val isSearchingMore: Boolean = false,
        // The Speakers setting at load time (Settings > Audio): the picker lists matching sources first-class, see pickByAudio().
        val audioMode: AudioChannelMode = AudioChannelMode.AUTO
    ) : SourcesUiState
    data class Error(val message: String) : SourcesUiState
}

class SourcesViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val continueWatchingRepository = (application as MangoTvApplication).container.continueWatchingRepository
    private val lastSourceRepository = (application as MangoTvApplication).container.lastSourceRepository
    private val customTorrentRepository = (application as MangoTvApplication).container.customTorrentRepository

    private val providerId: String =
        URLDecoder.decode(savedStateHandle.get<String>("providerId").orEmpty(), "UTF-8")
    private val contentType: ContentType =
        if (savedStateHandle.get<String>("type") == ContentType.TV_SHOW.name) {
            ContentType.TV_SHOW
        } else {
            ContentType.MOVIE
        }
    private val contentId: String =
        URLDecoder.decode(savedStateHandle.get<String>("id").orEmpty(), "UTF-8")
    private val season: Int? = savedStateHandle.get<String>("season")?.toIntOrNull()?.takeIf { it >= 0 }
    private val episode: Int? = savedStateHandle.get<String>("episode")?.toIntOrNull()?.takeIf { it >= 0 }
    // See MangoRoutes.sources's own doc -- true only for the explicit
    // "change source" flow, which must always show the picker.
    private val skipAutoSelect: Boolean = savedStateHandle.get<String>("skipAutoSelect").toBoolean()

    // True only for "Next episode" from the player (see MangoRoutes.sources's autoPlay): take the best source without asking.
    private val autoPlay: Boolean = savedStateHandle.get<String>("auto").toBoolean()

    // Read once per load: the Speakers setting decides which sources are listed and which one is recommended / taken for Next episode.
    private val audioMode: AudioChannelMode = DevicePlayerPrefs.audioChannelMode(application)
    private fun recommendedFor(streams: List<Stream>): String? = recommendedStreamId(pickByAudio(streams, audioMode).streams)

    private val _uiState = MutableStateFlow<SourcesUiState>(SourcesUiState.Loading)
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    /** Why the last "Add a torrent" attempt was refused (shown in its dialog), or null. */
    private val _addTorrentError = MutableStateFlow<String?>(null)
    val addTorrentError: StateFlow<String?> = _addTorrentError.asStateFlow()

    /** The magnet links and .torrent files the person added to this title / episode earlier. */
    private fun customSources(): List<Stream> =
        customTorrentRepository.streamsFor(providerId, contentId, contentType, season, episode)

    fun clearAddTorrentError() { _addTorrentError.value = null }

    /** Adds what was typed or pasted; returns true when it became a source (the list shows it at once, the dialog closes). */
    fun addTorrentText(text: String): Boolean {
        val added = customTorrentRepository.addText(providerId, contentId, contentType, season, episode, text) { _addTorrentError.value = it }
        return showAdded(added)
    }

    fun addTorrentFile(uri: android.net.Uri): Boolean {
        val added = customTorrentRepository.addFile(providerId, contentId, contentType, season, episode, uri) { _addTorrentError.value = it }
        return showAdded(added)
    }

    private fun showAdded(added: Stream?): Boolean {
        if (added == null) return false
        _addTorrentError.value = null
        val current = _uiState.value
        if (current is SourcesUiState.Loaded) {
            _uiState.value = current.copy(streams = listOf(added) + current.streams.filter { it.id != added.id })
        }
        return true
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SourcesUiState.Loading

            val providers = ProviderRegistry.activeProviders()
            val owningProvider = providers.find { it.id == providerId }

            // Known locally (an in-memory cache read, no network) -- decide
            // up front whether this load can end with the resumable
            // shortcut so the branch below never has to guess.  Only
            // auto-continue for the *exact* episode Continue Watching
            // points at -- a remembered source for a different episode of
            // the same show isn't a valid auto-pick for this one, same
            // guard PlayerViewModel.resumePositionMs() already applies.
            val resumeEntry = continueWatchingRepository.findResumePoint(providerId, contentId, contentType)
            val isSameResumeTarget = resumeEntry != null && resumeEntry.seasonNumber == season && resumeEntry.episodeNumber == episode
            val isResumeFlow = (isSameResumeTarget && !skipAutoSelect) || autoPlay

            coroutineScope {
                // getDetails and every provider's getStreams are independent
                // of each other (streams only need the raw id/season/episode
                // args, not the resolved Content) — always start both
                // concurrently rather than waiting on getDetails first.
                val contentDeferred = async {
                    owningProvider?.let { runCatching { it.getDetails(contentType, contentId) }.getOrNull() }
                }

                if (isResumeFlow) {
                    // Same all-or-nothing wait as before: this path must
                    // decide autoSelectStream before emitting anything, since
                    // SourcesScreen shows the loading skeleton (never the
                    // real picker) for as long as this state stays Loading,
                    // and a resumable title should never flash the
                    // interactive source list the user doesn't need to see.
                    val reports = providers.map { provider ->
                        async { provider.getStreamReport(contentType, contentId, season, episode) }
                    }.awaitAll()
                    val streams = customSources() + reports.flatMap { it.streams }
                    val content = contentDeferred.await()
                    if (content == null) {
                        _uiState.value = SourcesUiState.Error("Couldn't load details for this title.")
                        return@coroutineScope
                    }
                    val autoSelectStream = lastSourceRepository.findLastSource(providerId, contentId, contentType, season, episode)
                        ?.let { last -> matchLastSource(streams, last) }
                        // Next episode: no source is remembered for it yet, so take the recommended one (the picker shows if there is none).
                        ?: if (autoPlay) streams.find { it.id == recommendedFor(streams) } else null
                    _uiState.value = SourcesUiState.Loaded(
                        content = content,
                        streams = streams,
                        recommendedStreamId = recommendedFor(streams),
                        addons = reports.map { AddonLookupRow(it.addonName, it.lookup) },
                        season = season,
                        episode = episode,
                        autoSelectStream = autoSelectStream,
                        audioMode = audioMode
                    )
                    return@coroutineScope
                }

                val content = contentDeferred.await()
                if (content == null) {
                    _uiState.value = SourcesUiState.Error("Couldn't load details for this title.")
                    return@coroutineScope
                }

                // Not a resume flow, so the picker is shown either way --
                // no reason to make every provider's results wait on the
                // single slowest one. A real Stremio-style "stream" addon
                // often scrapes live and can take several seconds while
                // another answers in milliseconds; publishing each
                // provider's batch the moment it lands (completion order,
                // not launch order) lets a fast addon's sources appear
                // immediately instead of queuing behind a slow one, the
                // same "don't wait for the slowest of many" fix Home
                // already applies to its own row fetches (see
                // StremioAddonProvider.buildSectionsFlow).
                val resultsChannel = Channel<Pair<Int, StreamReport>>(capacity = providers.size)
                providers.forEachIndexed { providerIndex, provider ->
                    launch {
                        resultsChannel.send(providerIndex to provider.getStreamReport(contentType, contentId, season, episode))
                    }
                }

                val accumulated = mutableListOf<Stream>()
                val rows = providers.map { AddonLookupRow(it.name, null) }.toMutableList()
                repeat(providers.size) { index ->
                    val (providerIndex, report) = resultsChannel.receive()
                    accumulated += report.streams
                    rows[providerIndex] = AddonLookupRow(report.addonName, report.lookup)
                    // Read again each time, so a torrent added by hand while the addons are still answering is not lost.
                    val combined = customSources() + accumulated
                    _uiState.value = SourcesUiState.Loaded(
                        content = content,
                        streams = combined,
                        recommendedStreamId = recommendedFor(combined),
                        addons = rows.toList(),
                        season = season,
                        episode = episode,
                        isSearchingMore = index < providers.size - 1,
                        audioMode = audioMode
                    )
                }
            }
        }
    }
}
