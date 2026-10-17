package com.mangotv.app.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.provider.blockedGenreSet
import com.mangotv.app.data.provider.withoutBlocked
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

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

    private var blocked: Set<String> = blockedGenreSet(blockedGenresRepository.effectiveGenres.value)

    private val _uiState = MutableStateFlow<RowsBrowseUiState>(RowsBrowseUiState.Loading)
    val uiState: StateFlow<RowsBrowseUiState> = _uiState.asStateFlow()

    private val allItems = mutableListOf<Content>()
    private val seenIds = mutableSetOf<String>()
    private var providersSnapshot: List<CatalogProvider> = emptyList()
    private var nextPage = 1
    private var hasMore = true
    private var isLoadingMore = false
    private var watchedIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> load(providers) }
        }
        viewModelScope.launch {
            blockedGenresRepository.effectiveGenres.collect { genres ->
                blocked = blockedGenreSet(genres)
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
        HomeSection(id = "service_$service", title = "", items = allItems.withoutBlocked(blocked).map { it.withWatchedFlag() })

    fun load() {
        viewModelScope.launch { load(ProviderRegistry.activeProviders()) }
    }

    private suspend fun load(providers: List<CatalogProvider>) {
        _uiState.value = RowsBrowseUiState.Loading
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

        // Only the addons that have a catalogue for this service are asked.
        val results = coroutineScope {
            providers.filter { service in it.streamingServices() }.map { provider -> async { runCatching { provider.getServiceItems(service) } } }.awaitAll()
        }
        var anyProviderFailed = false
        val items = results.flatMap { result -> result.getOrElse { anyProviderFailed = true; emptyList() } }.distinctBy { it.id }
        allItems += items
        seenIds += items.map { it.id }

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
            val newItems = results.flatMap { it.getOrElse { emptyList() } }.filterNot { it.id in seenIds }.distinctBy { it.id }
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
