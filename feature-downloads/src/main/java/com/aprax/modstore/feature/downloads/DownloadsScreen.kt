package com.aprax.modstore.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.core.ui.AppIcon
import com.aprax.modstore.core.ui.EmptyState
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.SourceInfo
import com.aprax.modstore.domain.repository.DownloadState
import com.aprax.modstore.domain.repository.DownloadStatus
import com.aprax.modstore.domain.repository.DownloadedApk
import com.aprax.modstore.domain.usecase.ByteFormat
import com.aprax.modstore.feature.downloads.R

@Composable
fun DownloadsScreen(
    onOpenListing: (DownloadedApk) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = Spacing.medium,
            bottom = Spacing.xlarge,
        ),
    ) {
        val active = state.activeDownloads
        val history = state.historyDownloads

        if (active.isNotEmpty()) {
            item(key = "active-header") {
                SectionHeader(
                    title = stringResource(R.string.downloads_queue),
                    subtitle = stringResource(
                        R.string.download_storage_used,
                        ByteFormat.describeBytes(state.storageUsedBytes),
                    ),
                )
            }
            items(active, key = { it.listingId }) { download ->
                ActiveDownloadRow(
                    download = download,
                    onPause = { viewModel.pause(download.listingId) },
                    onResume = { viewModel.resume(download.listingId) },
                    onCancel = { viewModel.cancel(download.listingId) },
                    onRetry = { viewModel.retry(download.listingId) },
                )
            }
        }

        if (history.isNotEmpty()) {
            item(key = "history-header") {
                SectionHeader(
                    title = stringResource(R.string.downloads_history),
                    modifier = Modifier.padding(top = Spacing.large),
                )
            }
            items(history, key = { it.id }) { apk ->
                HistoryRow(
                    apk = apk,
                    onOpenListing = { onOpenListing(apk) },
                    onDelete = { viewModel.deleteFile(apk) },
                    onRemove = { viewModel.removeFromHistory(apk) },
                )
            }
        }

        if (active.isEmpty() && history.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.downloads_empty),
                    message = stringResource(R.string.downloads_empty_message),
                    icon = Icons.Filled.Download,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActiveDownloadRow(
    download: DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            iconUrl = null,
            contentDescription = download.appName,
            modifier = Modifier.size(56.dp),
        )

        Spacer(Modifier.width(Spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.appName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))

            when (download.status) {
                DownloadStatus.QUEUED, DownloadStatus.PAUSED -> {
                    Text(
                        text = when (download.status) {
                            DownloadStatus.QUEUED -> stringResource(R.string.download_queued)
                            else -> stringResource(R.string.download_paused)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DownloadStatus.RUNNING -> {
                    val progress = download.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = download.bytesPerSecond?.let { speed ->
                                stringResource(
                                    R.string.download_progress_speed,
                                    ByteFormat.describeBytes(download.bytesDownloaded),
                                    ByteFormat.describeBytes(download.totalBytes ?: 0L),
                                    ByteFormat.describeBytes(speed),
                                )
                            } ?: stringResource(
                                R.string.download_progress,
                                ByteFormat.describeBytes(download.bytesDownloaded),
                                ByteFormat.describeBytes(download.totalBytes ?: 0L),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(Spacing.small))
                            Text(
                                text = stringResource(R.string.download_queued),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                DownloadStatus.READY_TO_INSTALL, DownloadStatus.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.small))
                        Text(
                            text = stringResource(R.string.download_ready),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.small))
                        Text(
                            text = download.error
                                ?: stringResource(R.string.download_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                DownloadStatus.INSTALLED -> Unit
            }

            if (download.status == DownloadStatus.FAILED) {
                Spacer(Modifier.height(Spacing.small))
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.download_retry))
                }
            }
        }

        when (download.status) {
            DownloadStatus.RUNNING -> {
                IconButton(onClick = onPause) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.download_pause),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DownloadStatus.PAUSED, DownloadStatus.QUEUED -> {
                IconButton(onClick = onResume) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.download_resume),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> Unit
        }

        if (download.status.isActive) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.download_cancel),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    apk: DownloadedApk,
    onOpenListing: () -> Unit,
    onDelete: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            iconUrl = null,
            contentDescription = apk.appName,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(Spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = apk.appName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    apk.versionName?.let { "v$it" },
                    ByteFormat.describeBytes(apk.fileSizeBytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (apk.installed) {
                    stringResource(R.string.download_installed)
                } else {
                    stringResource(R.string.download_ready)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (apk.installed) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
        IconButton(onClick = onOpenListing) {
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = stringResource(R.string.download_open_listing),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.download_delete),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
