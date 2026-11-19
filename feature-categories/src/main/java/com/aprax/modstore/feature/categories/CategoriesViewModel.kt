package com.aprax.modstore.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.core.ui.dataOrNull
import com.aprax.modstore.domain.model.Category
import com.aprax.modstore.domain.model.CategoryTaxonomy
import com.aprax.modstore.domain.usecase.GetCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val state: UiState<List<Category>> = UiState.Loading,
) {
    val games: List<Category> get() = state.dataOrNull?.filter { it.isGame } ?: emptyList()
    val apps: List<Category> get() = state.dataOrNull?.filterNot { it.isGame } ?: emptyList()
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val getCategories: GetCategoriesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CategoriesUiState(UiState.Loading)
            val result = getCategories()
            result.fold(
                onSuccess = { categories ->
                    _uiState.value = CategoriesUiState(UiState.Ready(categories))
                },
                onFailure = { _ ->
                    // The taxonomy is static, so a failure here is effectively impossible,
                    // but the UI still handles it rather than crashing.
                    _uiState.value = CategoriesUiState(UiState.Ready(CategoryTaxonomy.all))
                },
            )
        }
    }
}
