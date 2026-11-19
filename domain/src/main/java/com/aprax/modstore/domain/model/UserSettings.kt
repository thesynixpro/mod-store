package com.aprax.modstore.domain.model

/** Theme selection offered in Settings. */
enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * Everything the user can configure. Defaults are chosen so a fresh install is safe:
 * notifications on, downloads restricted to Wi-Fi, and no automatic file deletion.
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    /** Only start downloads on an unmetered connection. On by default to protect data. */
    val wifiOnlyDownloads: Boolean = true,
    /** Delete the APK once the user has installed it, to reclaim storage. */
    val autoDeleteInstalledApks: Boolean = false,
    /** Per-source switches. All on by default; a source with no catalogue reports itself. */
    val enabledSources: Set<SourceId> = SourceId.entries.toSet(),
    /** BCP-47 language tag, or "system" to follow the device. */
    val languageTag: String = LANGUAGE_SYSTEM,
    val dynamicColour: Boolean = false,
    val onboardingComplete: Boolean = false,
) {
    fun isSourceEnabled(sourceId: SourceId): Boolean = sourceId in enabledSources

    /** The filter object the catalogue layer consumes. */
    val sourceFilter: SourceFilter get() = SourceFilter(enabledSources)

    companion object {
        const val LANGUAGE_SYSTEM = "system"
    }
}
