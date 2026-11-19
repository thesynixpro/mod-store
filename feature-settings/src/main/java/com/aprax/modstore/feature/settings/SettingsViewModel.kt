package com.aprax.modstore.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SourceStatus
import com.aprax.modstore.domain.model.ThemeMode
import com.aprax.modstore.domain.model.UserSettings
import com.aprax.modstore.domain.repository.CatalogRepository
import com.aprax.modstore.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val sources: UiState<List<SourceStatus>> = UiState.Loading,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            catalogRepository.sourceStatuses().collect { statuses ->
                _uiState.value = _uiState.value.copy(
                    sources = UiState.Ready(statuses),
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColour(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColour(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        viewModelScope.launch { settingsRepository.setWifiOnlyDownloads(wifiOnly) }
    }

    fun setAutoDeleteInstalledApks(delete: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDeleteInstalledApks(delete) }
    }

    fun toggleSource(sourceId: SourceId, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSourceEnabled(sourceId, enabled) }
    }

    fun refreshSources() {
        viewModelScope.launch {
            // Re-run the status flow so transient failures re-resolve.
            catalogRepository.sourceStatuses().first()
            _uiState.value = _uiState.value.copy(
                sources = UiState.Loading,
            )
            catalogRepository.sourceStatuses().collect { statuses ->
                _uiState.value = _uiState.value.copy(
                    sources = UiState.Ready(statuses),
                )
            }
        }
    }
}
