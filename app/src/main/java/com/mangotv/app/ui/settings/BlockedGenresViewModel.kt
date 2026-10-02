package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BlockedGenresUiState {
    data object Loading : BlockedGenresUiState
    data object NoAddons : BlockedGenresUiState

    /** [genres] is every genre the installed addons offer, plus any already-blocked one no addon lists right now (so it can still be unblocked). */
    data class Loaded(val genres: List<String>) : BlockedGenresUiState
}

/** Backs Settings > Blocked Genres: the genres the installed addons offer, and which of them are blocked (kept on the account). */
class BlockedGenresViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MangoTvApplication).container.blockedGenresRepository

    val blocked: StateFlow<List<String>> = repository.genres

    private val _offered = MutableStateFlow<List<String>?>(null)
    private val _uiState = MutableStateFlow<BlockedGenresUiState>(BlockedGenresUiState.Loading)
    val uiState: StateFlow<BlockedGenresUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> load(providers) }
        }
        viewModelScope.launch {
            repository.genres.collect { publish() }
        }
    }

    private suspend fun load(providers: List<CatalogProvider>) {
        if (providers.isEmpty()) {
            _offered.value = null
            _uiState.value = BlockedGenresUiState.NoAddons
            return
        }
        _uiState.value = BlockedGenresUiState.Loading
        val offered = mutableSetOf<String>()
        for (provider in providers) {
            runCatching { provider.getAvailableGenres() }.onSuccess { offered += it }
        }
        // Year filters some addons declare under the same "genre" extra aren't genres.
        _offered.value = offered.filterNot { it.toIntOrNull()?.let { y -> y in 1900..2100 } == true }
        publish()
    }

    private fun publish() {
        val offered = _offered.value ?: return
        val all = (offered + repository.genres.value).distinctBy { it.trim().lowercase() }.sortedBy { it.lowercase() }
        _uiState.value = BlockedGenresUiState.Loaded(all)
    }

    fun toggle(genre: String) {
        viewModelScope.launch { repository.toggle(genre) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
