package com.aprax.modstore.feature.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.repository.DownloadRepository
import com.aprax.modstore.domain.repository.DownloadState
import com.aprax.modstore.domain.repository.InstallerRepository
import com.aprax.modstore.domain.repository.LibraryRepository
import com.aprax.modstore.domain.usecase.GetListingDetailsUseCase
import com.aprax.modstore.domain.usecase.ToggleFavouriteUseCase
import com.aprax.modstore.domain.usecase.ToggleSavedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val listing: AppListing? = null,
    val state: UiState<AppListing> = UiState.Loading,
    val isFavourite: Boolean = false,
    val isSaved: Boolean = false,
    val download: DownloadState? = null,
    val installedVersionCode: Long? = null,
    val canRequestInstall: Boolean = false,
) {
    val current: AppListing? get() = state.dataOrNull ?: listing
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val getDetails: GetListingDetailsUseCase,
    private val downloadRepository: DownloadRepository,
    private val installerRepository: InstallerRepository,
    private val libraryRepository: LibraryRepository,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val toggleSaved: ToggleSavedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var initialised = false

    /** Call from the nav host with the tapped listing so details load against it. */
    fun load(listing: AppListing) {
        if (initialised) return
        initialised = true

        viewModelScope.launch {
            libraryRepository.isFavourite(listing.id).collect { favourite ->
                _uiState.value = _uiState.value.copy(isFavourite = favourite)
            }
        }
        viewModelScope.launch {
            libraryRepository.isSaved(listing.id).collect { saved ->
                _uiState.value = _uiState.value.copy(isSaved = saved)
            }
        }
        viewModelScope.launch {
            downloadRepository.observeDownload(listing.id).collect { download ->
                _uiState.value = _uiState.value.copy(download = download)
            }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                installedVersionCode = listing.packageName?.let {
                    installerRepository.installedVersionCode(it)
                },
                canRequestInstall = installerRepository.canRequestInstall(),
            )
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                listing = listing,
                state = UiState.Loading,
            )
            val result = getDetails(listing)
            result.fold(
                onSuccess = { detailed ->
                    _uiState.value = _uiState.value.copy(
                        listing = detailed,
                        state = UiState.Ready(detailed),
                        installedVersionCode = detailed.packageName?.let {
                            installerRepository.installedVersionCode(it)
                        },
                    )
                },
                onFailure = { _ ->
                    // The card already carried the summary, so a failed detail refresh
                    // degrades to the summary rather than a dead screen.
                    _uiState.value = _uiState.value.copy(
                        state = UiState.Ready(listing),
                        listing = listing,
                    )
                },
            )
        }
    }

    fun toggleFavourite() {
        val listing = _uiState.value.current ?: return
        viewModelScope.launch { toggleFavourite(listing) }
    }

    fun toggleSaved() {
        val listing = _uiState.value.current ?: return
        viewModelScope.launch { toggleSaved(listing) }
    }

    fun download(directUrl: String, wifiOnly: Boolean) {
        val listing = _uiState.value.current ?: return
        viewModelScope.launch {
            downloadRepository.enqueue(listing, directUrl, wifiOnly)
        }
    }

    fun retryDownload() {
        val listing = _uiState.value.current ?: return
        viewModelScope.launch {
            downloadRepository.retry(listing.id)
        }
    }

    fun pauseDownload() {
        val listing = _uiState.value.current ?: return
        viewModelScope.launch {
            downloadRepository.pause(listing.id)
        }
    }

    fun resumeDownload() {
        val listing = _uiState.value.current ?: return
        viewModelScope.launch {
            downloadRepository.resume(listing.id)
        }
    }
}
