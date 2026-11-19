package com.aprax.modstore.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.CatalogUiState
import com.aprax.modstore.core.ui.PagingState
import com.aprax.modstore.core.ui.SourceLoadReport
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SortOrder
import com.aprax.modstore.domain.repository.SearchHistoryRepository
import com.aprax.modstore.domain.repository.SettingsRepository
import com.aprax.modstore.domain.usecase.DeduplicateListingsUseCase
import com.aprax.modstore.domain.usecase.RecordSearchUseCase
import com.aprax.modstore.domain.usecase.SearchCatalogUseCase
import com.aprax.modstore.domain.usecase.SearchQueryRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

const val SEARCH_PAGE_SIZE = 20

data class SearchUiState(
    val query: String = "",
    val catalog: CatalogUiState = CatalogUiState(),
    val recentSearches: List<String> = emptyList(),
    val active: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCatalog: SearchCatalogUseCase,
    private val recordSearch: RecordSearchUseCase,
    private val deduplicate: DeduplicateListingsUseCase,
    private val historyRepository: SearchHistoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var currentQuery = ""

    init {
        viewModelScope.launch {
            historyRepository.recentSearches(limit = 10).collect { terms ->
                _uiState.value = _uiState.value.copy(recentSearches = terms)
            }
        }
    }

    fun onQueryChange(raw: String) {
        _uiState.value = _uiState.value.copy(query = raw)
        val trimmed = raw.trim()
        if (!SearchQueryRules.isSearchable(trimmed)) {
            // Too short to search yet; clear the results so stale matches are not shown.
            if (currentQuery.isNotBlank()) {
                currentQuery = ""
                _uiState.value = _uiState.value.copy(
                    catalog = CatalogUiState(),
                    active = false,
                )
            }
            return
        }
        if (trimmed == currentQuery) return
        runSearch(trimmed, reset = true)
    }

    fun submit() {
        val raw = _uiState.value.query
        if (!SearchQueryRules.isSearchable(raw)) return
        runSearch(SearchQueryRules.normalise(raw), reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.catalog.paging.canLoadMore) return
        runSearch(currentQuery, reset = false)
    }

    fun retry() {
        if (currentQuery.isBlank()) return
        runSearch(currentQuery, reset = true)
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clear() }
    }

    fun removeHistoryTerm(term: String) {
        viewModelScope.launch { historyRepository.remove(term) }
    }

    private fun runSearch(raw: String, reset: Boolean) {
        val query = SearchQueryRules.normalise(raw)
        currentQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            recordSearch(query)
            val settings = settingsRepository.settings.first()
            val page = if (reset) 1 else _uiState.value.catalog.paging.page + 1

            _uiState.value = if (reset) {
                _uiState.value.copy(
                    active = true,
                    catalog = CatalogUiState(state = UiState.Loading),
                )
            } else {
                _uiState.value.copy(
                    catalog = _uiState.value.catalog.copy(
                        paging = _uiState.value.catalog.paging.copy(isLoadingMore = true),
                    ),
                )
            }

            val result = searchCatalog(
                query = query,
                filter = settings.sourceFilter,
                sort = SortOrder.RELEVANCE,
                page = page,
                pageSize = SEARCH_PAGE_SIZE,
            )
            applyResult(
                query = query,
                result = result,
                page = page,
                reset = reset,
            )
        }
    }

    private fun applyResult(
        query: String,
        result: com.aprax.modstore.domain.model.Aggregated<AppListing>,
        page: Int,
        reset: Boolean,
    ) {
        if (query != currentQuery) return
        val current = _uiState.value
        val items = result.items
        val deduped = deduplicate(items)
        val combined = if (reset) deduped else current.catalog.listings + deduped
        // An Aggregated result does not carry pagination, so "more exists" is inferred from
        // a full page of items — the same convention the sources use.
        val hasMore = items.size >= SEARCH_PAGE_SIZE

        val catalogState: UiState<List<AppListing>> =
            if (reset && items.isEmpty() && result.failures.isNotEmpty()) {
                UiState.Failed(result.failures.values.first())
            } else {
                UiState.Ready(combined)
            }

        _uiState.value = current.copy(
            catalog = current.catalog.copy(
                state = catalogState,
                report = SourceLoadReport(
                    succeeded = result.succeeded,
                    failed = result.failures,
                ),
                paging = PagingState(page = page, hasMore = hasMore),
            ),
        )
    }
}
