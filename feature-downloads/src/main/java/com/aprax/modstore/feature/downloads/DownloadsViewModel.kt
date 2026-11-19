package com.aprax.modstore.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.core.ui.dataOrNull
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.repository.DownloadRepository
import com.aprax.modstore.domain.repository.DownloadState
import com.aprax.modstore.domain.repository.DownloadedApk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val active: UiState<List<DownloadState>> = UiState.Loading,
    val history: UiState<List<DownloadedApk>> = UiState.Loading,
    val storageUsedBytes: Long = 0L,
) {
    val activeDownloads: List<DownloadState> get() = active.dataOrNull ?: emptyList()
    val historyDownloads: List<DownloadedApk> get() = history.dataOrNull ?: emptyList()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadRepository.observeDownloads().collect { states ->
                _uiState.value = _uiState.value.copy(
                    active = UiState.Ready(
                        states.filter { it.status.isActive || it.status == com.aprax.modstore.domain.repository.DownloadStatus.READY_TO_INSTALL }
                    )
                )
            }
        }
        viewModelScope.launch {
            downloadRepository.history().collect { apks ->
                _uiState.value = _uiState.value.copy(history = UiState.Ready(apks))
            }
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                storageUsedBytes = downloadRepository.storageUsedBytes()
            )
        }
    }

    fun pause(listingId: String) {
        viewModelScope.launch { downloadRepository.pause(listingId) }
    }

    fun resume(listingId: String) {
        viewModelScope.launch { downloadRepository.resume(listingId) }
    }

    fun cancel(listingId: String) {
        viewModelScope.launch { downloadRepository.cancel(listingId) }
    }

    fun retry(listingId: String) {
        viewModelScope.launch { downloadRepository.retry(listingId) }
    }

    fun removeFromHistory(apk: DownloadedApk) {
        viewModelScope.launch { downloadRepository.removeFromHistory(apk.id) }
    }

    fun deleteFile(apk: DownloadedApk) {
        viewModelScope.launch { downloadRepository.deleteFile(apk) }
    }
}
