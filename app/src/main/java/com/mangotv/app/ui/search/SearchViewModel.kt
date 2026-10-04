package com.mangotv.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.provider.blockedGenreSet
import com.mangotv.app.data.provider.withoutBlocked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    /** [loading] while slower addons are still answering ("Still checking other addons..."). */
    data class Results(val movies: List<Content>, val tvShows: List<Content>, val loading: Boolean = false) : SearchUiState
    data class NoResults(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/** How long Search waits for any one addon; a slow one is left behind instead of holding up everyone else's results. */
const val SEARCH_ADDON_TIMEOUT_MS = 6_000L

/** The pause after the last typed letter before a search starts, and the fewest letters that start one (same as the web app). */
const val SEARCH_TYPING_PAUSE_MS = 250L
const val SEARCH_MIN_LETTERS = 2

/** What the addons have said so far, merged into one list (addon order is stable, so rows don't jump about), split into movies and shows. */
fun mergeSearchAnswers(answers: List<List<Content>?>): Pair<List<Content>, List<Content>> {
    val merged = interleaveAnswers(answers.filterNotNull()).distinctBy { it.id }
    return merged.filter { it.type == ContentType.MOVIE } to merged.filter { it.type == ContentType.TV_SHOW }
}

private fun <T> interleaveAnswers(lists: List<List<T>>): List<T> {
    if (lists.size == 1) return lists[0]
    val result = mutableListOf<T>()
    val maxSize = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until maxSize) {
        for (list in lists) {
            if (i < list.size) result += list[i]
        }
    }
    return result
}

/**
 * Search as you type, like the web app: a short pause after the last letter (from [SEARCH_MIN_LETTERS] letters) starts the search, every
 * addon is asked at once and each answer shows the moment it arrives. Enter on the keyboard searches straight away.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MangoTvApplication).container
    private val myListRepository = container.myListRepository
    private val blockedGenresRepository = container.blockedGenresRepository
    private val searchHistoryRepository = container.searchHistoryRepository
    private var blocked: Set<String> = blockedGenreSet(blockedGenresRepository.effectiveGenres.value)

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** The last few searches (newest first), shown under the bar while nothing is being searched. */
    val recents: StateFlow<List<String>> = searchHistoryRepository.recents

    // Pristine (never-stamped) results from the latest answers -- currentResults() always re-derives from these rather than from whatever's
    // already in _uiState, so a title removed from My List after being watched correctly loses its tick on the next re-publish.
    private var rawMovies: List<Content> = emptyList()
    private var rawTvShows: List<Content> = emptyList()
    private var loading: Boolean = false

    // Ids My List has marked watched -- drives the poster tick on search results too, not just My List's own screen.
    private var watchedIds: Set<String> = emptySet()

    private var searchJob: Job? = null
    private var typingJob: Job? = null
    private val lock = Any()

    init {
        viewModelScope.launch {
            blockedGenresRepository.effectiveGenres.collect { genres ->
                blocked = blockedGenreSet(genres)
                if (_uiState.value is SearchUiState.Results) _uiState.value = currentResults()
            }
        }
        // Re-publishes the last results whenever watched status changes, so a title crossing the completion threshold (or being removed from
        // My List) ticks/unticks immediately even if the user is still looking at old results rather than searching again.
        viewModelScope.launch {
            myListRepository.items.collect { items ->
                watchedIds = items.filter { it.watched }.map { it.id }.toSet()
                if (_uiState.value is SearchUiState.Results) {
                    _uiState.value = currentResults()
                }
            }
        }
    }

    private fun Content.withWatchedFlag(): Content = if (id in watchedIds) copy(watched = true) else this

    private fun currentResults(): SearchUiState.Results =
        SearchUiState.Results(
            rawMovies.withoutBlocked(blocked).map { it.withWatchedFlag() },
            rawTvShows.withoutBlocked(blocked).map { it.withWatchedFlag() },
            loading
        )

    /** The text in the bar changed: search after a short pause (never for a single letter), and go back to the start page when it is emptied. */
    fun onQueryChanged(query: String) {
        typingJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            searchJob?.cancel()
            _uiState.value = SearchUiState.Idle
            return
        }
        if (q.length < SEARCH_MIN_LETTERS) return
        typingJob = viewModelScope.launch {
            delay(SEARCH_TYPING_PAUSE_MS)
            search(q)
        }
    }

    /** Enter on the keyboard (or picking a recent search): search now, and remember it. */
    fun submit(query: String) {
        typingJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) return
        searchHistoryRepository.add(q)
        search(q)
    }

    /** Opening a result counts the search as a real one worth remembering. */
    fun rememberSearch(query: String) = searchHistoryRepository.add(query)

    fun removeRecent(term: String) = searchHistoryRepository.remove(term)

    fun clearRecents() = searchHistoryRepository.clearCurrent()

    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val providers = ProviderRegistry.activeProviders()
            if (providers.isEmpty()) {
                _uiState.value = SearchUiState.NoResults(query)
                return@launch
            }
            // Keep the old results up until the new ones arrive (no flash back to a spinner on every refinement).
            val previous = _uiState.value
            _uiState.value = if (previous is SearchUiState.Results) previous.copy(loading = true) else SearchUiState.Searching

            val answers = arrayOfNulls<List<Content>>(providers.size)
            var pending = providers.size
            var failed = 0

            // Called as each addon (or each of its catalogs) answers; every state change goes through the same lock.
            fun publish() {
                val (movies, shows) = mergeSearchAnswers(answers.toList())
                rawMovies = movies
                rawTvShows = shows
                loading = pending > 0
                _uiState.value = when {
                    movies.isNotEmpty() || shows.isNotEmpty() -> currentResults()
                    pending > 0 -> SearchUiState.Searching
                    failed == providers.size -> SearchUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
                    else -> SearchUiState.NoResults(query)
                }
            }

            coroutineScope {
                providers.forEachIndexed { index, provider ->
                    launch {
                        try {
                            val result = withTimeout(SEARCH_ADDON_TIMEOUT_MS) {
                                provider.search(query) { partial ->
                                    synchronized(lock) {
                                        answers[index] = partial
                                        publish()
                                    }
                                }
                            }
                            synchronized(lock) { answers[index] = result }
                        } catch (e: TimeoutCancellationException) {
                            synchronized(lock) { failed++ }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            synchronized(lock) { failed++ }
                        }
                        synchronized(lock) {
                            pending--
                            publish()
                        }
                    }
                }
            }
        }
    }
}
