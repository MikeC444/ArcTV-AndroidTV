package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.plus.PlusStatus
import com.mangotv.app.data.stats.WatchStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data object Error : StatsUiState
    data class Ready(val stats: WatchStats, val truncated: Boolean) : StatsUiState
}

/** Backs Settings > Your stats (an Arc TV Plus feature): reads the watch history once Plus is known to be on, and again on Retry. */
class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MangoTvApplication).container
    val plus: StateFlow<PlusStatus> = container.plusRepository.status

    private val _state = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            plus.map { it.active }.distinctUntilChanged().collect { active -> if (active) load() }
        }
    }

    fun load() {
        _state.value = StatsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val loaded = container.watchStatsRepository.load()
                StatsUiState.Ready(loaded.stats, loaded.truncated)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                StatsUiState.Error
            }
        }
    }
}
