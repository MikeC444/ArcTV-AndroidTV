package com.mangotv.app.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.blockedGenreSet
import com.mangotv.app.data.provider.withoutBlocked
import com.mangotv.app.data.provider.withoutBlockedNames
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.model.HomeSection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs Movies and TV Shows: loops every installed provider's
 * getSectionsByType(type), same reactive-to-ProviderRegistry pattern as
 * HomeViewModel. Unlike Home, this deliberately shows no genre breakdown --
 * every provider's base + genre rows are flattened into one deduplicated,
 * shuffled row, so genres never touch the user's saved Home Rows state and
 * a hidden Home row still shows up here. loadMore() extends that same row
 * with additional pages as the user scrolls, so it doesn't dead-end after
 * one base-catalog page's worth of items.
 *
 * The "All genres" drop-down (as on the web) narrows that to one genre of
 * this type: [selectGenre] reloads with it, and paging keeps using it. A
 * chosen genre keeps the providers' own (popularity) order instead of being
 * shuffled, since the point of picking one is to look through it.
 */
open class TypeBrowseViewModel(application: Application, private val type: ContentType) : AndroidViewModel(application) {

    private val myListRepository = (application as MangoTvApplication).container.myListRepository
    private val blockedGenresRepository = (application as MangoTvApplication).container.blockedGenresRepository

    // Lower-cased blocked genres; titles in them are left out of the grid.
    private var blocked: Set<String> = blockedGenreSet(blockedGenresRepository.genres.value)

    private val _uiState = MutableStateFlow<RowsBrowseUiState>(RowsBrowseUiState.Loading)
    val uiState: StateFlow<RowsBrowseUiState> = _uiState.asStateFlow()

    /** The genres the drop-down offers (empty hides it), and the one chosen (null is "All genres"). */
    private val bundledGenres: List<String> = bundledGenreOptions(application.assets, type)
    val genreOptions: StateFlow<List<String>> = blockedGenresRepository.genres
        .map { genres -> bundledGenres.withoutBlockedNames(blockedGenreSet(genres)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, bundledGenres.withoutBlockedNames(blocked))

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()

    private val allItems = mutableListOf<Content>()
    private val seenIds = mutableSetOf<String>()
    private var providersSnapshot: List<CatalogProvider> = emptyList()
    private var nextPage = 1
    private var hasMore = true
    private var isLoadingMore = false

    // Ids My List has marked watched -- drives the poster tick on this
    // screen's cards too, not just My List's own. Plain field + collector
    // (not a StateFlow) since it only needs to feed currentSection() below.
    private var watchedIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> load(providers) }
        }
        // Blocking or unblocking a genre re-filters the already-loaded grid, no re-fetch.
        viewModelScope.launch {
            blockedGenresRepository.genres.collect { genres ->
                blocked = blockedGenreSet(genres)
                if (_uiState.value is RowsBrowseUiState.Loaded && allItems.isNotEmpty()) {
                    _uiState.value = RowsBrowseUiState.Loaded(listOf(currentSection()))
                }
            }
        }
        // Re-publishes the already-loaded list whenever watched status
        // changes, so a title crossing the completion threshold (or being
        // removed from My List) ticks/unticks immediately even while this
        // screen just sits on the back stack rather than actively loading.
        viewModelScope.launch {
            myListRepository.items.collect { items ->
                watchedIds = items.filter { it.watched }.map { it.id }.toSet()
                if (_uiState.value is RowsBrowseUiState.Loaded && allItems.isNotEmpty()) {
                    _uiState.value = RowsBrowseUiState.Loaded(listOf(currentSection()))
                }
            }
        }
    }

    private fun Content.withWatchedFlag(): Content = if (id in watchedIds) copy(watched = true) else this

    private fun currentSection(): HomeSection =
        HomeSection(id = "flat_$type", title = "", items = allItems.withoutBlocked(blocked).map { it.withWatchedFlag() })

    fun load() {
        viewModelScope.launch { load(ProviderRegistry.activeProviders()) }
    }

    /** Narrows Movies / TV Shows to [genre] (null for all genres). Keeps the current grid on screen until the new one arrives, so the drop-down never loses focus. */
    fun selectGenre(genre: String?) {
        if (genre == _selectedGenre.value) return
        _selectedGenre.value = genre
        viewModelScope.launch { load(ProviderRegistry.activeProviders(), showLoading = false) }
    }

    private suspend fun load(providers: List<CatalogProvider>, showLoading: Boolean = true) {
        if (showLoading) _uiState.value = RowsBrowseUiState.Loading
        val genre = _selectedGenre.value
        providersSnapshot = providers
        nextPage = 1
        hasMore = true
        isLoadingMore = false
        allItems.clear()
        seenIds.clear()

        if (providers.isEmpty()) {
            _uiState.value = RowsBrowseUiState.Loaded(emptyList())
            return
        }

        val results = coroutineScope {
            providers.map { provider -> async { runCatching { provider.getSectionsByType(type, genre) } } }.awaitAll()
        }
        // The person changed genre again while this was loading: that newer load owns the screen.
        if (genre != _selectedGenre.value) return
        val sections = mutableListOf<HomeSection>()
        var anyProviderFailed = false
        results.forEach { result ->
            result.onSuccess { sections += it }.onFailure { anyProviderFailed = true }
        }

        // Flatten every provider's base + genre rows into one deduplicated,
        // shuffled row -- no genre breakdown here, and a fresh shuffle each
        // time this loads so the order varies on revisit.
        val items = sections.flatMap { it.items }.distinctBy { it.id }.let { if (genre == null) it.shuffled() else it }
        allItems += items
        seenIds += items.map { it.id }

        _uiState.value = when {
            allItems.isNotEmpty() -> RowsBrowseUiState.Loaded(listOf(currentSection()))
            anyProviderFailed -> RowsBrowseUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
            else -> RowsBrowseUiState.Loaded(emptyList())
        }
    }

    // Called as the grid scrolls near the bottom (see RowsBrowseScreen.kt) --
    // fetches the next page from every provider and appends it to the same
    // row, so scrolling feels endless instead of dead-ending after one
    // base-catalog page. Only the newly-fetched batch is shuffled, not the
    // whole accumulated list -- re-shuffling everything on every page would
    // visibly reorder rows the user has already scrolled past.
    fun loadMore() {
        if (isLoadingMore || !hasMore || providersSnapshot.isEmpty()) return
        isLoadingMore = true
        viewModelScope.launch {
            val page = nextPage
            val genre = _selectedGenre.value
            val results = coroutineScope {
                providersSnapshot.map { provider -> async { runCatching { provider.getMoreItemsByType(type, page, genre) } } }.awaitAll()
            }
            // The genre changed while this page was loading: it belongs to the old list, so drop it.
            if (genre != _selectedGenre.value) {
                isLoadingMore = false
                return@launch
            }
            val newItems = results.flatMap { it.getOrElse { emptyList() } }
                .filterNot { it.id in seenIds }
                .distinctBy { it.id }
                .let { if (genre == null) it.shuffled() else it }

            if (newItems.isEmpty()) {
                hasMore = false
            } else {
                nextPage++
                allItems += newItems
                seenIds += newItems.map { it.id }
                _uiState.value = RowsBrowseUiState.Loaded(listOf(currentSection()))
            }
            isLoadingMore = false
        }
    }
}

class MoviesViewModel(application: Application) : TypeBrowseViewModel(application, ContentType.MOVIE)

class TvShowsViewModel(application: Application) : TypeBrowseViewModel(application, ContentType.TV_SHOW)
