package com.aprax.modstore.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { FAVOURITES, SAVED }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.FAVOURITES,
    val favourites: UiState<List<AppListing>> = UiState.Loading,
    val saved: UiState<List<AppListing>> = UiState.Loading,
) {
    val current: List<AppListing>
        get() = when (tab) {
            LibraryTab.FAVOURITES -> favourites.dataOrNull ?: emptyList()
            LibraryTab.SAVED -> saved.dataOrNull ?: emptyList()
        }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.favourites().collect { listings ->
                _uiState.value = _uiState.value.copy(
                    favourites = UiState.Ready(listings),
                )
            }
        }
        viewModelScope.launch {
            libraryRepository.savedForLater().collect { listings ->
                _uiState.value = _uiState.value.copy(
                    saved = UiState.Ready(listings),
                )
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun remove(listing: AppListing) {
        viewModelScope.launch {
            when (_uiState.value.tab) {
                LibraryTab.FAVOURITES -> libraryRepository.removeFavourite(listing.id)
                LibraryTab.SAVED -> libraryRepository.toggleSaved(listing)
            }
        }
    }

    fun toggleFavourite(listing: AppListing) {
        viewModelScope.launch { libraryRepository.toggleFavourite(listing) }
    }

    fun toggleSaved(listing: AppListing) {
        viewModelScope.launch { libraryRepository.toggleSaved(listing) }
    }
}
