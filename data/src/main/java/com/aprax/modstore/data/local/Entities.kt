package com.aprax.modstore.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.AppVariant
import com.aprax.modstore.domain.model.SecurityStatus
import com.aprax.modstore.domain.model.SourceId
import kotlinx.coroutines.flow.Flow

/**
 * A cached listing.
 *
 * The cache exists so the app is still useful when a source is unreachable — the brief
 * requires the app to keep working when a source is down. It stores exactly what the source
 * published, so a cached row can never claim more than the live one did.
 */
@Entity(
    tableName = "listings",
    indices = [
        Index("sourceId"),
        Index("dedupeKey"),
        Index("lastSeenAt"),
        Index(value = ["sourceId", "sourceListingUrl"], unique = true),
    ],
)
data class ListingEntity(
    @PrimaryKey val id: String,
    val appName: String,
    val sourceId: String,
    val sourceName: String,
    val sourceListingUrl: String,
    val packageName: String?,
    val description: String?,
    val iconUrl: String?,
    val versionName: String?,
    val versionCode: Long?,
    val fileSize: String?,
    val fileSizeBytes: Long?,
    val category: String?,
    val downloadUrl: String?,
    val lastUpdated: String?,
    val androidRequirements: String?,
    val securityStatus: String,
    val developer: String?,
    val modInfo: String?,
    val ratingValue: Double?,
    val downloadCount: Long?,
    /** Computed at write time so the cache can be pruned and ordered without a join. */
    @ColumnInfo(name = "dedupeKey") val dedupeKey: String,
    /** Wall-clock time this row was last written from a live source response. */
    @ColumnInfo(name = "lastSeenAt") val lastSeenAt: Long,
) {
    fun toDomain(variants: List<AppVariant>, screenshots: List<String>): AppListing = AppListing(
        id = id,
        appName = appName,
        sourceId = SourceId.fromKey(sourceId) ?: SourceId.MODYOLO,
        sourceName = sourceName,
        sourceListingUrl = sourceListingUrl,
        packageName = packageName,
        description = description,
        iconUrl = iconUrl,
        screenshots = screenshots,
        versionName = versionName,
        versionCode = versionCode,
        fileSize = fileSize,
        fileSizeBytes = fileSizeBytes,
        category = category,
        downloadUrl = downloadUrl,
        lastUpdated = lastUpdated,
        androidRequirements = androidRequirements,
        securityStatus = runCatching { SecurityStatus.valueOf(securityStatus) }
            .getOrDefault(SecurityStatus.UNKNOWN),
        developer = developer,
        modInfo = modInfo,
        ratingValue = ratingValue,
        downloadCount = downloadCount,
        variants = variants,
    )

    companion object {
        fun fromDomain(listing: AppListing, seenAt: Long): ListingEntity = ListingEntity(
            id = listing.id,
            appName = listing.appName,
            sourceId = listing.sourceId.name,
            sourceName = listing.sourceName,
            sourceListingUrl = listing.sourceListingUrl,
            packageName = listing.packageName,
            description = listing.description,
            iconUrl = listing.iconUrl,
            versionName = listing.versionName,
            versionCode = listing.versionCode,
            fileSize = listing.fileSize,
            fileSizeBytes = listing.fileSizeBytes,
            category = listing.category,
            downloadUrl = listing.downloadUrl,
            lastUpdated = listing.lastUpdated,
            androidRequirements = listing.androidRequirements,
            securityStatus = listing.securityStatus.name,
            developer = listing.developer,
            modInfo = listing.modInfo,
            ratingValue = listing.ratingValue,
            downloadCount = listing.downloadCount,
            dedupeKey = listing.dedupeKey,
            lastSeenAt = seenAt,
        )
    }
}

/** Screenshots kept in their own table so a listing row stays narrow. */
@Entity(
    tableName = "listing_screenshots",
    primaryKeys = ["listingId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ListingEntity::class,
            parentColumns = ["id"],
            childColumns = ["listingId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("listingId")],
)
data class ScreenshotEntity(
    val listingId: String,
    val position: Int,
    val url: String,
)

/** Download variants kept separately for the same reason. */
@Entity(
    tableName = "listing_variants",
    primaryKeys = ["listingId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ListingEntity::class,
            parentColumns = ["id"],
            childColumns = ["listingId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("listingId")],
)
data class VariantEntity(
    val listingId: String,
    val position: Int,
    val label: String,
    val versionName: String?,
    val fileSize: String?,
    val modInfo: String?,
    val downloadUrl: String?,
    val sourceListingUrl: String,
)

/**
 * A listing plus its children.
 *
 * `@Embedded` with `prefix = ""` keeps the column names flat, and the relation loads in the
 * same query, so a 12-item feed is one round trip rather than 37.
 */
data class ListingWithChildren(
    @Embedded val listing: ListingEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "listingId")
    val screenshots: List<ScreenshotEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "listingId")
    val variants: List<VariantEntity>,
) {
    fun toDomain(): AppListing = listing.toDomain(
        variants = variants.sortedBy { it.position }.map {
            AppVariant(
                label = it.label,
                versionName = it.versionName,
                fileSize = it.fileSize,
                modInfo = it.modInfo,
                downloadUrl = it.downloadUrl,
                sourceListingUrl = it.sourceListingUrl,
            )
        },
        screenshots = screenshots.sortedBy { it.position }.map { it.url },
    )
}

@Dao
interface ListingDao {

    @Transaction
    @Query("SELECT * FROM listings ORDER BY lastSeenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ListingWithChildren>>

    @Transaction
    @Query("SELECT * FROM listings WHERE id = :id")
    suspend fun findById(id: String): ListingWithChildren?

    @Transaction
    @Query("SELECT * FROM listings WHERE id = :id")
    fun observeById(id: String): Flow<ListingWithChildren?>

    @Transaction
    @Query("SELECT * FROM listings WHERE sourceId = :sourceId ORDER BY lastSeenAt DESC LIMIT :limit")
    fun observeBySource(sourceId: String, limit: Int): Flow<List<ListingWithChildren>>

    @Query("SELECT MAX(lastSeenAt) FROM listings")
    suspend fun lastSeenAt(): Long?

    @Query("SELECT COUNT(*) FROM listings")
    suspend fun count(): Int

    /**
     * Writes a page of listings in one transaction.
     *
     * Children are replaced rather than merged so a listing that drops a screenshot or a
     * variant does not keep the stale one.
     */
    @Transaction
    suspend fun upsertAll(
        listings: List<AppListing>,
        seenAt: Long,
        deleteChildren: Boolean = true,
    ) {
        if (listings.isEmpty()) return
        insertListings(listings.map { ListingEntity.fromDomain(it, seenAt) })
        if (deleteChildren) {
            val ids = listings.map { it.id }
            deleteScreenshotsFor(ids)
            deleteVariantsFor(ids)
        }
        insertScreenshots(
            listings.flatMap { listing ->
                listing.screenshots.mapIndexed { index, url ->
                    ScreenshotEntity(listing.id, index, url)
                }
            }
        )
        insertVariants(
            listings.flatMap { listing ->
                listing.variants.mapIndexed { index, variant ->
                    VariantEntity(
                        listingId = listing.id,
                        position = index,
                        label = variant.label,
                        versionName = variant.versionName,
                        fileSize = variant.fileSize,
                        modInfo = variant.modInfo,
                        downloadUrl = variant.downloadUrl,
                        sourceListingUrl = variant.sourceListingUrl,
                    )
                }
            }
        )
    }

    @Upsert
    suspend fun insertListings(listings: List<ListingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenshots(screenshots: List<ScreenshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<VariantEntity>)

    @Query("DELETE FROM listing_screenshots WHERE listingId IN (:ids)")
    suspend fun deleteScreenshotsFor(ids: List<String>)

    @Query("DELETE FROM listing_variants WHERE listingId IN (:ids)")
    suspend fun deleteVariantsFor(ids: List<String>)

    /** Keeps the cache bounded: drop the oldest rows beyond [keep]. */
    @Query(
        """
        DELETE FROM listings WHERE id NOT IN
        (SELECT id FROM listings ORDER BY lastSeenAt DESC LIMIT :keep)
        """
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM listings")
    suspend fun clear()

    @Query("DELETE FROM listings WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: String)
}

/** A listing the user pinned as a favourite or saved for later. */
@Entity(tableName = "library_entries")
data class LibraryEntryEntity(
    @PrimaryKey val listingId: String,
    val appName: String,
    val packageName: String?,
    val iconUrl: String?,
    val sourceId: String,
    val sourceName: String,
    val sourceListingUrl: String,
    val versionName: String?,
    val fileSize: String?,
    val isFavourite: Boolean,
    val isSaved: Boolean,
    val savedAt: Long,
) {
    fun toDomain(): AppListing = AppListing(
        id = listingId,
        appName = appName,
        sourceId = SourceId.fromKey(sourceId) ?: SourceId.MODYOLO,
        sourceName = sourceName,
        sourceListingUrl = sourceListingUrl,
        packageName = packageName,
        iconUrl = iconUrl,
        versionName = versionName,
        fileSize = fileSize,
    )

    companion object {
        fun fromDomain(
            listing: AppListing,
            isFavourite: Boolean,
            isSaved: Boolean,
            savedAt: Long,
        ) = LibraryEntryEntity(
            listingId = listing.id,
            appName = listing.appName,
            packageName = listing.packageName,
            iconUrl = listing.iconUrl,
            sourceId = listing.sourceId.name,
            sourceName = listing.sourceName,
            sourceListingUrl = listing.sourceListingUrl,
            versionName = listing.versionName,
            fileSize = listing.fileSize,
            isFavourite = isFavourite,
            isSaved = isSaved,
            savedAt = savedAt,
        )
    }
}

@Dao
interface LibraryDao {

    @Query("SELECT * FROM library_entries WHERE isFavourite = 1 ORDER BY savedAt DESC")
    fun observeFavourites(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries WHERE isSaved = 1 ORDER BY savedAt DESC")
    fun observeSaved(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM library_entries WHERE listingId = :id AND isFavourite = 1)")
    fun observeIsFavourite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM library_entries WHERE listingId = :id AND isSaved = 1)")
    fun observeIsSaved(id: String): Flow<Boolean>

    @Query("SELECT * FROM library_entries WHERE listingId = :id")
    suspend fun find(id: String): LibraryEntryEntity?

    @Upsert
    suspend fun upsert(entry: LibraryEntryEntity)

    @Query("DELETE FROM library_entries WHERE listingId = :id")
    suspend fun delete(id: String)

    /**
     * Removes a row that is neither favourited nor saved any more, so the library does not
     * accumulate entries the user has no way to see.
     */
    @Query("DELETE FROM library_entries WHERE listingId = :id AND isFavourite = 0 AND isSaved = 0")
    suspend fun deleteIfUnused(id: String)
}

/** A previously searched term. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val termKey: String,
    val displayTerm: String,
    val searchedAt: Long,
)

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE termKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query(
        """
        DELETE FROM search_history WHERE termKey NOT IN
        (SELECT termKey FROM search_history ORDER BY searchedAt DESC LIMIT :keep)
        """
    )
    suspend fun trimTo(keep: Int)
}
