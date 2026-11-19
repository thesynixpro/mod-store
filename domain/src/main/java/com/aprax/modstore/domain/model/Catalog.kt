package com.aprax.modstore.domain.model

/** One page of results plus enough context for the UI to request the next one. */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    /** True when the source indicated more results exist after this page. */
    val hasMore: Boolean,
) {
    companion object {
        fun <T> empty(page: Int = 1): Page<T> = Page(emptyList(), page, hasMore = false)
        fun <T> single(items: List<T>): Page<T> = Page(items, page = 1, hasMore = false)
    }
}

/** A category destination shown on the Categories tab and as home shortcuts. */
data class Category(
    /** Stable key used in navigation routes. */
    val key: String,
    val title: String,
    /** True for the game half of the taxonomy, which drives a different icon set. */
    val isGame: Boolean,
    /**
     * Per-source category slug, so tapping a category can query each site with the slug it
     * actually uses instead of guessing a translated term.
     */
    val sourceSlugs: Map<SourceId, String> = emptyMap(),
    val description: String? = null,
)

/** Sort orders offered in search and category browsing. */
enum class SortOrder(val label: String) {
    RELEVANCE("Relevance"),
    RECENTLY_UPDATED("Recently updated"),
    NAME_ASC("Name (A-Z)"),
    SOURCE("Source"),
}

/** Which sources to include in a query, mirroring the per-source enable/disable switches. */
data class SourceFilter(
    val enabled: Set<SourceId> = SourceId.entries.toSet(),
) {
    fun includes(sourceId: SourceId): Boolean = sourceId in enabled
    val isEmpty: Boolean get() = enabled.isEmpty()

    companion object {
        val All = SourceFilter()
    }
}

/** The result of asking several sources at once, kept separate so failures stay visible. */
data class Aggregated<T>(
    val items: List<T>,
    /** Sources that answered successfully. */
    val succeeded: List<SourceId>,
    /** Sources that failed, with the reason, so the UI can say which site is down. */
    val failures: Map<SourceId, AppError>,
)
