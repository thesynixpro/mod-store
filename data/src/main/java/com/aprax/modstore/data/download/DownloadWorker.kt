package com.aprax.modstore.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.aprax.modstore.data.local.DownloadDao
import com.aprax.modstore.domain.repository.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams a single APK to private storage.
 *
 * What this worker guarantees:
 *  * **HTTPS only.** A non-HTTPS URL is refused before any bytes are read.
 *  * **Type validation.** A response that is not an APK/ZIP — an HTML error page is the
 *    classic case — is rejected by inspecting the first bytes, not by trusting the
 *    Content-Type header alone.
 *  * **Size sanity.** A declared length that would overflow the per-download cap aborts
 *    the transfer to avoid filling the device.
 *  * **Atomic publish.** Bytes go to a `.part` file, and only a fully verified download is
 *    renamed into place, so a partial file can never be handed to the installer.
 *  * **Integrity record.** SHA-256 is computed while streaming, so the details screen can
 *    show a value the user can compare against the source.
 *  * **Cancellation.** WorkManager cancellation deletes the partial file.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient,
    private val mutableProgress: MutableProgressUpdater,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure(failureData("Missing download id."))

        val record = downloadDao.findById(downloadId)
            ?: return@withContext Result.failure(failureData("Download record not found."))

        // A URL that is not HTTPS is refused outright: an APK fetched over plain HTTP can be
        // swapped in transit, which is exactly the risk this app must not introduce.
        if (!record.downloadUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext fail(downloadId, "Only HTTPS downloads are permitted.")
        }

        val targetDir = File(applicationContext.filesDir, "downloads").apply { mkdirs() }
        val safeName = sanitiseFileName("${record.appName}-${record.versionName ?: "apk"}")
        val finalFile = File(targetDir, "$safeName.apk")
        val partFile = File(targetDir, "$safeName.apk.part")

        var bytesWritten = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            val request = Request.Builder()
                .url(record.downloadUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext fail(
                        downloadId,
                        "The source refused the download (HTTP ${response.code}).",
                    )
                }

                val contentLength = response.body?.contentLength()?.takeIf { it > 0 }
                if (contentLength != null && contentLength > MAX_APK_BYTES) {
                    return@withContext fail(
                        downloadId,
                        "This file is larger than the app's per-download limit.",
                    )
                }

                val body = response.body
                    ?: return@withContext fail(downloadId, "The source sent an empty response.")

                response.header("Content-Type")?.let { contentType ->
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        return@withContext fail(
                            downloadId,
                            "The source returned a web page instead of an APK.",
                        )
                    }
                }

                downloadDao.updateProgress(
                    id = downloadId,
                    bytes = 0L,
                    total = contentLength,
                    status = DownloadStatus.RUNNING.name,
                    now = System.currentTimeMillis(),
                )

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var firstChunk = true
                        var lastProgressAt = 0L

                        while (true) {
                            if (isStopped) {
                                partFile.delete()
                                return@withContext Result.failure(
                                    failureData("Download cancelled.")
                                )
                            }

                            val read = input.read(buffer)
                            if (read <= 0) break

                            // Validate the file signature on the very first bytes.
                            if (firstChunk) {
                                if (!looksLikeApk(buffer, read)) {
                                    partFile.delete()
                                    return@withContext fail(
                                        downloadId,
                                        "The downloaded file is not a valid APK.",
                                    )
                                }
                                firstChunk = false
                            }

                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytesWritten += read

                            if (bytesWritten > MAX_APK_BYTES) {
                                output.close()
                                partFile.delete()
                                return@withContext fail(
                                    downloadId,
                                    "This file exceeded the app's per-download limit.",
                                )
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt > PROGRESS_INTERVAL_MS) {
                                lastProgressAt = now
                                downloadDao.updateProgress(
                                    id = downloadId,
                                    bytes = bytesWritten,
                                    total = contentLength,
                                    status = DownloadStatus.RUNNING.name,
                                    now = now,
                                )
                                mutableProgress.update(
                                    downloadId = downloadId,
                                    bytes = bytesWritten,
                                    total = contentLength,
                                )
                                setProgress(
                                    Data.Builder()
                                        .putLong(KEY_BYTES, bytesWritten)
                                        .putLong(KEY_TOTAL, contentLength ?: -1L)
                                        .build()
                                )
                            }
                        }
                    }
                }
            }

            if (bytesWritten == 0L) {
                partFile.delete()
                return@withContext fail(downloadId, "The source sent no data.")
            }

            // A declared length that does not match what arrived means a truncated transfer.
            val expected = downloadDao.findById(downloadId)?.totalBytes
            if (expected != null && expected > 0 && bytesWritten < expected) {
                partFile.delete()
                return@withContext fail(
                    downloadId,
                    "The download ended early ($bytesWritten of $expected bytes).",
                )
            }

            // Publish atomically: only a complete file is ever visible to the installer.
            if (finalFile.exists()) finalFile.delete()
            val renamed = partFile.renameTo(finalFile)
            if (!renamed) {
                partFile.delete()
                return@withContext fail(downloadId, "Could not finalise the downloaded file.")
            }

            val sha256 = digest.digest().toHex()

            downloadDao.complete(
                id = downloadId,
                status = DownloadStatus.READY_TO_INSTALL.name,
                path = finalFile.absolutePath,
                size = bytesWritten,
                sha256 = sha256,
                now = System.currentTimeMillis(),
            )

            Result.success(
                Data.Builder()
                    .putString(KEY_DOWNLOAD_ID, downloadId)
                    .putString(KEY_LOCAL_PATH, finalFile.absolutePath)
                    .build()
            )
        } catch (error: IOException) {
            partFile.delete()
            fail(downloadId, "Network error while downloading: ${error.message ?: "unknown"}")
        } catch (error: Exception) {
            partFile.delete()
            fail(downloadId, "Download failed: ${error.message ?: "unknown error"}")
        }
    }

    private suspend fun fail(downloadId: String, message: String): Result {
        downloadDao.updateStatus(
            id = downloadId,
            status = DownloadStatus.FAILED.name,
            error = message,
            now = System.currentTimeMillis(),
        )
        mutableProgress.clear(downloadId)
        return Result.failure(failureData(message))
    }

    private fun failureData(message: String) = Data.Builder()
        .putString(KEY_ERROR, message)
        .build()

    /**
     * An APK is a ZIP archive, so it must start with "PK\x03\x04" (or the empty-archive
     * signature). Checking bytes is far more reliable than the Content-Type header, which
     * sources frequently get wrong.
     */
    private fun looksLikeApk(buffer: ByteArray, length: Int): Boolean {
        if (length < 4) return false
        return buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte() &&
            (buffer[2] == 0x03.toByte() || buffer[2] == 0x05.toByte() || buffer[2] == 0x07.toByte())
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte) }

    /** Keeps the filename filesystem-safe and short. */
    private fun sanitiseFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.')
        return cleaned.take(80).ifBlank { "download" }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_ERROR = "error"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"

        /** 400 MB: generous for a mobile game, small enough to protect device storage. */
        const val MAX_APK_BYTES = 400L * 1024 * 1024
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 250L
        const val USER_AGENT =
            "ApraxModsStore/1.0 (Android; APK download)"
    }
}
