package com.aprax.modstore.domain.repository

import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.Page
import com.aprax.modstore.domain.model.SortOrder
import com.aprax.modstore.domain.model.SourceId

/**
 * Download state, owned by the download manager and surfaced to the UI.
 *
 * [progress] is 0f..1f when known. Speed and remaining time are null when they cannot be
 * computed honestly (for example before a content length is known), rather than being
 * shown as zero.
 */
data class DownloadState(
    val listingId: String,
    val appName: String,
    val sourceId: SourceId,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val progress: Float? = null,
    val bytesPerSecond: Long? = null,
    val remainingSeconds: Long? = null,
    val localFilePath: String? = null,
    /** sha-256 of the finished file, computed locally after the bytes are on disk. */
    val sha256: String? = null,
    val error: String? = null,
    val updatedAt: Long = 0L,
)

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,

    /** Finished downloading but not yet installed. */
    READY_TO_INSTALL,

    /** The user installed it (or it was already installed). */
    INSTALLED;

    val isFinished: Boolean get() = this == COMPLETED || this == READY_TO_INSTALL || this == INSTALLED
    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == PAUSED
}

/** A completed download kept in history. */
data class DownloadedApk(
    val id: String,
    val listingId: String,
    val appName: String,
    val packageName: String?,
    val versionName: String?,
    val sourceId: SourceId,
    /** The listing page on the source site, carried through so history can reopen it. */
    val sourceListingUrl: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val sha256: String?,
    val completedAt: Long,
    val installed: Boolean,
)

/** Lifecycle state of an install handed to Android's PackageInstaller. */
enum class InstallStatus {
    IDLE,

    /** Waiting on the user's confirmation dialog. Never bypassed. */
    PENDING_USER_CONFIRMATION,

    /** User confirmed; the package manager is applying it. */
    INSTALLING,

    INSTALLED,

    /** Settings must grant this app the right to request installs. */
    NEEDS_UNKNOWN_SOURCES_PERMISSION,

    FAILED,

    CANCELLED;

    /** True when Android is showing or about to show its own consent UI. */
    val awaitingUser: Boolean get() = this == PENDING_USER_CONFIRMATION
}

/** The outcome of asking Android to install a downloaded APK. */
data class InstallResult(
    val status: InstallStatus,
    val message: String,
    val packageName: String? = null,
)

/**
 * Download and installation surface consumed by the feature modules.
 */
interface DownloadRepository {

    /** Live state for everything currently downloading or recently finished. */
    fun observeDownloads(): kotlinx.coroutines.flow.Flow<List<DownloadState>>

    fun observeDownload(listingId: String): kotlinx.coroutines.flow.Flow<DownloadState?>

    /** Finished downloads, newest first. */
    fun history(): kotlinx.coroutines.flow.Flow<List<DownloadedApk>>

    /**
     * Enqueues a download. [url] must be a direct artifact URL that the source publishes;
     * if the listing has none, callers must use the external-source path instead.
     */
    suspend fun enqueue(listing: AppListing, url: String, wifiOnly: Boolean): Result<String>

    suspend fun pause(listingId: String): Result<Unit>
    suspend fun resume(listingId: String): Result<Unit>
    suspend fun cancel(listingId: String): Result<Unit>
    suspend fun retry(listingId: String): Result<Unit>
    suspend fun removeFromHistory(id: String): Result<Unit>
    suspend fun deleteFile(download: DownloadedApk): Result<Unit>

    /** Total bytes currently used by downloaded APKs. */
    suspend fun storageUsedBytes(): Long
}

/**
 * Installation through Android's own PackageInstaller.
 *
 * There is deliberately no silent-install path: every method here ends in Android's
 * consent UI, and the implementation refuses to proceed without the system permission.
 */
interface InstallerRepository {

    /** Whether this app may currently request package installs on this device. */
    fun canRequestInstall(): Boolean

    /**
     * Verifies the APK is a readable package, reads its identity, then hands it to
     * PackageInstaller. Never installs without the user's explicit confirmation.
     */
    suspend fun install(apkPath: String): InstallResult

    /** Reads package name, version and signing certificate from an APK on disk. */
    suspend fun inspect(apkPath: String): Result<ApkMetadata>

    /** True when a package with this name is already installed on the device. */
    fun isInstalled(packageName: String): Boolean

    /** Installed version code, or null when absent. */
    fun installedVersionCode(packageName: String): Long?
}

/** Facts read from an APK file on disk. */
data class ApkMetadata(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val label: String?,
    val minSdk: Int,
    val targetSdk: Int,
    /** SHA-256 of the file contents, computed locally. */
    val sha256: String,
    /** SHA-256 fingerprints of the signing certificates found in the APK. */
    val signerSha256: List<String>,
    val debugSigned: Boolean,
)
