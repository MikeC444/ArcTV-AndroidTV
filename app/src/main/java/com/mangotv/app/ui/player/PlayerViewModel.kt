package com.mangotv.app.ui.player

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.mangotv.app.data.player.matchLastSource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Tracks
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.addon.AddonSubtitle
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.data.model.Stream
import com.mangotv.app.data.network.ExternalPlayerEventDto
import com.mangotv.app.data.network.PlaybackOutcomeDto
import com.mangotv.app.data.player.StreamHandoff
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.ui.sources.pickByAudio
import com.mangotv.app.ui.sources.recommendedStreamId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * Re-derives everything from route args rather than taking anything
 * in-memory from the Sources screen — same convention SourcesViewModel and
 * DetailViewModel already use, and it survives process death since a
 * Stream's id is deterministic across an identical getStreams() re-fetch.
 *
 * Only consumes/exposes state — never touches a live ExoPlayer. The
 * Composable owns the player instance and issues commands (play/pause/
 * seek/track selection) directly; this ViewModel only reacts to
 * Player.Listener callbacks forwarded into [onPlaybackPhaseChanged].
 */
class PlayerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val preferencesRepository = (application as MangoTvApplication).container.playerPreferencesRepository
    private val continueWatchingRepository = (application as MangoTvApplication).container.continueWatchingRepository
    private val continueWatchingSyncRepository = (application as MangoTvApplication).container.continueWatchingSyncRepository
    private val lastSourceRepository = (application as MangoTvApplication).container.lastSourceRepository
    private val externalPlayerRepository = (application as MangoTvApplication).container.externalPlayerRepository
    private val playbackOutcomeRepository = (application as MangoTvApplication).container.playbackOutcomeRepository
    private val subtitleFinder = (application as MangoTvApplication).container.subtitleFinder
    private val myListRepository = (application as MangoTvApplication).container.myListRepository
    private val customTorrentRepository = (application as MangoTvApplication).container.customTorrentRepository

    /** Identifies this title (not the episode) for the player remembered for it (DevicePlayerPrefs.titlePlayer). */
    val titleKey: String get() = "$providerId|$contentId|${contentType.name}"

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
    // Start as the route says; playEpisode moves them on without leaving the player.
    private var season: Int? = savedStateHandle.get<String>("season")?.toIntOrNull()?.takeIf { it >= 0 }
    private var episodeNumber: Int? = savedStateHandle.get<String>("episode")?.toIntOrNull()?.takeIf { it >= 0 }
    private var streamId: String =
        URLDecoder.decode(savedStateHandle.get<String>("streamId").orEmpty(), "UTF-8")

    /** The season and episode being played now (the route's, until the next episode was started). */
    val currentSeason: Int? get() = season
    val currentEpisode: Int? get() = episodeNumber

    private val _uiState = MutableStateFlow<PlayerScreenUiState>(PlayerScreenUiState.Loading)
    val uiState: StateFlow<PlayerScreenUiState> = _uiState.asStateFlow()

    private val _playbackPhase = MutableStateFlow<PlaybackPhase>(PlaybackPhase.Loading)
    val playbackPhase: StateFlow<PlaybackPhase> = _playbackPhase.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackOption>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackOption>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrackOption>> = _subtitleTracks.asStateFlow()

    private val _qualityOptions = MutableStateFlow<List<QualityOption>>(emptyList())
    val qualityOptions: StateFlow<List<QualityOption>> = _qualityOptions.asStateFlow()

    val preferences: StateFlow<PlayerPreferences> = preferencesRepository.preferences

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PlayerScreenUiState.Loading
            _playbackPhase.value = PlaybackPhase.Loading

            val providers = ProviderRegistry.activeProviders()
            val owningProvider = providers.find { it.id == providerId }
            val content = owningProvider?.let { runCatching { it.getDetails(contentType, contentId) }.getOrNull() }
            if (content == null) {
                reportNeverStarted("details did not load")
                _uiState.value = PlayerScreenUiState.Error("Couldn't load details for this title.")
                return@launch
            }

            // Same "query every active provider and merge" rule as the
            // Sources screen — run in parallel since the player route pays
            // this cost a second time on top of the one Sources already paid.
            // The source the person just picked comes straight from the source list: nothing to look up again, so an addon whose ids change
            // between two requests (as some addons do) can't make it vanish. Only a resume from Detail, which has just the remembered id, asks again.
            val handedOver = StreamHandoff.get(streamId)
            val streams = if (handedOver != null) emptyList() else coroutineScope {
                providers.map { provider ->
                    async { runCatching { provider.getStreams(contentType, contentId, season, episodeNumber) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }.flatten() + customTorrentRepository.streamsFor(providerId, contentId, contentType, season, episodeNumber)

            // The source's id can change between two fetches of the same addon: carry on with the same release when it is still offered.
            val stream = handedOver
                ?: streams.find { it.id == streamId }
                ?: lastSourceRepository.findLastSource(providerId, contentId, contentType, season, episodeNumber)
                    ?.let { matchLastSource(streams, it) }
            if (stream == null) {
                reportNeverStarted("source no longer available")
                _uiState.value = PlayerScreenUiState.Error("This source is no longer available.")
                return@launch
            }

            val episode: Episode? = if (season != null && episodeNumber != null) {
                content.seasons.find { it.seasonNumber == season }
                    ?.episodes?.find { it.episodeNumber == episodeNumber }
            } else {
                null
            }

            setReady(PlayerScreenUiState.Ready(content, episode, stream))
        }
    }

    // What progress reports are filed under: the episode that is on screen. It only moves on when the next episode's player is ready, so
    // the old player's last report (sent as it is removed) still goes to the episode it was playing.
    private var activeReady: PlayerScreenUiState.Ready? = null
    private var activeSeason: Int? = null
    private var activeEpisodeNumber: Int? = null

    private fun setReady(ready: PlayerScreenUiState.Ready) {
        placeholderSource = false
        activeReady = ready
        activeSeason = season
        activeEpisodeNumber = episodeNumber
        _uiState.value = ready
    }

    /**
     * Plays an episode (the next one, or one picked in the episode selector) inside the player: so there is no trip through the source list (which flashed on screen). The
     * loading screen (backdrop and logo) shows while its sources are found; the source is the one this episode was last watched on,
     * else the recommended one for the person's Speakers setting. With none found, the error screen offers the source list.
     */
    fun playEpisode(nextSeason: Int, nextEpisode: Int) {
        val content = (uiState.value as? PlayerScreenUiState.Ready)?.content ?: return
        if (nextSeason == season && nextEpisode == episodeNumber) return // already playing it
        season = nextSeason
        episodeNumber = nextEpisode
        streamId = ""
        val episode = content.seasons.find { it.seasonNumber == nextSeason }?.episodes?.find { it.episodeNumber == nextEpisode }
        _playbackPhase.value = PlaybackPhase.Loading
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        _qualityOptions.value = emptyList()
        _uiState.value = PlayerScreenUiState.Switching(content, episode)
        viewModelScope.launch {
            val providers = ProviderRegistry.activeProviders()
            val streams = coroutineScope {
                providers.map { provider ->
                    async { runCatching { provider.getStreams(contentType, contentId, nextSeason, nextEpisode) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }.flatten() + customTorrentRepository.streamsFor(providerId, contentId, contentType, nextSeason, nextEpisode)
            val audioMode = DevicePlayerPrefs.audioChannelMode(getApplication<Application>())
            val stream = lastSourceRepository.findLastSource(providerId, contentId, contentType, nextSeason, nextEpisode)
                ?.let { matchLastSource(streams, it) }
                ?: recommendedStreamId(pickByAudio(streams, audioMode).streams)?.let { id -> streams.find { it.id == id } }
            if (stream != null) {
                setReady(PlayerScreenUiState.Ready(content, episode, stream))
            } else {
                reportNeverStarted("no sources for the next episode")
                _uiState.value = PlayerScreenUiState.Error("No sources were found for the next episode.")
            }
        }
    }

    /**
     * One confirmed "Choose player" pick other than the built-in player: [fromError] when it was offered because the built-in player failed,
     * [engine] "external" (another app) or "vlc" (VLC's engine inside Arc TV), [opened] false when no player app took it.
     */
    fun recordExternalPlayer(fromError: Boolean, opened: Boolean, errorMessage: String?, engine: String) {
        val state = uiState.value as? PlayerScreenUiState.Ready ?: return
        externalPlayerRepository.record(
            ExternalPlayerEventDto(
                providerId = providerId,
                contentId = contentId,
                contentType = contentType.name,
                title = state.content.title,
                releaseTitle = state.stream.releaseTitle.take(500),
                resolution = state.stream.qualityBadge,
                codec = state.stream.codec,
                trigger = if (fromError) "error" else "button",
                outcome = if (opened) "opened" else "no_player",
                engine = engine,
                errorMessage = errorMessage?.take(1000)
            )
        )
    }

    /**
     * A tracker for one viewing of [stream] in the given player ("vlc" or "builtin"): it reports once, as the viewing ends, how it went
     * (see [PlaybackHealthTracker]). [sourcesTried] is how many sources were opened for this title in this sitting.
     */
    fun newHealthTracker(stream: Stream, engine: String, sourcesTried: Int) = PlaybackHealthTracker(
        engine = engine,
        deviceModel = Build.MODEL?.take(80),
        resolution = stream.qualityBadge,
        codec = stream.codec,
        hdr = hdrLabelOf(stream.releaseTitle),
        sourcesTried = sourcesTried,
        report = playbackOutcomeRepository::record
    )

    /** A viewing that ended before any player opened (the source could not be found, details failed to load): reported like any other. */
    private fun reportNeverStarted(kind: String) {
        playbackOutcomeRepository.record(
            PlaybackOutcomeDto(
                engine = "none",
                deviceModel = Build.MODEL?.take(80),
                watchedMs = 0,
                outcome = "never_started",
                errorKind = kind,
                sourcesTried = SourceAttempts.next(titleKey)
            )
        )
    }

    /** The subtitle files the person's addons offer for what is playing now (empty when no addon offers any, or none answers in time). */
    suspend fun findAddonSubtitles(preferredLanguage: String?): List<AddonSubtitle> {
        val ready = uiState.value as? PlayerScreenUiState.Ready ?: return emptyList()
        return subtitleFinder.find(
            type = contentType,
            id = contentId,
            season = season,
            episode = episodeNumber,
            filename = ready.stream.torrentFilename ?: ready.stream.releaseTitle,
            preferredLanguage = preferredLanguage
        )
    }

    fun onPlaybackPhaseChanged(phase: PlaybackPhase) {
        _playbackPhase.value = phase
    }

    fun onTracksChanged(tracks: Tracks) {
        _audioTracks.value = tracks.toAudioTrackOptions()
        _subtitleTracks.value = tracks.toSubtitleTrackOptions()
        _qualityOptions.value = tracks.toQualityOptions()
    }

    fun setAutoplayNextEpisode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoplayNextEpisode(enabled) }
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSkipIntroEnabled(enabled) }
    }

    /**
     * This exact title/episode's stored resume point, if any -- a
     * synchronous read of the local Continue Watching cache (Milestone 8),
     * looked up once when building the Ready state so PlaybackContent can
     * seek to it on first prepare. Guards on season/episode matching
     * too: a stored entry for a different episode of the same show is not
     * a valid resume point for *this* stream.
     */
    fun resumePositionMs(): Long? =
        continueWatchingRepository.findResumePoint(providerId, contentId, contentType)
            ?.takeIf { it.seasonNumber == season && it.episodeNumber == episodeNumber }
            ?.positionMs

    /**
     * Called by PlaybackContent at the player's own "sensible update
     * strategy" trigger points (periodic while playing, on pause, on
     * stop/dispose, on completion) -- never on every position tick, per
     * the milestone's explicit "don't flood the network" requirement.
     * Deliberately not suspend: the dispose-time call happens from a
     * plain onDispose{} lambda, not a coroutine — see
     * ContinueWatchingSyncRepository.reportProgress's own kdoc for why
     * this whole chain stays non-suspend down to the actual network call.
     */
    /**
     * Set when the source turned out to be a placeholder (a "not ready yet" slate, see [isPlaceholderVideo]): nothing it plays may be saved
     * as progress, and its end must never mark the title as watched. Cleared when a different source starts.
     */
    private var placeholderSource = false

    /** Whether the source now playing is a placeholder slate: while it is, no progress is saved for it. */
    fun onPlaceholderVideo(isPlaceholder: Boolean) {
        placeholderSource = isPlaceholder
    }

    fun reportProgress(positionMs: Long, durationMs: Long, completed: Boolean) {
        if (durationMs <= 0 || placeholderSource) return
        // Ignore a report from before anything has played (the first second): any real watching is worth remembering, but a source
        // that never played must not leave a Continue Watching entry behind.
        if (!completed && positionMs < MIN_REPORTABLE_POSITION_MS) return

        val state = activeReady ?: return
        val reportSeason = activeSeason
        val reportEpisode = activeEpisodeNumber

        // Same gating as the Continue Watching entry this report is about
        // to create (not completed, past the "genuinely started watching"
        // threshold above) -- remembers the source that actually got this
        // title into Continue Watching, so SourcesViewModel can skip
        // straight back to it next time instead of asking the user to pick
        // again. See LastSourceRepository's own doc for why this is a
        // separate, local-only store rather than a field synced with the
        // rest of this report -- and, importantly, why this call is NOT
        // wrapped in viewModelScope.launch{}: this method is called from
        // the player's dispose-time "final report" (see PlayerScreen's own
        // comment on that call site), where viewModelScope may already be
        // cancelling. setLastStreamId dispatches onto its own repository-
        // owned scope instead, so the write survives that regardless.
        if (!completed) {
            lastSourceRepository.setLastSource(providerId, contentId, contentType, reportSeason, reportEpisode, state.stream)
        }

        // A movie counts as watched once it crosses the same >85%-of-
        // duration completion threshold playbackProgressService.
        // recordProgress already applies server-side, not only on
        // ExoPlayer's own literal STATE_ENDED -- most viewers never sit
        // through end credits. This only decides whether to add the movie
        // to My List; the raw `completed` flag passed below still goes to
        // the server unchanged, which independently re-derives the same
        // rule for Continue Watching, so there's no need to duplicate that
        // decision here. Scoped to movies only: a single episode crossing
        // this threshold doesn't mean the show itself is "watched", which
        // My List has no per-episode concept of anyway.
        val crossedCompletionThreshold = contentType == ContentType.MOVIE &&
            positionMs.toDouble() / durationMs.toDouble() > COMPLETION_THRESHOLD
        if (contentType == ContentType.MOVIE && (completed || crossedCompletionThreshold)) {
            myListRepository.markWatched(state.content)
        }

        continueWatchingSyncRepository.reportProgress(
            providerId = providerId,
            contentId = contentId,
            contentType = contentType,
            seasonNumber = reportSeason,
            episodeNumber = reportEpisode,
            episodeTitle = state.episode?.title,
            title = state.content.title,
            posterUrl = state.content.posterUrl,
            backdropUrl = state.content.backdropUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed
        )
    }

    companion object {
        private const val MIN_REPORTABLE_POSITION_MS = 1_000L

        // Matches playbackProgressService.recordProgress's own
        // MOVIE_COMPLETION_FRACTION -- "anywhere past 85%" means strictly
        // more than, not at least, and this only ever needs to match that
        // rule's sense of "watched", not restate it as the source of truth.
        private const val COMPLETION_THRESHOLD = 0.85
    }
}
