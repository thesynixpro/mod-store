package com.aprax.modstore.core.ui

import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.SourceId

/**
 * The loading state every screen shares.
 *
 * Modelling this once means no screen can accidentally ship a spinner that never resolves
 * or an error that shows no retry, because the shape of the state forces both to be
 * handled explicitly in the UI.
 */
sealed interface UiState<out T> {

    /** First load, nothing to show yet. Renders a skeleton. */
    data object Loading : UiState<Nothing>

    /** Ready. [data] may itself be empty, which renders the empty state. */
    data class Ready<T>(val data: T) : UiState<T>

    /** Terminal failure with a typed cause. */
    data class Failed(val error: AppError) : UiState<Nothing>

    /** Showing cached data, with a note about why it is stale. */
    data class Stale<T>(val data: T, val error: AppError) : UiState<T>

    val isLoading: Boolean get() = this is Loading
}

/**
 * The value when there is one, from either [UiState.Ready] or [UiState.Stale].
 *
 * Declared as an extension so the receiver's type argument is concrete and the smart cast
 * inside is provable; a member property on the covariant interface would not compile.
 */
val <T> UiState<T>.dataOrNull: T?
    get() = when (this) {
        is UiState.Ready -> data
        is UiState.Stale -> data
        else -> null
    }

/** The failure, whether it was terminal or accompanied by cached data. */
val <T> UiState<T>.errorOrNull: AppError?
    get() = when (this) {
        is UiState.Failed -> error
        is UiState.Stale -> error
        else -> null
    }

/**
 * Per-source load outcome for a screen that queries several sites.
 *
 * Holding these separately is what lets the UI say "Modyolo is unreachable, showing
 * HappyMod results" instead of failing the screen outright.
 */
data class SourceLoadReport(
    val succeeded: List<SourceId> = emptyList(),
    val failed: Map<SourceId, AppError> = emptyMap(),
    val loading: Set<SourceId> = emptySet(),
) {
    /** True when at least one source returned data or is still working. */
    val anyUsable: Boolean get() = succeeded.isNotEmpty() || loading.isNotEmpty()

    /** True when every attempted source failed. */
    val allFailed: Boolean
        get() = succeeded.isEmpty() && failed.isNotEmpty() && loading.isEmpty()

    /**
     * A one-line summary for the warning banner, or null when everything is healthy.
     * Names the failing sites so the user knows exactly what is missing.
     */
    fun warningMessage(sourceNameOf: (SourceId) -> String): String? {
        if (failed.isEmpty()) return null
        val names = failed.keys.map(sourceNameOf)
        return when {
            succeeded.isEmpty() -> "${names.joinToString(", ")} could not be reached."
            names.size == 1 -> "${names.first()} is unavailable. Showing other sources."
            else -> "${names.joinToString(", ")} are unavailable. Showing other sources."
        }
    }
}

/**
 * Pagination state for infinite-scroll lists.
 *
 * Kept separate from the item list so the footer spinner and the "retry this page" action
 * do not re-render the whole list.
 */
data class PagingState(
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val appendError: AppError? = null,
) {
    val canLoadMore: Boolean get() = hasMore && !isLoadingMore && appendError == null
}

/** A screen's catalogue state: the paged items, the per-source report, and paging. */
data class CatalogUiState(
    val state: UiState<List<AppListing>> = UiState.Loading,
    val report: SourceLoadReport = SourceLoadReport(),
    val paging: PagingState = PagingState(),
) {
    val listings: List<AppListing> get() = state.dataOrNull ?: emptyList()

    val isEmpty: Boolean get() = state is UiState.Ready && state.data.isEmpty()

    /**
     * True when the screen should render its full-page error instead of a list: the first
     * page failed outright, so there is nothing to show underneath a warning banner.
     */
    val shouldShowFullError: Boolean
        get() = state is UiState.Failed && listings.isEmpty()
}
