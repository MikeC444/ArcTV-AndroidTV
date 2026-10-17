package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.HomeRowPreferences
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.provider.inUserOrder
import com.mangotv.app.data.provider.reorderWithin
import com.mangotv.app.data.provider.orderServices
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/** One installed addon's rows (Cinemeta's genres, say); an addon that gives no rows has none of these, and so no page. */
data class AddonRows(val providerId: String, val name: String, val rows: List<HomeSection>)

sealed interface HomeRowsUiState {
    data object Loading : HomeRowsUiState
    data object NoAddons : HomeRowsUiState
    /** Every installed addon that has rows, in the order the addons are installed: each gets its own page in Settings > Home Rows. */
    data class Loaded(val addons: List<AddonRows>) : HomeRowsUiState {
        val rows: List<HomeSection> get() = addons.flatMap { it.rows }
    }
}

/**
 * Backs Settings > Home Rows. Re-fetches every installed addon's rows the
 * same way HomeViewModel does (there's no shared cache between them) so the
 * list here matches what Home would show, titles included — this only runs
 * when the addon set changes, not on every visibility toggle or reorder, so
 * dragging a row doesn't wait on a network round-trip per step. Manual
 * ordering and hidden state are applied to [uiState]'s raw rows separately
 * (see [preferences]) as a cheap in-memory re-sort instead.
 *
 * Every provider's fetch runs concurrently (same reasoning as
 * HomeViewModel's own fetch()), not one after another -- with N addons
 * installed, awaiting them sequentially meant this screen's total wait was
 * the SUM of every addon's own fetch time instead of just the slowest one.
 * Unlike Home, this screen has no progressive reveal to preserve (it only
 * ever wants the final, complete list), so a plain concurrent await is
 * enough here without needing Home's own per-batch collect().
 */
class HomeRowsViewModel(application: Application) : AndroidViewModel(application) {

    private val homeRowPreferences = (application as MangoTvApplication).container.homeRowPreferencesRepository

    val preferences: StateFlow<HomeRowPreferences> = homeRowPreferences.preferences

    /** The streaming services (Netflix, Disney+ ...) the installed addons have catalogues for, in the person's own order: Home's tiles, switched and ordered here. */
    val services: StateFlow<List<String>> = combine(ProviderRegistry.providers, homeRowPreferences.preferences) { providers, prefs ->
        inUserOrder(orderServices(providers.flatMap { it.streamingServices() }), prefs.order)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow<HomeRowsUiState>(HomeRowsUiState.Loading)
    val uiState: StateFlow<HomeRowsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> loadRows(providers) }
        }
    }

    private suspend fun loadRows(providers: List<CatalogProvider>) {
        if (providers.isEmpty()) {
            _uiState.value = HomeRowsUiState.NoAddons
            return
        }
        _uiState.value = HomeRowsUiState.Loading
        // This screen just wants the final, complete row list (there's no
        // progressive reveal here) -- collecting each provider's
        // getHomeSections() batches to completion and flattening restores
        // that, same as before. What's new is `async` per provider instead
        // of a sequential loop, so every provider's own (up to ~30 request)
        // fetch overlaps instead of stacking up back to back.
        val addons = coroutineScope {
            providers.map { provider ->
                async {
                    val rows = runCatching { provider.getHomeSections().toList().flatten() }.getOrDefault(emptyList())
                    AddonRows(provider.id, provider.name, rows)
                }
            }.awaitAll()
        }.filter { it.rows.isNotEmpty() }
        _uiState.value = HomeRowsUiState.Loaded(addons)
    }

    fun setRowVisible(rowId: String, visible: Boolean) {
        viewModelScope.launch { homeRowPreferences.setRowHidden(rowId, hidden = !visible) }
    }

    /**
     * Moves one row [delta] places among [subset] (the rows the current page shows, in their order); [allOrdered] is every row's id in the order Home uses,
     * so the other addons' rows keep their places.
     */
    fun moveRowWithin(allOrdered: List<String>, subset: List<String>, rowId: String, delta: Int) {
        val next = reorderWithin(allOrdered, subset, rowId, delta) ?: return
        viewModelScope.launch { homeRowPreferences.setRowOrder(next) }
    }

    fun setServiceVisible(service: String, visible: Boolean) {
        viewModelScope.launch { homeRowPreferences.setServiceVisible(service, visible) }
    }

    /** Moves one service's tile [delta] places among [on] (the services shown, in their order); [all] is every service in the person's order. */
    fun moveServiceWithin(all: List<String>, on: List<String>, service: String, delta: Int) {
        val next = reorderWithin(all, on, service, delta) ?: return
        viewModelScope.launch { homeRowPreferences.setServiceOrder(next) }
    }
}
