package com.aprax.modstore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.CatalogUiState
import com.aprax.modstore.core.ui.PagingState
import com.aprax.modstore.core.ui.SourceLoadReport
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.Category
import com.aprax.modstore.domain.model.SortOrder
import com.aprax.modstore.domain.repository.CatalogRepository
import com.aprax.modstore.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

const val BROWSE_PAGE_SIZE = 20

data class BrowseUiState(
    val category: Category? = null,
    val catalog: CatalogUiState = CatalogUiState(),
)

/**
 * Category browser backing model.
 *
 * Lives in the app module (not a feature module) because it is the shell's own
 * destination: a full feature module would be disproportionate for one route.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedCategoryKey: String? = null

    /** Called from the nav host when the category changes; reloads only when it does. */
    fun load(category: Category) {
        if (loadedCategoryKey == category.key && _uiState.value.category != null) return
        loadedCategoryKey = category.key
        loadJob?.cancel()

        _uiState.value = BrowseUiState(category = category)
        loadJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.value = _uiState.value.copy(
                catalog = CatalogUiState(state = UiState.Loading),
            )

            val result = catalogRepository.byCategory(
                category = category,
                filter = settings.sourceFilter,
                page = 1,
                pageSize = BROWSE_PAGE_SIZE,
                sort = SortOrder.RECENTLY_UPDATED,
            )

            // An Aggregated result does not carry pagination, so "more exists" is inferred
            // from a full page of items — the same convention the sources use.
            val hasMore = result.items.size >= BROWSE_PAGE_SIZE
            val catalogState: UiState<List<AppListing>> =
                if (result.items.isEmpty() && result.failures.isNotEmpty()) {
                    UiState.Failed(result.failures.values.first())
                } else {
                    UiState.Ready(result.items)
                }

            _uiState.value = _uiState.value.copy(
                catalog = _uiState.value.catalog.copy(
                    state = catalogState,
                    report = SourceLoadReport(
                        succeeded = result.succeeded,
                        failed = result.failures,
                    ),
                    paging = PagingState(page = 1, hasMore = hasMore),
                ),
            )
        }
    }

    fun retry() {
        _uiState.value.category?.let { load(it) }
    }
}
