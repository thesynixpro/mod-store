package com.aprax.modstore.data.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.aprax.modstore.data.local.DownloadDao
import com.aprax.modstore.domain.repository.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the outcome of a PackageInstaller session.
 *
 * Android delivers the result to the PendingIntent a session was committed with, which
 * [com.aprax.modstore.data.repository.PackageInstallerRepository] points at this receiver.
 * It has one job: record whether the package actually went in, so the Downloads screen stops
 * claiming "ready to install" for an app the user declined, and so a real Android error
 * message can be shown instead of a generic failure.
 *
 * It is not exported, it installs nothing itself, and it never suppresses the system's
 * consent dialog.
 */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var downloadDao: DownloadDao

    override fun onReceive(context: Context, intent: Intent) {
        // Hilt populates the injected fields before onReceive is called; BroadcastReceiver
        // declares this method abstract, so there is no super implementation to invoke.

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        // A receiver must return quickly, so the database write is handed off with
        // goAsync() rather than run on the main thread.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val record = downloadDao.findByPackage(packageName)
                if (record != null) {
                    val now = System.currentTimeMillis()
                    when (status) {
                        PackageInstaller.STATUS_SUCCESS ->
                            downloadDao.markInstalled(
                                id = record.id,
                                installed = true,
                                status = DownloadStatus.INSTALLED.name,
                                now = now,
                            )

                        PackageInstaller.STATUS_FAILURE_ABORTED ->
                            // The user dismissed the dialog. That is not an error worth
                            // alarming them about; the app is still ready to install.
                            downloadDao.markInstalled(
                                id = record.id,
                                installed = false,
                                status = DownloadStatus.READY_TO_INSTALL.name,
                                now = now,
                            )

                        else ->
                            downloadDao.updateStatus(
                                id = record.id,
                                status = DownloadStatus.READY_TO_INSTALL.name,
                                error = message
                                    ?: "Android reported the install did not complete.",
                                now = now,
                            )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}