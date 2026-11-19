package com.aprax.modstore.data.repository

import com.aprax.modstore.data.local.ListingDao
import com.aprax.modstore.data.local.SourceHealthDao
import com.aprax.modstore.data.local.SourceHealthEntity
import com.aprax.modstore.data.network.NetworkMonitor
import com.aprax.modstore.domain.model.Aggregated
import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.Category
import com.aprax.modstore.domain.model.Page
import com.aprax.modstore.domain.model.SortOrder
import com.aprax.modstore.domain.model.SourceAvailability
import com.aprax.modstore.domain.model.SourceFilter
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SourceStatus
import com.aprax.modstore.domain.repository.CatalogRepository
import com.aprax.modstore.domain.repository.CatalogSource
import com.aprax.modstore.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fans a query out across every enabled source and merges the results.
 *
 * Design rules this class enforces, all of which come straight from the brief:
 *
 *  * **Isolation.** Each source is queried in its own coroutine and its failure is caught
 *    locally. One site being down changes the [Aggregated.failures] map, never the
 *    exception the screen sees. A screen therefore keeps working with fewer sources.
 *  * **Honesty.** Results are reported per source. Nothing is invented to fill a gap: a
 *    source that returns nothing contributes nothing, and a source that has no catalogue
 *    (A1N) is reported as such rather than silently ignored.
 *  * **Provenance.** Every listing carries its own [AppListing.sourceName] and
 *    [AppListing.sourceListingUrl], so a merged list is still fully attributed.
 *  * **Politeness.** Queries run concurrently but through the shared per-host rate
 *    limiter, and each source's own HTTP cache absorbs repeats.
 *  * **Offline resilience.** Successful pages are written to the Room cache, and
 *    [cachedListings] serves them back when sources are unreachable.
 */
@Singleton
class AggregatedCatalogRepository @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards CatalogSource>,
    private val listingDao: ListingDao,
    private val sourceHealthDao: SourceHealthDao,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
) : CatalogRepository {

    /** Sources ordered deterministically so the UI never reshuffles between launches. */
    private val orderedSources: List<CatalogSource> by lazy {
        sources.sortedBy { it.id.ordinal }
    }

    override fun sourceStatuses(): Flow<List<SourceStatus>> =
        combine(
            settingsRepository.settings,
            sourceHealthDao.observeAll(),
        ) { settings, health ->
            val healthBySource = health.associateBy { it.sourceId }
            orderedSources.map { source ->
                val record = healthBySource[source.id.name]
                SourceStatus(
                    info = source.info,
                    availability = availabilityFrom(source, record),
                    enabledByUser = settings.isSourceEnabled(source.id),
                )
            }
        }

    /**
     * Derives a display state from the recorded health, so the Settings screen can show
     * "reachable" or "last error" without making a network request just to draw a row.
     */
    private fun availabilityFrom(
        source: CatalogSource,
        record: SourceHealthEntity?,
    ): SourceAvailability {
        // A1N is structurally different: it has no catalogue, and that is a permanent fact
        // about the site rather than a failure to report as a transient problem.
        if (source.id == SourceId.A1N) {
            return SourceAvailability.NoCatalogue(
                "a1n.com serves a domain notice and publishes no APK listings."
            )
        }
        if (record != null && record.consecutiveFailures > 0 && record.lastError != null) {
            return SourceAvailability.TemporarilyUnavailable(
                AppError.SourceUnavailable(source.id, source.info.displayName)
            )
        }
        return SourceAvailability.Available
    }

    override suspend fun recent(
        filter: SourceFilter,
        page: Int,
        pageSize: Int,
    ): Aggregated<AppListing> = fanOut(filter) { source ->
        source.recent(page, pageSize)
    }.let { (listings, succeeded, failures) ->
        Aggregated(
            items = finalise(listings, SortOrder.RECENTLY_UPDATED),
            succeeded = succeeded,
            failures = failures,
        )
    }

    override suspend fun byCategory(
        category: Category,
        filter: SourceFilter,
        page: Int,
        pageSize: Int,
        sort: SortOrder,
    ): Aggregated<AppListing> {
        // Only query sources that actually publish this category. A source without the
        // slug is skipped rather than searched with a guessed term, because a guess would
        // produce mislabelled results.
        val participating = filter.enabled.filter { category.sourceSlugs.containsKey(it) }

        if (participating.isEmpty()) {
            return Aggregated(
                items = emptyList(),
                succeeded = emptyList(),
                failures = emptyMap(),
            )
        }

        val (listings, succeeded, failures) = fanOut(SourceFilter(participating.toSet())) { source ->
            val slug = category.sourceSlugs[source.id] ?: return@fanOut Result.success(Page.empty(page))
            source.byCategory(slug, page, pageSize)
        }

        return Aggregated(
            items = finalise(listings, sort),
            succeeded = succeeded,
            failures = failures,
        )
    }

    override suspend fun search(
        query: String,
        filter: SourceFilter,
        page: Int,
        pageSize: Int,
        sort: SortOrder,
    ): Aggregated<AppListing> {
        val (listings, succeeded, failures) = fanOut(filter) { source ->
            source.search(query, page, pageSize)
        }
        return Aggregated(
            items = finalise(listings, sort),
            succeeded = succeeded,
            failures = failures,
        )
    }

    override suspend fun details(listing: AppListing): Result<AppListing> {
        val source = orderedSources.firstOrNull { it.id == listing.sourceId }
            ?: return Result.failure(AppError.NotFound("source"))

        val result = source.details(listing.sourceListingUrl)
        result.onSuccess { detailed ->
            // Refresh the cache with the richer record so the details survive going offline.
            listingDao.upsertAll(listOf(detailed), System.currentTimeMillis())
            recordSuccess(source.id)
        }.onFailure { error -> recordFailure(source.id, error as? AppError ?: AppError.Unexpected(error)) }

        return result.recoverCatching { cause ->
            // Fall back to the cached copy when the live page cannot be read, but keep the
            // error visible so the UI can say the data may be stale.
            val cached = listingDao.findById(listing.id)?.toDomain()
            cached ?: throw (cause as? AppError ?: AppError.Unexpected(cause))
        }
    }

    override fun cachedListings(limit: Int): Flow<List<AppListing>> =
        listingDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun lastSuccessfulSync(): Long? = sourceHealthDao.lastAnySuccessAt()

    // ---------------------------------------------------------------------------------
    // Fan-out
    // ---------------------------------------------------------------------------------

    private data class FanOutResult(
        val listings: List<AppListing>,
        val succeeded: List<SourceId>,
        val failures: Map<SourceId, AppError>,
    )

    /**
     * Runs [query] against every source in [filter] concurrently, collecting results and
     * failures separately. Never throws for a source-level problem.
     */
    private suspend fun fanOut(
        filter: SourceFilter,
        query: suspend (CatalogSource) -> Result<Page<AppListing>>,
    ): FanOutResult {
        if (!networkMonitor.isOnline()) {
            val offlineFailures = filter.enabled
                .mapNotNull { id -> orderedSources.firstOrNull { it.id == id } }
                .associate { it.id to (AppError.Offline() as AppError) }
            return FanOutResult(emptyList(), emptyList(), offlineFailures)
        }

        val targets = orderedSources.filter { filter.includes(it.id) }
        if (targets.isEmpty()) return FanOutResult(emptyList(), emptyList(), emptyMap())

        val outcomes = coroutineScope {
            targets.map { source ->
                async {
                    // A hard ceiling per source: a hanging site must not hold the screen.
                    val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { query(source) }.getOrElse { Result.failure(AppError.Unexpected(it)) }
                    } ?: Result.failure(AppError.Timeout(source.info.displayName))

                    source.id to result
                }
            }.awaitAll()
        }

        val listings = mutableListOf<AppListing>()
        val succeeded = mutableListOf<SourceId>()
        val failures = linkedMapOf<SourceId, AppError>()
        val now = System.currentTimeMillis()

        for ((sourceId, result) in outcomes) {
            result.fold(
                onSuccess = { page ->
                    succeeded.add(sourceId)
                    listings.addAll(page.items)
                    recordSuccess(sourceId)
                    // Cache what the source actually returned, so later visits work offline.
                    if (page.items.isNotEmpty()) {
                        runCatching { listingDao.upsertAll(page.items, now) }
                    }
                },
                onFailure = { cause ->
                    val error = cause as? AppError ?: AppError.Unexpected(cause)
                    failures[sourceId] = error
                    recordFailure(sourceId, error)
                },
            )
        }

        return FanOutResult(listings, succeeded, failures)
    }

    /**
     * Applies de-duplication and the requested ordering.
     *
     * The same app often appears on more than one site. Keeping the first occurrence (the
     * sources are ordered deterministically) means the list is stable between refreshes
     * instead of shuffling which duplicate survives.
     */
    private fun finalise(listings: List<AppListing>, sort: SortOrder): List<AppListing> {
        val seen = HashSet<String>(listings.size)
        val unique = listings.filter { seen.add(it.dedupeKey) }

        return when (sort) {
            SortOrder.RECENTLY_UPDATED -> unique.sortedByDescending { it.lastUpdated ?: "" }
            SortOrder.NAME_ASC -> unique.sortedBy { it.appName.lowercase() }
            SortOrder.SOURCE -> unique.sortedWith(
                compareBy({ it.sourceId.ordinal }, { it.appName.lowercase() })
            )
            // Relevance is the source's own ordering, which de-duplication preserved.
            SortOrder.RELEVANCE -> unique
        }
    }

    private suspend fun recordSuccess(sourceId: SourceId) {
        val existing = sourceHealthDao.find(sourceId.name)
        sourceHealthDao.upsert(
            SourceHealthEntity(
                sourceId = sourceId.name,
                lastSuccessAt = System.currentTimeMillis(),
                lastFailureAt = existing?.lastFailureAt,
                lastError = null,
                consecutiveFailures = 0,
            )
        )
    }

    private suspend fun recordFailure(sourceId: SourceId, error: AppError) {
        val existing = sourceHealthDao.find(sourceId.name)
        sourceHealthDao.upsert(
            SourceHealthEntity(
                sourceId = sourceId.name,
                lastSuccessAt = existing?.lastSuccessAt,
                lastFailureAt = System.currentTimeMillis(),
                lastError = error.message,
                consecutiveFailures = (existing?.consecutiveFailures ?: 0) + 1,
            )
        )
    }

    private companion object {
        /** Per-source ceiling. Two sites in parallel still finish well inside a screen. */
        const val SOURCE_TIMEOUT_MS = 20_000L
    }
}
