package com.aprax.modstore.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.ThemeMode
import com.aprax.modstore.domain.model.UserSettings
import com.aprax.modstore.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aprax-settings",
)

/**
 * User settings, stored in DataStore.
 *
 * Reads are total: a corrupt or unreadable preferences file falls back to defaults rather
 * than crashing on launch, and every enum is parsed leniently so an older or newer build
 * cannot leave the app in an unusable state.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val wifiOnly = booleanPreferencesKey("wifi_only_downloads")
        val autoDelete = booleanPreferencesKey("auto_delete_installed")
        val enabledSources = stringSetPreferencesKey("enabled_sources")
        val language = stringPreferencesKey("language_tag")
        val dynamicColour = booleanPreferencesKey("dynamic_colour")
        val onboarding = booleanPreferencesKey("onboarding_complete")
    }

    override val settings: Flow<UserSettings> = context.settingsDataStore.data
        .catch { error ->
            // A broken preferences file must not prevent the app from starting.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toSettings() }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        val storedSources = this[Keys.enabledSources]
        val enabled = if (storedSources == null) {
            defaults.enabledSources
        } else {
            // Keep only ids this build still knows about, so removing a source from the
            // codebase does not leave a dangling id in settings.
            storedSources.mapNotNull { SourceId.fromKey(it) }.toSet()
        }

        return UserSettings(
            themeMode = ThemeMode.fromKey(this[Keys.themeMode]),
            notificationsEnabled = this[Keys.notifications] ?: defaults.notificationsEnabled,
            wifiOnlyDownloads = this[Keys.wifiOnly] ?: defaults.wifiOnlyDownloads,
            autoDeleteInstalledApks = this[Keys.autoDelete] ?: defaults.autoDeleteInstalledApks,
            enabledSources = enabled,
            languageTag = this[Keys.language] ?: defaults.languageTag,
            dynamicColour = this[Keys.dynamicColour] ?: defaults.dynamicColour,
            onboardingComplete = this[Keys.onboarding] ?: defaults.onboardingComplete,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.notifications] = enabled }
    }

    override suspend fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        context.settingsDataStore.edit { it[Keys.wifiOnly] = wifiOnly }
    }

    override suspend fun setAutoDeleteInstalledApks(delete: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoDelete] = delete }
    }

    override suspend fun setSourceEnabled(sourceId: SourceId, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences[Keys.enabledSources]
                ?.mapNotNull { SourceId.fromKey(it) }
                ?.toMutableSet()
                ?: UserSettings().enabledSources.toMutableSet()
            if (enabled) current.add(sourceId) else current.remove(sourceId)
            preferences[Keys.enabledSources] = current.map { it.name }.toSet()
        }
    }

    override suspend fun setLanguage(tag: String) {
        context.settingsDataStore.edit { it[Keys.language] = tag }
    }

    override suspend fun setDynamicColour(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamicColour] = enabled }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        context.settingsDataStore.edit { it[Keys.onboarding] = complete }
    }
}
