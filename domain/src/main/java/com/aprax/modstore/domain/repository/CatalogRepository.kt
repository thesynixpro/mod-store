package com.aprax.modstore.domain.repository

import com.aprax.modstore.domain.model.Aggregated
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.Category
import com.aprax.modstore.domain.model.Page
import com.aprax.modstore.domain.model.SortOrder
import com.aprax.modstore.domain.model.SourceFilter
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SourceStatus
import kotlinx.coroutines.flow.Flow

/**
 * One external site the app can read listings from.
 *
 * Each source is implemented independently and registered into a DI multibinding set.
 * Adding a source means adding one implementation plus one registration; no screen, view
 * model or repository needs to change. Removing a source is the same operation in reverse.
 *
 * Implementations must:
 *  * only use access methods the site authorizes (public API, or public pages a browser
 *    would receive), and never bypass auth, paywalls, or bot protections;
 *  * send honest, identifiable requests and honour rate limits;
 *  * never synthesise a listing, a download URL, a rating or a download count;
 *  * return failures as typed [com.aprax.modstore.domain.model.AppError]s rather than
 *    throwing, so a dead source cannot take down a screen.
 */
interface CatalogSource {

    val id: SourceId

    /** Immutable descriptive facts used for attribution in the UI. */
    val info: com.aprax.modstore.domain.model.SourceInfo

    /**
     * Whether this source can be queried right now. Implementations report
     * [com.aprax.modstore.domain.model.SourceAvailability.NoCatalogue] or
     * [LinkOnly] rather than pretending to have data.
     */
    suspend fun availability(): com.aprax.modstore.domain.model.SourceAvailability

    /** Paged, most-recently-updated listings. */
    suspend fun recent(page: Int, pageSize: Int): Result<Page<AppListing>>

    /** Paged listings within one of this source's own category slugs. */
    suspend fun byCategory(categorySlug: String, page: Int, pageSize: Int): Result<Page<AppListing>>

    /** Paged full-text search on the source. */
    suspend fun search(query: String, page: Int, pageSize: Int): Result<Page<AppListing>>

    /**
     * Full details for one listing. [sourceListingUrl] is the page on the source site;
     * implementations parse that page rather than guessing an endpoint.
     */
    suspend fun details(sourceListingUrl: String): Result<AppListing>
}

/**
 * Aggregate catalogue reads across every enabled source.
 *
 * This is the "backend" seam the brief asks for: the app today reads sources directly
 * through [CatalogSource], and a future REST or Firebase backend can be slotted in behind
 * this same interface without touching the feature modules.
 */
interface CatalogRepository {

    /** Current state of every known source, including ones the user switched off. */
    fun sourceStatuses(): Flow<List<SourceStatus>>

    /**
     * Newest listings across all enabled sources, merged and de-duplicated by package
     * name. Per-source failures are reported inside [Aggregated] instead of failing the
     * whole call.
     */
    suspend fun recent(filter: SourceFilter, page: Int, pageSize: Int): Aggregated<AppListing>

    suspend fun byCategory(
        category: Category,
        filter: SourceFilter,
        page: Int,
        pageSize: Int,
        sort: SortOrder,
    ): Aggregated<AppListing>

    suspend fun search(
        query: String,
        filter: SourceFilter,
        page: Int,
        pageSize: Int,
        sort: SortOrder,
    ): Aggregated<AppListing>

    /** Details for a listing, using the source that published it. */
    suspend fun details(listing: AppListing): Result<AppListing>

    /** Cached listings, newest first, used when a source is unreachable. */
    fun cachedListings(limit: Int): Flow<List<AppListing>>

    /** Rows from the most recent successful sync, for the offline banner. */
    suspend fun lastSuccessfulSync(): Long?
}

/** Favourites and "save for later", persisted locally. */
interface LibraryRepository {
    fun favourites(): Flow<List<AppListing>>
    fun isFavourite(id: String): Flow<Boolean>
    suspend fun toggleFavourite(listing: AppListing)
    suspend fun removeFavourite(id: String): Result<Unit>

    fun savedForLater(): Flow<List<AppListing>>
    fun isSaved(id: String): Flow<Boolean>
    suspend fun toggleSaved(listing: AppListing)

    /** Every version of an app the user has previously seen, for the version tracker. */
    fun trackedVersions(): Flow<List<AppListing>>
}

/** Recently searched terms, persisted locally. */
interface SearchHistoryRepository {
    fun recentSearches(limit: Int): Flow<List<String>>
    suspend fun record(term: String)
    suspend fun clear()
    suspend fun remove(term: String)
}

/** User settings backed by DataStore. */
interface SettingsRepository {
    val settings: Flow<com.aprax.modstore.domain.model.UserSettings>
    suspend fun setThemeMode(mode: com.aprax.modstore.domain.model.ThemeMode)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setWifiOnlyDownloads(wifiOnly: Boolean)
    suspend fun setAutoDeleteInstalledApks(delete: Boolean)
    suspend fun setSourceEnabled(sourceId: SourceId, enabled: Boolean)
    suspend fun setLanguage(tag: String)
    suspend fun setDynamicColour(enabled: Boolean)
    suspend fun setOnboardingComplete(complete: Boolean)
}
