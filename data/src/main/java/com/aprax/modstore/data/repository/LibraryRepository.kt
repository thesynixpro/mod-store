package com.aprax.modstore.data.repository

import com.aprax.modstore.data.local.LibraryDao
import com.aprax.modstore.data.local.LibraryEntryEntity
import com.aprax.modstore.data.local.SearchHistoryDao
import com.aprax.modstore.data.local.SearchHistoryEntity
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.repository.LibraryRepository
import com.aprax.modstore.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favourites and "save for later".
 *
 * Both flags live on one row per listing, because they are two independent booleans the
 * user toggles from different places. Clearing the last one deletes the row so the library
 * never accumulates invisible entries.
 */
@Singleton
class DefaultLibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
) : LibraryRepository {

    override fun favourites(): Flow<List<AppListing>> =
        libraryDao.observeFavourites().map { rows -> rows.map { it.toDomain() } }

    override fun isFavourite(id: String): Flow<Boolean> = libraryDao.observeIsFavourite(id)

    override suspend fun toggleFavourite(listing: AppListing) {
        val existing = libraryDao.find(listing.id)
        if (existing == null) {
            libraryDao.upsert(
                LibraryEntryEntity.fromDomain(
                    listing = listing,
                    isFavourite = true,
                    isSaved = false,
                    savedAt = System.currentTimeMillis(),
                )
            )
            return
        }

        val nowFavourite = !existing.isFavourite
        libraryDao.upsert(existing.copy(isFavourite = nowFavourite))

        // If the row is now neither favourited nor saved, it carries no meaning.
        if (!nowFavourite && !existing.isSaved) {
            libraryDao.deleteIfUnused(listing.id)
        }
    }

    override suspend fun removeFavourite(id: String): Result<Unit> = runCatching {
        val existing = libraryDao.find(id) ?: return@runCatching
        libraryDao.upsert(existing.copy(isFavourite = false))
        libraryDao.deleteIfUnused(id)
    }

    override fun savedForLater(): Flow<List<AppListing>> =
        libraryDao.observeSaved().map { rows -> rows.map { it.toDomain() } }

    override fun isSaved(id: String): Flow<Boolean> = libraryDao.observeIsSaved(id)

    override suspend fun toggleSaved(listing: AppListing) {
        val existing = libraryDao.find(listing.id)
        if (existing == null) {
            libraryDao.upsert(
                LibraryEntryEntity.fromDomain(
                    listing = listing,
                    isFavourite = false,
                    isSaved = true,
                    savedAt = System.currentTimeMillis(),
                )
            )
            return
        }

        val nowSaved = !existing.isSaved
        libraryDao.upsert(existing.copy(isSaved = nowSaved))
        if (!nowSaved && !existing.isFavourite) {
            libraryDao.deleteIfUnused(listing.id)
        }
    }

    /**
     * Rows the user has interacted with, used by the library screen to show which saved
     * apps have moved on since they were pinned.
     */
    override fun trackedVersions(): Flow<List<AppListing>> =
        libraryDao.observeAll().map { rows -> rows.map { it.toDomain() } }
}

/** Recent search terms, trimmed to a fixed history size. */
@Singleton
class DefaultSearchHistoryRepository @Inject constructor(
    private val historyDao: SearchHistoryDao,
) : SearchHistoryRepository {

    override fun recentSearches(limit: Int): Flow<List<String>> =
        historyDao.observeRecent(limit).map { rows -> rows.map { it.displayTerm } }

    override suspend fun record(term: String) {
        if (term.isBlank()) return
        historyDao.upsert(
            SearchHistoryEntity(
                termKey = term.trim().lowercase(),
                displayTerm = term.trim(),
                searchedAt = System.currentTimeMillis(),
            )
        )
        // Re-searching an old term moves it to the top and drops the oldest beyond the cap.
        historyDao.trimTo(MAX_HISTORY)
    }

    override suspend fun clear() = historyDao.clear()

    override suspend fun remove(term: String) = historyDao.delete(term.trim().lowercase())

    private companion object {
        const val MAX_HISTORY = 20
    }
}
