package com.aprax.modstore.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aprax.modstore.data.download.DownloadWorker
import com.aprax.modstore.data.download.MutableProgressUpdater
import com.aprax.modstore.data.local.DownloadDao
import com.aprax.modstore.data.local.DownloadEntity
import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.repository.DownloadRepository
import com.aprax.modstore.domain.repository.DownloadState
import com.aprax.modstore.domain.repository.DownloadStatus
import com.aprax.modstore.domain.repository.DownloadedApk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Download orchestration.
 *
 * This class owns the queue and the bookkeeping; the actual transfer happens in
 * [DownloadWorker] under WorkManager, so a download survives the screen closing or the
 * process being reclaimed. Live byte counts come from [MutableProgressUpdater] and are
 * merged into the persisted rows here.
 *
 * A download is only ever enqueued for a URL the source itself published — callers pass
 * [AppListing.hasDirectDownload] into account and fall back to "visit source" otherwise.
 */
@Singleton
class DefaultDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val progressUpdater: MutableProgressUpdater,
) : DownloadRepository {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Row IDs are UUID strings; ignore legacy rows whose ID is not a valid WorkManager UUID. */
    private fun cancelWork(id: String) {
        runCatching { UUID.fromString(id) }.getOrNull()?.let(workManager::cancelWorkById)
    }

    override fun observeDownloads(): Flow<List<DownloadState>> =
        combine(downloadDao.observeAll(), progressUpdater.progress) { rows, liveProgress ->
            rows.map { row -> row.toDomain(liveProgress[row.id]) }
        }

    override fun observeDownload(listingId: String): Flow<DownloadState?> =
        combine(
            downloadDao.observeForListing(listingId),
            progressUpdater.progress,
        ) { row, liveProgress ->
            row?.toDomain(liveProgress[row.id])
        }

    override fun history(): Flow<List<DownloadedApk>> =
        downloadDao.observeInstallable().map { rows -> rows.mapNotNull { it.toHistory() } }

    override suspend fun enqueue(
        listing: AppListing,
        url: String,
        wifiOnly: Boolean,
    ): Result<String> {
        if (!url.startsWith("https://", ignoreCase = true)) {
            // The source handed out a non-HTTPS artifact URL. Refusing is the safe course:
            // an APK fetched over plain HTTP can be swapped in transit.
            return Result.failure(AppError.Malformed(listing.sourceName))
        }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val inserted = runCatching {
            downloadDao.insert(DownloadEntity.queued(id, listing, url, now))
        }

        if (inserted.isFailure) {
            return Result.failure(
                AppError.Storage(
                    cause = inserted.exceptionOrNull() ?: IllegalStateException("unknown")
                )
            )
        }

        return runCatching {
            enqueueWork(id, wifiOnly)
            id
        }.recoverCatching { cause ->
            // The row must not linger if the work never entered the queue.
            downloadDao.delete(id)
            throw cause
        }
    }

    override suspend fun pause(listingId: String): Result<Unit> = runCatching {
        val row = downloadDao.findByListing(listingId) ?: return@runCatching
        // WorkManager has no portable pause, so pausing cancels the work and keeps the row so
        // the user can resume it. The partial file is removed by the worker on cancellation.
        cancelWork(row.id)
        downloadDao.updateStatus(row.id, DownloadStatus.PAUSED.name, null, System.currentTimeMillis())
        progressUpdater.clear(row.id)
    }

    override suspend fun resume(listingId: String): Result<Unit> = runCatching {
        val row = downloadDao.findByListing(listingId) ?: return@runCatching
        downloadDao.updateStatus(row.id, DownloadStatus.QUEUED.name, null, System.currentTimeMillis())
        enqueueWork(row.id, wifiOnly = false)
    }

    override suspend fun cancel(listingId: String): Result<Unit> = runCatching {
        val row = downloadDao.findByListing(listingId) ?: return@runCatching
        cancelWork(row.id)
        row.localFilePath?.let { path -> runCatching { File(path).delete() } }
        downloadDao.updateStatus(row.id, DownloadStatus.CANCELLED.name, null, System.currentTimeMillis())
        progressUpdater.clear(row.id)
    }

    override suspend fun retry(listingId: String): Result<Unit> = runCatching {
        val row = downloadDao.findByListing(listingId) ?: return@runCatching
        downloadDao.updateStatus(row.id, DownloadStatus.QUEUED.name, null, System.currentTimeMillis())
        enqueueWork(row.id, wifiOnly = false)
    }

    override suspend fun removeFromHistory(id: String): Result<Unit> = runCatching {
        cancelWork(id)
        progressUpdater.clear(id)
        downloadDao.delete(id)
    }

    override suspend fun deleteFile(download: DownloadedApk): Result<Unit> = runCatching {
        File(download.localFilePath).delete()
        downloadDao.delete(download.id)
        progressUpdater.clear(download.id)
    }

    override suspend fun storageUsedBytes(): Long = downloadDao.storageUsedBytes()

    private fun enqueueWork(downloadId: String, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    // A download must not run without a network, and honour the WiFi-only
                    // preference when the user set it.
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG_DOWNLOAD)
            .build()

        workManager.enqueueUniqueWork(
            uniqueName(downloadId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun uniqueName(downloadId: String) = "apk-download-$downloadId"

    private fun DownloadEntity.toDomain(liveProgress: com.aprax.modstore.data.download.TransferProgress?): DownloadState {
        val status = statusOrNull() ?: DownloadStatus.FAILED
        val bytes = liveProgress?.bytes?.takeIf { it > 0 } ?: bytesDownloaded
        val total = liveProgress?.total ?: totalBytes

        return DownloadState(
            listingId = listingId,
            appName = appName,
            sourceId = sourceIdOrNull() ?: SourceId.MODYOLO,
            status = status,
            bytesDownloaded = bytes,
            totalBytes = total,
            progress = liveProgress?.fraction ?: fractionOf(bytes, total),
            localFilePath = localFilePath,
            sha256 = sha256,
            error = error,
            updatedAt = updatedAt,
        )
    }

    private fun fractionOf(bytes: Long, total: Long?): Float? {
        val size = total ?: return null
        if (size <= 0L || bytes <= 0L) return null
        return (bytes.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun DownloadEntity.toHistory(): DownloadedApk? {
        val path = localFilePath ?: return null
        // A row whose file the user already deleted is not history worth showing.
        if (!File(path).exists()) return null
        return DownloadedApk(
            id = id,
            listingId = listingId,
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            sourceId = sourceIdOrNull() ?: SourceId.MODYOLO,
            sourceListingUrl = sourceListingUrl,
            localFilePath = path,
            fileSizeBytes = totalBytes ?: 0L,
            sha256 = sha256,
            completedAt = updatedAt,
            installed = installed,
        )
    }

    companion object {
        const val TAG_DOWNLOAD = "apk-download"
    }
}
