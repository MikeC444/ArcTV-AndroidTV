package com.mangotv.app.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.provider.blockedGenreSet
import com.mangotv.app.data.provider.loadDetails
import com.mangotv.app.data.streaming.TOP_TEN_COUNT
import com.mangotv.app.data.streaming.TOP_TEN_POOL
import com.mangotv.app.data.streaming.TOP_TEN_SERVICE
import com.mangotv.app.data.streaming.TopTenState
import com.mangotv.app.data.streaming.pickUnblocked
import com.mangotv.app.data.streaming.rankedPool
import com.mangotv.app.data.streaming.regionOf
import com.mangotv.app.data.streaming.topTen
import com.mangotv.app.data.provider.withoutBlocked
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Backs a streaming service's page (Netflix, Disney+ ...), opened from a Home tile: that service's movies and series from every installed addon that
 * has a catalogue for it, interleaved and de-duplicated (as the web app's `StreamingServiceScreen`), with the following pages read as the grid scrolls
 * (only an addon whose catalogue declares paging has any). Same shape as [TypeBrowseViewModel], whose Anime page is the pattern.
 */
class StreamingViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    /** The service this page is for (`netflix`, `disney` ...). */
    val service: String = URLDecoder.decode(savedStateHandle.get<String>("service").orEmpty(), "UTF-8")

    private val myListRepository = (application as MangoTvApplication).container.myListRepository
    private val blockedGenresRepository = (application as MangoTvApplication).container.blockedGenresRepository
    private val streamingTitlesRepository = (application as MangoTvApplication).container.streamingTitlesRepository

    private var blocked: Set<String> = blockedGenreSet(blockedGenresRepository.effectiveGenres.value)

    private val _uiState = MutableStateFlow<RowsBrowseUiState>(RowsBrowseUiState.Loading)
    val uiState: StateFlow<RowsBrowseUiState> = _uiState.asStateFlow()

    private val allItems = mutableListOf<Content>()
    private val seenIds = mutableSetOf<String>()
    private var providersSnapshot: List<CatalogProvider> = emptyList()
    private var nextPage = 1
    // The next page of TMDB titles to read once the addons have no more (see loadMore)
    private var nextTmdbPage = 1
    private var hasMore = true
    private var isLoadingMore = false

    /** The page opens with a Top 10 row, wide pictures and the service's red N (Netflix's page, as on the web app); the other services keep the poster grid. */
    val hasTopTen: Boolean = service == TOP_TEN_SERVICE

    private val _topTen = MutableStateFlow<TopTenState>(if (hasTopTen) TopTenState.Loading else TopTenState.None)

    /** The Top 10 row: grey posters while it is on its way, then the ten titles (or nothing when the service has no list). */
    val topTen: StateFlow<TopTenState> = _topTen.asStateFlow()
    private var topTenIds: Set<String> = emptySet()
    private var topTenGen = 0

    private val _moreComing = MutableStateFlow(true)

    /** True while more titles may still arrive by scrolling: the grid shows grey poster shapes after the last title, so the page can be scrolled ahead of what has loaded. */
    val moreComing: StateFlow<Boolean> = _moreComing.asStateFlow()
    private var watchedIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> load(providers) }
        }
        viewModelScope.launch {
            blockedGenresRepository.effectiveGenres.collect { genres ->
                val changed = blockedGenreSet(genres) != blocked
                blocked = blockedGenreSet(genres)
                // the Top 10 leaves out blocked genres too, so it is worked out again for the new set
                if (changed && hasTopTen && providersSnapshot.isNotEmpty()) startTopTen(providersSnapshot)
                if (_uiState.value is RowsBrowseUiState.Loaded && allItems.isNotEmpty()) _uiState.value = RowsBrowseUiState.Loaded(listOf(currentSection()))
            }
        }
        viewModelScope.launch {
            myListRepository.items.collect { items ->
                watchedIds = items.filter { it.watched }.map { it.id }.toSet()
                if (_uiState.value is RowsBrowseUiState.Loaded && allItems.isNotEmpty()) _uiState.value = RowsBrowseUiState.Loaded(listOf(currentSection()))
            }
        }
    }

    private fun Content.withWatchedFlag(): Content = if (id in watchedIds) copy(watched = true) else this

    private fun currentSection(): HomeSection =
        HomeSection(id = "service_$service", title = "", items = allItems.withoutBlocked(blocked).filterNot { it.id in topTenIds }.map { it.withWatchedFlag() })

    /** What the title list shows again once the Top 10 has come (its ten titles are left out of the list under it, so nothing shows twice). */
    private fun refreshList() {
        if (_uiState.value is RowsBrowseUiState.Loaded && allItems.isNotEmpty()) _uiState.value = RowsBrowseUiState.Loaded(listOf(currentSection()))
    }

    /**
     * Works out the Top 10 row: ten titles from TMDB's popularity list for the service in the viewer's country, series and movies in turn (not Netflix's own
     * chart), leaving out titles with a blocked genre (their genres are looked up through the addons, as TMDB sends none). One answer per service, country
     * and set of blocked genres is kept for the session. Starts at once when the page opens, not after the titles. Same as the web app's `TopTenRow.tsx`.
     */
    private fun startTopTen(providers: List<CatalogProvider>): Deferred<Unit> {
        // An older ask is left to finish (a page waiting on it must not be cancelled with it); only the newest one's answer is used.
        val gen = ++topTenGen
        val providerId = providers.firstOrNull { service in it.streamingServices() }?.id
        val key = "$service:${regionOf(Locale.getDefault())}:${blocked.sorted().joinToString(",")}"
        val job = viewModelScope.async {
            val cached = topTenCache[key]
            val ten = cached ?: run {
                if (providerId == null) {
                    emptyList<Content>()
                } else {
                    val (movies, shows) = coroutineScope {
                        val m = async { streamingTitlesRepository.page(service, ContentType.MOVIE, 1, providerId) }
                        val t = async { streamingTitlesRepository.page(service, ContentType.TV_SHOW, 1, providerId) }
                        m.await() to t.await()
                    }
                    val picked = if (blocked.isEmpty()) {
                        topTen(movies.items, shows.items)
                    } else {
                        pickUnblocked(rankedPool(movies.items, shows.items, TOP_TEN_POOL), blocked, { c -> loadDetails(providers, providerId, c.type, c.id)?.genres }, TOP_TEN_COUNT)
                    }
                    if (picked.isNotEmpty()) topTenCache[key] = picked
                    picked
                }
            }
            if (gen != topTenGen) return@async
            topTenIds = ten.map { it.id }.toSet()
            _topTen.value = if (ten.isEmpty()) TopTenState.None else TopTenState.Ready(ten)
            refreshList()
        }
        return job
    }

    fun load() {
        viewModelScope.launch { load(ProviderRegistry.activeProviders()) }
    }

    private suspend fun load(providers: List<CatalogProvider>) {
        _uiState.value = RowsBrowseUiState.Loading
        providersSnapshot = providers
        nextPage = 1
        nextTmdbPage = 1
        hasMore = true
        _moreComing.value = true
        isLoadingMore = false
        allItems.clear()
        seenIds.clear()

        if (providers.isEmpty()) {
            _uiState.value = RowsBrowseUiState.Loaded(emptyList())
            return
        }

        // The Top 10 starts loading now, beside the titles
        val topTenWork = if (hasTopTen) startTopTen(providers) else null

        // Only the addons that have a catalogue for this service are asked.
        val results = coroutineScope {
            providers.filter { service in it.streamingServices() }.map { provider -> async { runCatching { provider.getServiceItems(service) } } }.awaitAll()
        }
        var anyProviderFailed = false
        val items = results.flatMap { result -> result.getOrElse { anyProviderFailed = true; emptyList() } }.distinctBy { it.id }
        allItems += items
        seenIds += items.map { it.id }

        // The titles wait for the Top 10 (up to 6 seconds), so it does not pull ten titles out of the list after the list is on screen
        if (topTenWork != null && allItems.isNotEmpty()) withTimeoutOrNull(TOP_TEN_WAIT_MS) { topTenWork.await() }

        _uiState.value = when {
            allItems.isNotEmpty() -> RowsBrowseUiState.Loaded(listOf(currentSection()))
            anyProviderFailed -> RowsBrowseUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
            else -> RowsBrowseUiState.Loaded(emptyList())
        }
    }

    /** Called as the grid scrolls near the bottom: the next page from every addon that has one, appended to the same list. */
    fun loadMore() {
        if (isLoadingMore || !hasMore || providersSnapshot.isEmpty()) return
        isLoadingMore = true
        viewModelScope.launch {
            val page = nextPage
            val results = coroutineScope {
                providersSnapshot.filter { service in it.streamingServices() }.map { provider -> async { runCatching { provider.getMoreServiceItems(page, service) } } }.awaitAll()
            }
            var newItems = results.flatMap { it.getOrElse { emptyList() } }.filterNot { it.id in seenIds }.distinctBy { it.id }
            var exhausted = false
            if (newItems.isEmpty()) {
                // The addons have no more (Streaming Catalogs lists about 100 a catalogue), so carry on with TMDB's list for the service, a page of
                // movies and of series at a time (as the web app's service page does).
                val providerId = providersSnapshot.firstOrNull { service in it.streamingServices() }?.id
                var tries = 0
                while (tries < 4 && newItems.isEmpty() && providerId != null) {
                    tries++
                    val at = nextTmdbPage
                    val (movies, shows) = coroutineScope {
                        val m = async { streamingTitlesRepository.page(service, ContentType.MOVIE, at, providerId) }
                        val t = async { streamingTitlesRepository.page(service, ContentType.TV_SHOW, at, providerId) }
                        m.await() to t.await()
                    }
                    if (!movies.available && !shows.available) break
                    nextTmdbPage++
                    newItems = interleaveTwo(movies.items, shows.items).filterNot { it.id in seenIds }.distinctBy { it.id }
                    if (at >= maxOf(movies.totalPages, shows.totalPages)) {
                        exhausted = true
                        break
                    }
                }
            }
            if (newItems.isEmpty()) {
                hasMore = false
            } else {
                nextPage++
                allItems += newItems
                seenIds += newItems.map { it.id }
                _uiState.value = RowsBrowseUiState.Loaded(listOf(currentSection()))
                if (exhausted) hasMore = false
            }
            _moreComing.value = hasMore
            isLoadingMore = false
        }
    }

    /** Two lists taken turn about (a movie, a series, a movie ...), as the web app's interleave does for the two kinds. */
    private fun <T> interleaveTwo(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            a.getOrNull(i)?.let(out::add)
            b.getOrNull(i)?.let(out::add)
        }
        return out
    }

    private companion object {
        /** How long the title list waits for the Top 10 before it shows anyway. */
        const val TOP_TEN_WAIT_MS = 6_000L

        /** One answer per service, country and set of blocked genres for the session, so coming back to the page does not ask again. */
        val topTenCache = ConcurrentHashMap<String, List<Content>>()
    }
}
