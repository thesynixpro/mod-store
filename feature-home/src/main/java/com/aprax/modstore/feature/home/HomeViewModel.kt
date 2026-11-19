package com.aprax.modstore.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.SourceLoadReport
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.Category
import com.aprax.modstore.domain.model.CategoryTaxonomy
import com.aprax.modstore.domain.model.foldValue
import com.aprax.modstore.domain.repository.SettingsRepository
import com.aprax.modstore.domain.usecase.GetHomeFeedUseCase
import com.aprax.modstore.domain.usecase.GetHomeFeedUseCase.HomeFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the home screen renders. */
data class HomeUiState(
    val state: UiState<HomeFeed> = UiState.Loading,
) {
    val isLoading: Boolean get() = state.isLoading
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeFeed: GetHomeFeedUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val sections: List<Category> =
        CategoryTaxonomy.homeShortcuts + listOf(CategoryTaxonomy.PERSONALIZATION)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(UiState.Loading)
            val settings = settingsRepository.settings.first()
            val result = getHomeFeed(settings.sourceFilter, sections)
            result.foldValue(
                onSuccess = { feed ->
                    _uiState.value = HomeUiState(
                        state = if (feed.isEmpty) {
                            UiState.Ready(
                                GetHomeFeedUseCase.HomeFeed(
                                    newest = emptyList(),
                                    byCategory = emptyMap(),
                                    succeededSources = feed.succeededSources,
                                    failedSources = feed.failedSources,
                                )
                            )
                        } else {
                            UiState.Ready(feed)
                        }
                    )
                },
                onFailure = { error: AppError ->
                    _uiState.value = HomeUiState(state = UiState.Failed(error))
                },
            )
        }
    }
}
