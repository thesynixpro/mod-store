package com.aprax.modstore.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.repository.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * Download state, persisted so the Downloads tab survives process death and a reboot.
 *
 * A `RUNNING` row found at startup is not a running download any more — the worker died
 * with the process. The repository reconciles those to [DownloadStatus.FAILED] with an
 * explicit reason rather than showing a progress bar that will never move.
 */
@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("listingId", unique = true)],
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val listingId: String,
    val appName: String,
    val packageName: String?,
    val versionName: String?,
    val sourceId: String,
    val sourceListingUrl: String,
    val downloadUrl: String,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val localFilePath: String?,
    val sha256: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val installed: Boolean,
) {
    fun statusOrNull(): DownloadStatus? = runCatching { DownloadStatus.valueOf(status) }.getOrNull()
    fun sourceIdOrNull(): SourceId? = SourceId.fromKey(sourceId)

    companion object {
        fun queued(
            id: String,
            listing: com.aprax.modstore.domain.model.AppListing,
            downloadUrl: String,
            now: Long,
        ) = DownloadEntity(
            id = id,
            listingId = listing.id,
            appName = listing.appName,
            packageName = listing.packageName,
            versionName = listing.versionName,
            sourceId = listing.sourceId.name,
            sourceListingUrl = listing.sourceListingUrl,
            downloadUrl = downloadUrl,
            status = DownloadStatus.QUEUED.name,
            bytesDownloaded = 0L,
            totalBytes = null,
            localFilePath = null,
            sha256 = null,
            error = null,
            createdAt = now,
            updatedAt = now,
            installed = false,
        )
    }
}

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE listingId = :listingId LIMIT 1")
    fun observeForListing(listingId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE listingId = :listingId LIMIT 1")
    suspend fun findByListing(listingId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun findById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE packageName = :packageName ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByPackage(packageName: String?): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('RUNNING', 'QUEUED', 'PAUSED')")
    suspend fun activeDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN ('READY_TO_INSTALL', 'COMPLETED') ORDER BY updatedAt DESC")
    fun observeInstallable(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE installed = 1 ORDER BY updatedAt DESC")
    fun observeInstalled(): Flow<List<DownloadEntity>>

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloads WHERE installed = 0 AND localFilePath IS NOT NULL")
    suspend fun storageUsedBytes(): Long

    @Upsert
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, error = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?, now: Long)

    @Query(
        """
        UPDATE downloads SET bytesDownloaded = :bytes, totalBytes = :total,
        status = :status, updatedAt = :now WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        bytes: Long,
        total: Long?,
        status: String,
        now: Long,
    )

    @Query(
        """
        UPDATE downloads SET status = :status, localFilePath = :path, totalBytes = :size,
        sha256 = :sha256, error = NULL, updatedAt = :now WHERE id = :id
        """
    )
    suspend fun complete(
        id: String,
        status: String,
        path: String,
        size: Long,
        sha256: String?,
        now: Long,
    )

    @Query("UPDATE downloads SET installed = :installed, status = :status, updatedAt = :now WHERE id = :id")
    suspend fun markInstalled(id: String, installed: Boolean, status: String, now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    /** Removes a row only when it is no longer holding a file the user might install. */
    @Query("DELETE FROM downloads WHERE id = :id AND status IN ('INSTALLED', 'CANCELLED', 'FAILED')")
    suspend fun deleteIfFinished(id: String)

    @Query("DELETE FROM downloads WHERE localFilePath IS NULL AND status IN ('INSTALLED', 'CANCELLED', 'FAILED')")
    suspend fun purgeOrphans()

    /** One row per listing, so a re-download replaces the previous attempt. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)
}

/**
 * A record of what was seen at each source, used to answer "was this ever reachable?" and
 * to drive the Settings screen's source health list.
 */
@Entity(tableName = "source_health")
data class SourceHealthEntity(
    @PrimaryKey val sourceId: String,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val lastError: String?,
    val consecutiveFailures: Int,
)

@Dao
interface SourceHealthDao {

    @Query("SELECT * FROM source_health")
    fun observeAll(): Flow<List<SourceHealthEntity>>

    @Query("SELECT * FROM source_health WHERE sourceId = :sourceId")
    suspend fun find(sourceId: String): SourceHealthEntity?

    @Upsert
    suspend fun upsert(entity: SourceHealthEntity)

    @Query("SELECT MAX(lastSuccessAt) FROM source_health")
    suspend fun lastAnySuccessAt(): Long?
}

@Database(
    entities = [
        ListingEntity::class,
        ScreenshotEntity::class,
        VariantEntity::class,
        LibraryEntryEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        SourceHealthEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ApraxDatabase : RoomDatabase() {

    abstract fun listingDao(): ListingDao
    abstract fun libraryDao(): LibraryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun sourceHealthDao(): SourceHealthDao

    companion object {
        const val NAME = "aprax-mods-store.db"
    }
}

/**
 * Columns whose values must never be trusted blindly when read back.
 *
 * A stored enum name that no longer exists (after a downgrade, or a hand-edited database)
 * would otherwise throw on the first read. Each reader therefore falls back to a safe
 * default instead.
 */
internal fun safeStatus(raw: String?): DownloadStatus =
    DownloadStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: DownloadStatus.FAILED

internal fun safeSource(raw: String?): SourceId? = SourceId.fromKey(raw ?: "")
