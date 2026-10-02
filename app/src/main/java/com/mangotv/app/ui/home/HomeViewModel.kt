package com.mangotv.app.ui.home

import android.app.Application
import android.graphics.Bitmap
import coil.imageLoader
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.model.RowStyle
import com.mangotv.app.data.model.WatchProgress
import com.mangotv.app.data.feedback.FeedbackEntry
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.recommend.EngineInput
import com.mangotv.app.data.recommend.EngineResult
import com.mangotv.app.data.recommend.Interaction
import com.mangotv.app.data.recommend.MovieRef
import com.mangotv.app.data.recommend.collectInteractions
import com.mangotv.app.data.recommend.excludedFromPicks
import com.mangotv.app.data.recommend.fetchMovieFeatures
import com.mangotv.app.data.recommend.interactionInputs
import com.mangotv.app.data.recommend.pickedSection
import com.mangotv.app.data.recommend.recommend
import com.mangotv.app.data.recommend.signatureOf
import com.mangotv.app.data.recommend.toCandidate
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.blockedGenreSet
import com.mangotv.app.data.provider.withoutBlocked
import com.mangotv.app.data.provider.HomeRowPreferences
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

// How many random titles feed the hero -- HeroSection already rotates
// through whatever list it's given (see its own LaunchedEffect), so this
// is the pool it rotates within, not a fixed set of items shown at once.
// See applyPreferences' own comment for where/how those 10 are picked.
private const val HERO_POOL_SIZE = 10

/** How long Home's rows settle (they arrive in batches) before "Picked for you" is recomputed from them. */
private const val PICKED_DEBOUNCE_MS = 1200L

/** Everything one "Picked for you" run needs, equal exactly when the key is: the key changes only when a result could. */
private class PickedInputs(
    val key: String,
    val movies: List<Content>,
    val interactions: List<Interaction>,
    val excludeIds: Set<String>,
    val refs: Map<String, MovieRef>
) {
    override fun equals(other: Any?): Boolean = other is PickedInputs && other.key == key
    override fun hashCode(): Int = key.hashCode()
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(
        val heroItems: List<Content>,
        val sections: List<HomeSection>
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val homeRowPreferences = (application as MangoTvApplication).container.homeRowPreferencesRepository
    private val myListRepository = (application as MangoTvApplication).container.myListRepository
    private val continueWatchingRepository = (application as MangoTvApplication).container.continueWatchingRepository
    private val homeCacheRepository = (application as MangoTvApplication).container.homeCacheRepository
    private val trailerRepository = (application as MangoTvApplication).container.trailerRepository
    private val guestGate = (application as MangoTvApplication).container.guestGate
    private val blockedGenresRepository = (application as MangoTvApplication).container.blockedGenresRepository
    private val feedbackRepository = (application as MangoTvApplication).container.feedbackRepository
    private val featureCache = (application as MangoTvApplication).container.featureCacheRepository
    private val plusRepository = (application as MangoTvApplication).container.plusRepository

    // "Picked for you" (Plus preview, signed-in only): the latest engine result and the movies it was computed from.
    private var feedbackEntries: Map<String, FeedbackEntry> = feedbackRepository.entries.value
    private var pickedResult: EngineResult? = null
    private var pickedMovies: List<Content> = emptyList()
    private val pickedInputs = MutableStateFlow<PickedInputs?>(null)
    private var lastPickedKey: String? = null

    // Genres the person has blocked, lower-cased -- read synchronously from applyPreferences, kept current by the collector in init.
    private var blockedGenres: Set<String> = blockedGenreSet(blockedGenresRepository.genres.value)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val savedIds: StateFlow<Set<String>> = myListRepository.items
        .map { items -> items.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Ids My List has watched=true for (see MyListRepository.markWatched/
    // toggleWatched) -- plain field + init{} collector rather than a
    // WhileSubscribed StateFlow
    // like savedIds above, since nothing in the UI subscribes to this one
    // directly: it only needs to be read synchronously from within
    // applyPreferences() below, and WhileSubscribed would never start
    // collecting without a UI subscriber.
    private var watchedIds: Set<String> = emptySet()

    // Raw fetch results, cached here so a preferences-only change (row
    // order/hidden state from Settings > Home Rows) can re-apply cheaply
    // without re-hitting the network -- same "cheap in-memory re-sort of
    // already-fetched rows" principle Home Rows' own drag-reorder already
    // relies on, applied here to preference changes instead of drag events.
    // No separate raw hero list: hero is always derived from whichever
    // section ends up first after preferences are applied (see
    // applyPreferences) rather than fetched independently.
    private var rawSections: List<HomeSection> = emptyList()
    // Not subject to Home Rows' order/hidden-state prefs (those apply to
    // addon-supplied catalog rows) -- always shown first when non-empty,
    // omitted entirely when empty rather than rendering an empty row.
    private var continueWatchingSection: HomeSection? = null
    private var lastFetchFailed = false
    private var hasFetchedOnce = false

    // The hero's random 10, locked in the first time applyPreferences() runs
    // against real live-fetched data (see its own comment for why not
    // during the cache-paint phase) and reused on every later call instead
    // of reshuffling -- "randomized once per app boot", not once per batch
    // or preference change. Null again is only reachable via a fresh
    // ViewModel instance, i.e. an actual new boot.
    private var randomHeroPool: List<Content>? = null

    // The hero chosen for the NEXT launch: drawn once per session from the live catalogue, saved in the cache, and its
    // pictures downloaded in the background. The next boot then shows these titles with their pictures already on disk,
    // instead of drawing a fresh set only after the network answers and then waiting for their pictures too.
    private var catalogItems: List<Content> = emptyList()

    // True once cold-boot cache has painted Home but before the first real
    // (network) fetch has settled. Used two ways below: (1) an empty
    // provider list during this window means "addon restore hasn't
    // registered anything yet", not "user genuinely has no addons", so it
    // shouldn't wipe a perfectly good cached screen back to empty; (2) a
    // total fetch failure during this window (e.g. no network on this
    // launch) leaves the stale cache up rather than replacing it with a
    // hard error -- still-stale content beats no content, and the next
    // successful fetch (this session or next cold boot) replaces it.
    //
    // Known trade-off: there's no reliable signal here to tell "restore
    // hasn't registered anything yet" apart from "the user really did just
    // remove their last addon" -- both look identical (one empty-list
    // emission, ProviderRegistry never re-emits an unchanged value) from
    // this ViewModel's perspective. Cold-booting right after removing every
    // addon can briefly show stale cached rows instead of the Empty state
    // until providers changes again. Accepted as rare/self-correcting
    // rather than adding cross-repository "restore settled" signaling for
    // it.
    private var showingCacheOnly = false

    fun toggleMyList(content: Content) {
        // Saving a title needs an account: a guest is asked to sign in instead.
        guestGate.requireAccount { viewModelScope.launch { myListRepository.toggle(content) } }
    }

    init {
        viewModelScope.launch {
            // Paints instantly from whatever the last successful live fetch
            // produced, before the real network fetch even starts -- see
            // HomeCacheRepository's own doc. This is deliberately awaited
            // (not just launched) before starting the providers collector
            // below: ProviderRegistry starts empty and only fills in once
            // addon restore finishes elsewhere, and that collector's very
            // first (possibly still-empty) emission runs fully
            // synchronously up to its own next suspension point. Launching
            // both at once let that first emission's synchronous branch
            // race ahead of this coroutine's disk read and mark
            // hasFetchedOnce = true before the cache ever got a chance --
            // silently defeating caching on exactly the cold-boot case it
            // exists for. Awaiting first removes the race instead of hoping
            // timing favors the cache.
            homeCacheRepository.read()?.let { (cachedHero, sections) ->
                // The hero drawn (and pre-downloaded) last session is the one shown now and kept when live data
                // arrives, so the first slide doesn't change under the viewer a few seconds in.
                if (cachedHero.isNotEmpty()) randomHeroPool = cachedHero
                rawSections = sections
                hasFetchedOnce = true
                showingCacheOnly = true
                applyPreferences(homeRowPreferences.preferences.value)
            }
            // Network fetch is keyed ONLY on the provider list (an addon
            // being installed, removed, enabled or disabled) -- NOT on
            // preferences. These two used to be combined into one trigger,
            // which meant the (independently-resolving) preferences
            // DataStore read settling shortly after providers did on cold
            // boot fired a second full network re-fetch, doubling
            // perceived load time for no reason.
            launch {
                ProviderRegistry.providers.collect { providers -> fetch(providers) }
            }
        }
        // Preference changes just re-apply the already-fetched raw data.
        viewModelScope.launch {
            homeRowPreferences.preferences.collect { prefs -> applyPreferences(prefs) }
        }
        // Continue Watching (Milestone 8) is its own independent trigger,
        // same reasoning as preferences above: re-applying is a cheap
        // local re-combine, never a network re-fetch of the catalog rows.
        viewModelScope.launch {
            continueWatchingRepository.items.collect { entries ->
                continueWatchingSection = entries.toHomeSectionOrNull()
                applyPreferences(homeRowPreferences.preferences.value)
            }
        }
        // Like / Not for me re-applies at once (a "Not for me" title leaves the row immediately) and, via the key, recomputes.
        viewModelScope.launch {
            feedbackRepository.entries.collect { entries ->
                feedbackEntries = entries
                applyPreferences(homeRowPreferences.preferences.value)
            }
        }
        viewModelScope.launch { runPickedPipeline() }
        // Plus switching on (or off) shows or hides the row.
        viewModelScope.launch {
            plusRepository.status.collect { applyPreferences(homeRowPreferences.preferences.value) }
        }
        // Signing in turns "Picked for you" on (a guest has nothing to learn from), signing out turns it off.
        viewModelScope.launch {
            guestGate.isGuest.collect { applyPreferences(homeRowPreferences.preferences.value) }
        }
        // Blocking or unblocking a genre re-applies the already-fetched rows, no network re-fetch.
        viewModelScope.launch {
            blockedGenresRepository.genres.collect { genres ->
                blockedGenres = blockedGenreSet(genres)
                applyPreferences(homeRowPreferences.preferences.value)
            }
        }
        // Drives the watched tick on every row's ContentCard (not just My
        // List's own screen) -- re-applies whenever a title crosses the
        // completion threshold (or a watched title is removed from My List)
        // while Home is alive, same cheap local re-combine as above.
        viewModelScope.launch {
            myListRepository.items.collect { items ->
                watchedIds = items.filter { it.watched }.map { it.id }.toSet()
                applyPreferences(homeRowPreferences.preferences.value)
            }
        }
    }

    fun load() {
        viewModelScope.launch { fetch(ProviderRegistry.activeProviders()) }
    }

    private suspend fun fetch(providers: List<CatalogProvider>) {
        if (providers.isEmpty()) {
            if (showingCacheOnly) return
            rawSections = emptyList()
            lastFetchFailed = false
            hasFetchedOnce = true
            _uiState.value = HomeUiState.Empty
            return
        }

        // Keep showing cached content while this fetch is in flight rather
        // than flashing back to the loading skeleton -- that would defeat
        // the entire point of painting from cache first. A cache-less cold
        // boot (nothing to show yet) behaves exactly as before.
        if (!showingCacheOnly) _uiState.value = HomeUiState.Loading

        val wasShowingCacheOnly = showingCacheOnly
        val sections = mutableListOf<HomeSection>()
        var anyProviderFailed = false

        // Every provider is collected concurrently, and each provider's own
        // rows arrive in batches (see StremioAddonProvider.buildSectionsFlow)
        // rather than all at once -- every batch, from any provider, is
        // published immediately instead of waiting for the entire fetch (up
        // to ~30 rows per provider) to finish. This is the actual "rows
        // appear progressively" behavior; the collect{} block below is where
        // it happens, not a separate pass over a final combined list.
        coroutineScope {
            providers.forEach { provider ->
                launch {
                    runCatching {
                        provider.getHomeSections().collect { batch ->
                            sections += batch
                            rawSections = sections.toList()
                            hasFetchedOnce = true
                            showingCacheOnly = false
                            // Hero is derived inside applyPreferences from
                            // whichever section ends up first there, not
                            // tracked separately here -- see its own doc.
                            applyPreferences(homeRowPreferences.preferences.value)
                                        }
                    }.onFailure { anyProviderFailed = true }
                }
            }
        }

        lastFetchFailed = anyProviderFailed

        if (sections.isEmpty()) {
            // Nothing arrived from any provider. If cache was showing and
            // this is a total failure, leave the stale cache up instead of
            // replacing it with a hard error -- showingCacheOnly was never
            // touched above (the collect{} block that flips it never ran),
            // so it's still true here exactly when that applies.
            if (anyProviderFailed && wasShowingCacheOnly) return
            hasFetchedOnce = true
            showingCacheOnly = false
            applyPreferences(homeRowPreferences.preferences.value)
        }

        if (sections.isNotEmpty()) {
            // Reuses the hero applyPreferences just derived and published
            // above rather than recomputing it, so what's cached always
            // matches what was actually shown.
            val nextHero = catalogItems.shuffled().take(HERO_POOL_SIZE)
                .ifEmpty { (_uiState.value as? HomeUiState.Success)?.heroItems ?: emptyList() }
            homeCacheRepository.write(nextHero, sections)
            // Download their pictures now, quietly, so next launch's hero is already on disk.
            prefetchHeroImages(nextHero)
        }
    }

    private fun prefetchHeroImages(items: List<Content>) {
        val context = getApplication<Application>()
        val loader = context.imageLoader
        heroImages(items).forEach { image ->
            loader.enqueue(ImageRequest.Builder(context).data(image.url).bitmapConfig(Bitmap.Config.RGB_565).build())
        }
    }

    private fun applyPreferences(rowPreferences: HomeRowPreferences) {
        if (!hasFetchedOnce) return

        // Each title shows in only one row (the first one displayed that holds it). Done after hidden rows are
        // removed so a hidden row never uses up a title, and before the hero pool is drawn so the hero follows suit.
        val visibleSections = dedupeSections(
            rowPreferences.applyOrder(rawSections.withoutBlocked(blockedGenres)).filterNot { it.id in rowPreferences.hiddenRowIds }
        ).map { it.withWatchedFlags() }
        // A title that already sits in a catalogue row is not repeated under Continue Watching.
        val continueWatching = continueWatchingSection?.let { withoutShownTitles(it, visibleSections) }
        updatePickedInputs()
        val picked = if (pickedAvailable()) pickedSection(pickedResult, pickedMovies.withoutBlocked(blockedGenres), feedbackEntries) else null
        val sections = listOfNotNull(continueWatching?.withWatchedFlags()) + listOfNotNull(picked?.withWatchedFlags()) + visibleSections

        // HERO_POOL_SIZE random titles drawn from every visible row (not
        // just the first one), respecting manual reordering and hidden rows
        // the same way the row list itself does. HeroSection (see its own
        // LaunchedEffect) rotates through whatever list it's given, so this
        // is the pool it rotates within, not a fixed set shown at once.
        // Deliberately drawn from visibleSections, not the Continue
        // Watching row prepended below -- the hero stays tied to the
        // addon-driven catalog even when Continue Watching is present.
        //
        // Shuffling an already-fetched, in-memory list of a few hundred
        // items at most costs nothing beyond the network fetch that already
        // happened -- this can't be what makes Home feel slow.
        //
        // Only locked into randomHeroPool once real live data has arrived
        // (showingCacheOnly false): applyPreferences() also runs once
        // during the transient cold-boot cache-paint, and locking in a
        // selection from that stale, about-to-be-replaced data would freeze
        // the "random 10" a step too early, before the live fetch this
        // session actually settles.
        catalogItems = visibleSections.flatMap { it.items }.distinctBy { it.id }
        val hero = if (showingCacheOnly) {
            // A remembered hero (from last session) wins; otherwise a stand-in draw that isn't locked in.
            randomHeroPool?.withoutBlocked(blockedGenres) ?: catalogItems.shuffled().take(HERO_POOL_SIZE)
        } else {
            if (randomHeroPool == null) {
                randomHeroPool = catalogItems.shuffled().take(HERO_POOL_SIZE)
            }
            randomHeroPool.orEmpty().withoutBlocked(blockedGenres)
        }

        _uiState.value = when {
            hero.isNotEmpty() || sections.isNotEmpty() -> HomeUiState.Success(hero, sections)
            lastFetchFailed -> HomeUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
            else -> HomeUiState.Empty
        }
    }

    /** "Picked for you" is an ArcTV Plus feature (whether this account has Plus comes from the backend) and needs an account: a guest has no feedback or history to learn from. */
    private fun pickedAvailable(): Boolean = plusRepository.status.value.active && !guestGate.isGuest.value

    /** Publishes what a "Picked for you" run would be computed from; unchanged inputs (same key) do nothing. */
    private fun updatePickedInputs() {
        if (!pickedAvailable()) return
        // Every movie the addons listed on Home (hidden rows included) is a candidate.
        val movies = rawSections.withoutBlocked(blockedGenres).flatMap { it.items }.filter { it.type == ContentType.MOVIE }.distinctBy { it.id }
        if (movies.isEmpty()) return
        val list = myListRepository.items.value
        val interactions = collectInteractions(interactionInputs(list, feedbackEntries))
        val excludeIds = excludedFromPicks(list, feedbackEntries, continueWatchingRepository.items.value)
        val key = movies.map { it.id }.sorted().joinToString(",") + "#" + signatureOf(interactions) + "#" + excludeIds.sorted().joinToString(",")
        if (key == lastPickedKey) return
        lastPickedKey = key
        val refs = HashMap<String, MovieRef>()
        for (item in list) refs[item.id] = MovieRef(item.id, item.providerId)
        for ((id, entry) in feedbackEntries) refs.putIfAbsent(id, MovieRef(id, entry.providerId))
        pickedInputs.value = PickedInputs(key, movies, interactions, excludeIds, refs)
    }

    @OptIn(FlowPreview::class)
    private suspend fun runPickedPipeline() {
        pickedInputs.filterNotNull().debounce(PICKED_DEBOUNCE_MS).collectLatest { inputs ->
            val providers = ProviderRegistry.activeProviders()
            val result = runCatching {
                recommend(
                    EngineInput(
                        interactions = inputs.interactions,
                        excludeIds = inputs.excludeIds,
                        pool = inputs.movies.map { it.toCandidate() },
                        interactionRefs = inputs.refs,
                        loadFeatures = { refs, limit -> featureCache.load(refs, limit, fetchOne = { ref -> fetchMovieFeatures(providers, ref) }) }
                    )
                )
            }.getOrNull() ?: return@collectLatest
            pickedResult = result
            pickedMovies = inputs.movies
            applyPreferences(homeRowPreferences.preferences.value)
        }
    }

    /** The YouTube id of [content]'s trailer for the hero's Trailer button, or null when there is none (or it can't be looked up). */
    suspend fun findTrailer(content: Content): String? =
        trailerRepository.findTrailer(content.title, content.year, content.type)

    private fun List<ContinueWatchingEntry>.toHomeSectionOrNull(): HomeSection? {
        if (isEmpty()) return null
        return HomeSection(
            id = CONTINUE_WATCHING_ROW_ID,
            title = "Continue Watching",
            items = map { it.toContent() },
            style = RowStyle.CONTINUE_WATCHING
        )
    }

    // Always re-derived from the pristine, never-stamped rawSections/
    // continueWatchingSection (never from an already-stamped HomeUiState) --
    // that way a title removed from My List after being watched correctly
    // loses its tick on the next re-apply instead of staying stuck true.
    private fun HomeSection.withWatchedFlags(): HomeSection = copy(items = items.map { it.withWatchedFlag() })

    private fun Content.withWatchedFlag(): Content = if (id in watchedIds) copy(watched = true) else this

    private fun ContinueWatchingEntry.toContent(): Content = Content(
        id = contentId,
        type = contentType,
        title = title,
        description = "",
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        providerId = providerId,
        watchProgress = WatchProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle
        )
    )

    companion object {
        private const val CONTINUE_WATCHING_ROW_ID = "continue_watching"
    }
}
