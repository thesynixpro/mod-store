package com.aprax.modstore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.UserSettings
import com.aprax.modstore.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-scoped state shared by the shell.
 *
 * The settings flow drives the theme, a small in-memory registry carries the
 * [AppListing]s the detail route renders, and a one-shot slot holds an incoming
 * source-page deep link until the nav host has navigated to it.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    /** Live value of the wifi-only preference for the details screen's download action. */
    val wifiOnly: Boolean get() = _settings.value.wifiOnlyDownloads

    /**
     * Cached listings by stable id. Only ever written from the screens' callbacks, the
     * downloads history, or a deep link; never synthesised beyond a minimal identity.
     */
    private val _listings = MutableStateFlow<Map<String, AppListing>>(emptyMap())
    val listings: StateFlow<Map<String, AppListing>> = _listings.asStateFlow()

    /** A source-page link waiting for the nav host to open it. */
    private val _deepLink = MutableStateFlow<AppListing?>(null)
    val deepLink: StateFlow<AppListing?> = _deepLink.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _settings.value = settings
            }
        }
    }

    /** Called by the shell before navigating to a detail, so the route can render it. */
    fun rememberListing(listing: AppListing) {
        _listings.value = _listings.value + (listing.id to listing)
    }

    fun rememberListings(listings: List<AppListing>) {
        _listings.value = _listings.value + listings.associateBy { it.id }
    }

    /** The most recently seen listing with this id, if any. */
    fun listingById(id: String): AppListing? = _listings.value[id]

    /** Queue a deep-linked listing for the nav host to open, remembering it for details. */
    fun queueDeepLink(listing: AppListing) {
        rememberListing(listing)
        _deepLink.value = listing
    }

    /** The nav host has navigated to the pending deep link. */
    fun consumeDeepLink(listing: AppListing) {
        if (_deepLink.value?.id == listing.id) _deepLink.value = null
    }
}
