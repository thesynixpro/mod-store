package com.aprax.modstore.domain.usecase

import com.aprax.modstore.domain.model.Aggregated
import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.Category
import com.aprax.modstore.domain.model.SortOrder
import com.aprax.modstore.domain.model.SourceFilter
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.repository.CatalogRepository
import javax.inject.Inject

/**
 * Fetches the aggregated home feed in one call and splits it into the sections the home
 * screen renders.
 *
 * Doing this in the domain layer keeps the view model free of fan-out logic, and it means
 * the fan-out happens once per refresh rather than once per section: six home rows do not
 * become six sets of network requests.
 */
class GetHomeFeedUseCase @Inject constructor(
    private val catalog: CatalogRepository,
) {

    data class HomeFeed(
        val newest: List<AppListing>,
        val byCategory: Map<String, List<AppListing>>,
        val succeededSources: List<SourceId>,
        val failedSources: Map<SourceId, AppError>,
    ) {
        val isEmpty: Boolean
            get() = newest.isEmpty() && byCategory.values.all { it.isEmpty() }

        val isFullyAvailable: Boolean
            get() = failedSources.isEmpty()
    }

    /**
     * [categories] are the sections to populate. Each is queried with its own per-source
     * slug so the rows show genuinely categorised content rather than a keyword guess.
     */
    suspend operator fun invoke(
        filter: SourceFilter,
        categories: List<Category>,
        perSection: Int = DEFAULT_PER_SECTION,
    ): Result<HomeFeed> = runCatching {
        val newest = catalog.recent(filter, page = 1, pageSize = perSection)

        val sections = linkedMapOf<String, List<AppListing>>()
        val extraFailures = linkedMapOf<SourceId, AppError>()

        for (category in categories) {
            val result = catalog.byCategory(
                category = category,
                filter = filter,
                page = 1,
                pageSize = perSection,
                sort = SortOrder.RECENTLY_UPDATED,
            )
            if (result.items.isNotEmpty()) {
                sections[category.key] = result.items
            }
            extraFailures.putAll(result.failures)
        }

        HomeFeed(
            newest = newest.items,
            byCategory = sections,
            succeededSources = newest.succeeded,
            failedSources = newest.failures + extraFailures,
        )
    }.recoverCatching { throw AppError.Unexpected(it) }

    companion object {
        const val DEFAULT_PER_SECTION = 12
    }
}

/**
 * De-duplicates a mixed list of listings from several sources.
 *
 * Sources overlap heavily, and the same app often appears on two sites. The first
 * occurrence wins, which — because the aggregate repository already orders by most
 * recently updated — keeps the freshest copy.
 */
class DeduplicateListingsUseCase @Inject constructor() {
    operator fun invoke(listings: List<AppListing>): List<AppListing> {
        val seen = HashSet<String>(listings.size)
        return listings.filter { seen.add(it.dedupeKey) }
    }
}

/** Details for one listing, with the source-specific errors the details screen reports. */
class GetListingDetailsUseCase @Inject constructor(
    private val catalog: CatalogRepository,
) {
    suspend operator fun invoke(listing: AppListing): Result<AppListing> =
        catalog.details(listing)
}

/** Everything the categories tab needs. Static taxonomy plus per-source availability. */
class GetCategoriesUseCase @Inject constructor(
    private val catalog: CatalogRepository,
) {
    suspend operator fun invoke(): Result<List<Category>> =
        runCatching { com.aprax.modstore.domain.model.CategoryTaxonomy.all }
}

/**
 * Turns a raw search box value into a query worth sending.
 *
 * Blank and single-character queries are rejected locally so the app does not hammer every
 * source with a request the user clearly has not finished typing.
 */
object SearchQueryRules {
    const val MIN_LENGTH = 2

    fun normalise(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun isSearchable(raw: String): Boolean = normalise(raw).length >= MIN_LENGTH

    /** Stable key for de-duplicating in-flight requests and history entries. */
    fun keyOf(raw: String): String = normalise(raw).lowercase()
}

/** Records a search term, ignoring duplicates and too-short input. */
class RecordSearchUseCase @Inject constructor(
    private val history: com.aprax.modstore.domain.repository.SearchHistoryRepository,
) {
    suspend operator fun invoke(term: String) {
        val normalised = SearchQueryRules.normalise(term)
        if (SearchQueryRules.isSearchable(normalised)) {
            history.record(normalised)
        }
    }
}

/** Merged searches across sources, de-duplicated, honouring the user's sort choice. */
class SearchCatalogUseCase @Inject constructor(
    private val catalog: CatalogRepository,
) {
    suspend operator fun invoke(
        query: String,
        filter: SourceFilter,
        sort: SortOrder,
        page: Int,
        pageSize: Int,
    ): Aggregated<AppListing> {
        if (!SearchQueryRules.isSearchable(query)) return Aggregated(emptyList(), emptyList(), emptyMap())
        return catalog.search(query, filter, page, pageSize, sort)
    }
}

/** Favourites and saved-for-later, exposed as one toggle API for the details screen. */
class ToggleFavouriteUseCase @Inject constructor(
    private val library: com.aprax.modstore.domain.repository.LibraryRepository,
) {
    suspend operator fun invoke(listing: AppListing) = library.toggleFavourite(listing)
}

class ToggleSavedUseCase @Inject constructor(
    private val library: com.aprax.modstore.domain.repository.LibraryRepository,
) {
    suspend operator fun invoke(listing: AppListing) = library.toggleSaved(listing)
}
