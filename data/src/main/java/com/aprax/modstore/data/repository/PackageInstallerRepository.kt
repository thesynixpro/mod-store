package com.aprax.modstore.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.aprax.modstore.domain.repository.ApkMetadata
import com.aprax.modstore.domain.repository.InstallResult
import com.aprax.modstore.domain.repository.InstallStatus
import com.aprax.modstore.domain.repository.InstallerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installation through Android's own PackageInstaller.
 *
 * There is no silent-install path anywhere in this class. On API 31+ the app opens a
 * PackageInstaller session and calls [PackageInstaller.commit], which shows Android's
 * confirmation dialog — the user decides. On older versions the APK is shared with the
 * system installer through a scoped `content://` URI, which again shows the same dialog.
 *
 * Before either happens the file is read with the package manager, so a corrupt download or
 * a file that is not really an APK is rejected with a clear message instead of a cryptic
 * system error.
 */
@Singleton
class PackageInstallerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstallerRepository {

    override fun canRequestInstall(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override suspend fun install(apkPath: String): InstallResult = withContext(Dispatchers.IO) {
        val file = File(apkPath)

        if (!file.exists() || !file.isFile) {
            return@withContext InstallResult(
                status = InstallStatus.FAILED,
                message = "The downloaded file is no longer on this device.",
            )
        }
        if (!file.canRead()) {
            return@withContext InstallResult(
                status = InstallStatus.FAILED,
                message = "The downloaded file cannot be read.",
            )
        }

        // Reject anything that is not a real package before handing it to the system.
        val metadata = inspectInternal(file).getOrElse { error ->
            return@withContext InstallResult(
                status = InstallStatus.FAILED,
                message = error.message ?: "This file is not a valid APK.",
            )
        }

        if (!canRequestInstall()) {
            return@withContext InstallResult(
                status = InstallStatus.NEEDS_UNKNOWN_SOURCES_PERMISSION,
                message = "Android needs permission for this app to install packages.",
                packageName = metadata.packageName,
            )
        }

        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            commitSession(file, metadata.packageName)
        } else {
            launchLegacyInstaller(file, metadata.packageName)
        }
    }

    /**
     * API 31+: open a PackageInstaller session and commit it. Android shows its own
     * confirmation sheet and reports the outcome to
     * [com.aprax.modstore.data.install.InstallResultReceiver].
     */
    private fun commitSession(file: File, packageName: String): InstallResult {
        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(packageName)
                // No extra install-time permissions are requested on the user's behalf.
            }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("apk", 0, file.length()).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                        session.fsync(output)
                    }
                }

                val callbackIntent = Intent(context, com.aprax.modstore.data.install.InstallResultReceiver::class.java)
                    .setAction(ACTION_INSTALL_RESULT)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_MUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    flags,
                )
                session.commit(pendingIntent.intentSender)
            }

            InstallResult(
                status = InstallStatus.PENDING_USER_CONFIRMATION,
                message = "Confirm the installation in the dialog Android just opened.",
                packageName = packageName,
            )
        } catch (error: SecurityException) {
            InstallResult(
                status = InstallStatus.NEEDS_UNKNOWN_SOURCES_PERMISSION,
                message = "Android blocked the install request. Allow installs for this app and retry.",
                packageName = packageName,
            )
        } catch (error: Exception) {
            InstallResult(
                status = InstallStatus.FAILED,
                message = "Android could not start the installation: ${error.message ?: "unknown error"}",
                packageName = packageName,
            )
        }
    }

    /**
     * API 24-30: hand the APK to the system installer through a FileProvider URI. This is
     * the only supported path on those versions and it also ends in Android's own consent
     * dialog.
     */
    private fun launchLegacyInstaller(file: File, packageName: String): InstallResult {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.apk-provider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            InstallResult(
                status = InstallStatus.PENDING_USER_CONFIRMATION,
                message = "Confirm the installation in the dialog Android just opened.",
                packageName = packageName,
            )
        } catch (error: Exception) {
            InstallResult(
                status = InstallStatus.FAILED,
                message = "Could not open the installer: ${error.message ?: "unknown error"}",
                packageName = packageName,
            )
        }
    }

    override suspend fun inspect(apkPath: String): Result<ApkMetadata> = withContext(Dispatchers.IO) {
        runCatching { inspectInternal(File(apkPath)).getOrThrow() }
    }

    private fun inspectInternal(file: File): Result<ApkMetadata> {
        if (!file.exists()) {
            return Result.failure(IllegalStateException("File not found."))
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo: PackageInfo = try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: return Result.failure(
                    IllegalStateException("This file is not a valid Android package.")
                )
        } catch (error: Exception) {
            return Result.failure(
                IllegalStateException("This file is not a valid Android package.", error)
            )
        }

        val packageName = packageInfo.packageName
            ?: return Result.failure(IllegalStateException("The package has no identifier."))

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val signers = readSignerDigests(packageInfo)

        return runCatching {
            ApkMetadata(
                packageName = packageName,
                versionName = packageInfo.versionName,
                versionCode = versionCode,
                label = packageInfo.applicationInfo?.let { info ->
                    runCatching { context.packageManager.getApplicationLabel(info).toString() }
                        .getOrNull()
                },
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    packageInfo.applicationInfo?.minSdkVersion ?: 0
                } else {
                    0
                },
                targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: 0,
                sha256 = file.sha256(),
                signerSha256 = signers,
                // A debug certificate is a fact worth surfacing: it means the build was not
                // signed for distribution, which the user should know before installing.
                debugSigned = signers.isNotEmpty() && isDebugCertificate(packageInfo),
            )
        }.recoverCatching { error ->
            throw IllegalStateException("Could not read the package metadata.", error)
        }
    }

    private fun readSignerDigests(packageInfo: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        } ?: return emptyList()

        return signatures.mapNotNull { signature ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .toHex()
            }.getOrNull()
        }
    }

    /**
     * Google's public debug keystore certificate is used by every default debug build, so
     * its presence is a reliable signal that the APK is not a production release.
     *
     * The check is a comparison against that certificate's published SHA-1 fingerprint. It
     * only ever *warns*: a matching certificate says "this was signed with the shared debug
     * key", never anything about what the APK contains.
     */
    private fun isDebugCertificate(packageInfo: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val signers = packageInfo.signingInfo?.apkContentsSigners ?: return false
        return signers.any { signature ->
            runCatching {
                MessageDigest.getInstance("SHA-1")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull() == ANDROID_DEBUG_KEY_SHA1
        }
    }

    override fun isInstalled(packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun installedVersionCode(packageName: String): Long? = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val ACTION_INSTALL_RESULT = "com.aprax.modstore.INSTALL_RESULT"

        /** SHA-1 of the certificate in the standard Android debug keystore. */
        const val ANDROID_DEBUG_KEY_SHA1 = "61ed377e85d386a8dfee6b864bd85b0bfaa5af81"
    }
}
