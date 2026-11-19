package com.aprax.modstore.feature.details

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.core.ui.AppIcon
import com.aprax.modstore.core.ui.DetailFactRow
import com.aprax.modstore.core.ui.DetailsSkeleton
import com.aprax.modstore.core.ui.MetaPill
import com.aprax.modstore.core.ui.SectionHeader
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.core.ui.sourceAccent
import com.aprax.modstore.core.util.ExternalLinks
import com.aprax.modstore.core.util.PackageNames
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.AppVariant
import com.aprax.modstore.domain.model.SecurityStatus
import com.aprax.modstore.domain.repository.DownloadStatus
import com.aprax.modstore.domain.usecase.ByteFormat
import com.aprax.modstore.domain.usecase.RelativeDate
import com.aprax.modstore.feature.details.R

@Composable
fun DetailsScreen(
    listing: AppListing,
    onBack: () -> Unit,
    wifiOnly: Boolean = false,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(listing.id) {
        viewModel.load(listing)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val uiState = state.state) {
            is UiState.Loading -> DetailsSkeleton(
                modifier = Modifier.padding(top = Spacing.xlarge),
            )

            is UiState.Ready -> {
                val app = uiState.data
                DetailsContent(
                    app = app,
                    state = state,
                    onBack = onBack,
                    onToggleFavourite = viewModel::toggleFavourite,
                    onToggleSaved = viewModel::toggleSaved,
                    onDownload = { url ->
                        viewModel.download(
                            directUrl = url,
                            wifiOnly = wifiOnly,
                        )
                    },
                    onOpenSource = { ExternalLinks.openSourceSite(context, app.sourceListingUrl) },
                    onOpenPlayStore = { pkg ->
                        ExternalLinks.openPlayStore(context, pkg)
                    },
                    onCopyChecksum = { checksum ->
                        val manager = remember { LocalContext.current.getSystemService(ClipboardManager::class.java) }
                        manager.setPrimaryClip(ClipData.newPlainText("checksum", checksum))
                    },
                )
            }

            is UiState.Failed -> Unit

            is UiState.Stale -> Unit
        }
    }
}

@Composable
private fun DetailsContent(
    app: AppListing,
    state: DetailsUiState,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleSaved: () -> Unit,
    onDownload: (String) -> Unit,
    onOpenSource: () -> Unit,
    onOpenPlayStore: (String?) -> Unit,
    onCopyChecksum: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.small, vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (state.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.details_favourite),
                    tint = if (state.isFavourite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (state.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(R.string.details_saved),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Hero
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                iconUrl = app.iconUrl,
                contentDescription = app.appName,
                modifier = Modifier.size(Spacing.iconHero),
                cornerRadius = 26,
            )
            Spacer(Modifier.width(Spacing.regular))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                app.versionName?.let {
                    Text(
                        text = "v$it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
                    app.ratingValue?.let {
                        MetaPill(
                            text = "$it",
                            leadingIcon = Icons.Filled.Star,
                        )
                    }
                    app.fileSize?.let { MetaPill(text = it) }
                    app.sourceName.let { sourceName ->
                        MetaPill(
                            text = sourceName,
                            tint = sourceAccent(sourceName),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.regular))

        // Installed / update badge
        val installedVersionCode = state.installedVersionCode
        val updateAvailable = installedVersionCode != null &&
            app.versionCode != null &&
            installedVersionCode < app.versionCode
        val installed = installedVersionCode != null

        if (installed) {
            val message = when {
                updateAvailable -> stringResource(R.string.details_update_available)
                else -> stringResource(R.string.details_installed_version, app.versionName ?: "?")
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal),
                color = if (updateAvailable) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.medium),
                )
            }
            Spacer(Modifier.height(Spacing.small))
        }

        // Actions
        val directUrl = app.downloadUrl
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            if (directUrl != null && !directUrl.isBlank()) {
                Button(
                    onClick = { onDownload(directUrl) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.details_download))
                }
            } else {
                OutlinedButton(
                    onClick = onOpenSource,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.details_visit_source))
                }
            }
            app.packageName?.let { pkg ->
                if (PackageNames.isValid(pkg)) {
                    OutlinedButton(
                        onClick = { onOpenPlayStore(pkg) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.details_play_store))
                    }
                }
            }
        }

        if (directUrl == null || directUrl.isBlank()) {
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = stringResource(R.string.details_no_direct_download),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
        }

        // Live download state
        val download = state.download
        if (download != null && download.status.isActive) {
            Spacer(Modifier.height(Spacing.medium))
            DownloadStatusBlock(download = download)
        }

        Spacer(Modifier.height(Spacing.regular))

        // Facts
        SectionHeader(title = stringResource(R.string.details_facts))
        Column(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)) {
            DetailFactRow(
                label = stringResource(R.string.details_source),
                value = app.sourceName,
                icon = Icons.Filled.OpenInNew,
            )
            DetailFactRow(
                label = stringResource(R.string.details_version),
                value = app.versionName,
            )
            DetailFactRow(
                label = stringResource(R.string.details_size),
                value = app.fileSize ?: ByteFormat.format(app.fileSizeBytes),
            )
            DetailFactRow(
                label = stringResource(R.string.details_category),
                value = app.category,
            )
            DetailFactRow(
                label = stringResource(R.string.details_developer),
                value = app.developer,
            )
            DetailFactRow(
                label = stringResource(R.string.details_updated),
                value = RelativeDate.describe(app.lastUpdated),
            )
            DetailFactRow(
                label = stringResource(R.string.details_requirements),
                value = app.androidRequirements,
            )
        }

        // Security note
        val securityNote = when (app.securityStatus) {
            SecurityStatus.CHECKSUM_AVAILABLE -> stringResource(R.string.details_security_checksum)
            SecurityStatus.SIGNATURE_VERIFIED -> stringResource(R.string.details_security_signature)
            SecurityStatus.UNKNOWN -> stringResource(R.string.details_security_unknown)
        }
        Spacer(Modifier.height(Spacing.small))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = securityNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.medium),
            )
        }

        // Checksum
        val checksum = download?.sha256
        if (checksum != null) {
            Spacer(Modifier.height(Spacing.small))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = checksum,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onCopyChecksum(checksum) }) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.details_copy_checksum),
                        )
                    }
                }
            }
        }

        // Description
        if (!app.description.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.regular))
            SectionHeader(title = stringResource(R.string.details_about))
            Text(
                text = app.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
        }

        // Mod info
        if (!app.modInfo.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.regular))
            SectionHeader(title = stringResource(R.string.details_mod_info))
            Text(
                text = app.modInfo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
        }

        // Variants
        if (app.variants.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.regular))
            SectionHeader(title = stringResource(R.string.details_variants))
            Column(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                app.variants.forEach { variant ->
                    VariantRow(
                        variant = variant,
                        onDownload = { url ->
                            if (!url.isNullOrBlank()) onDownload(url)
                        },
                        onOpenSource = onOpenSource,
                    )
                }
            }
        }

        // Permissions
        if (app.permissions.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.regular))
            SectionHeader(title = stringResource(R.string.details_permissions))
            Column(modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                app.permissions.forEach { permission ->
                    Text(
                        text = permission,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xlarge))
    }
}

@Composable
private fun DownloadStatusBlock(
    download: com.aprax.modstore.domain.repository.DownloadState,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.medium)) {
            Text(
                text = when (download.status) {
                    DownloadStatus.QUEUED -> stringResource(R.string.download_queued)
                    DownloadStatus.PAUSED -> stringResource(R.string.download_paused)
                    DownloadStatus.RUNNING -> stringResource(R.string.download_running)
                    DownloadStatus.READY_TO_INSTALL, DownloadStatus.COMPLETED -> stringResource(R.string.download_ready)
                    DownloadStatus.FAILED -> stringResource(R.string.download_failed)
                    DownloadStatus.CANCELLED -> stringResource(R.string.download_cancelled)
                    DownloadStatus.INSTALLED -> stringResource(R.string.download_installed)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (download.status == DownloadStatus.RUNNING && download.progress != null) {
                Spacer(Modifier.height(Spacing.small))
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = ByteFormat.format(download.bytesDownloaded)
                        ?: stringResource(R.string.download_percent, (download.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VariantRow(
    variant: AppVariant,
    onDownload: (String?) -> Unit,
    onOpenSource: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = variant.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
                variant.versionName?.let { MetaPill(text = it) }
                variant.fileSize?.let { MetaPill(text = it) }
            }
        }
        if (variant.downloadUrl != null) {
            TextButton(onClick = { onDownload(variant.downloadUrl) }) {
                Text(stringResource(R.string.details_download))
            }
        } else {
            TextButton(onClick = onOpenSource) {
                Text(stringResource(R.string.details_visit_source))
            }
        }
    }
}
