package com.example.minedtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minedtv.data.AppServices
import com.example.minedtv.data.search.SearchQuery
import com.example.minedtv.data.search.SearchRepository
import com.example.minedtv.data.search.SearchResponse
import com.example.minedtv.data.search.SearchScope
import com.example.minedtv.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val response: SearchResponse? = null,
    val error: String? = null,
    val recent: List<String> = emptyList()
)

/** Debounced semantic search for one [scope]. Shared by Explore (global) and "Search your clips" (library). */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val scope: SearchScope,
    private val repository: SearchRepository = AppServices.searchRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val queries = MutableStateFlow("")
    private var retryJob: Job? = null

    init {
        viewModelScope.launch {
            queries
                .debounce(350)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { runSearch(it) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        queries.value = query
    }

    /** Called on keyboard "search" or when a suggestion is tapped; remembers the query. */
    fun commit(query: String = _state.value.query) {
        onQueryChange(query)
        val q = query.trim()
        if (q.isNotEmpty()) _state.update { s -> s.copy(recent = (listOf(q) + s.recent.filter { it != q }).take(6)) }
    }

    fun retry() {
        retryJob?.cancel()
        retryJob = viewModelScope.launch { runSearch(_state.value.query.trim()) }
    }

    private suspend fun runSearch(q: String) {
        if (q.isEmpty()) {
            _state.update { it.copy(loading = false, response = null, error = null) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        try {
            val response = repository.search(SearchQuery(q, scope))
            Logger.d(
                "Search [${scope.apiName}] \"$q\" -> ${response.hits.size} clips, ${response.totalMoments} moments " +
                    "(games=${response.interpretation.games}, events=${response.interpretation.events}, ${response.tookMs}ms)"
            )
            _state.update { it.copy(loading = false, response = response) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("Search failed for \"$q\"", e)
            _state.update { it.copy(loading = false, error = e.message ?: "Search failed") }
        }
    }
}
