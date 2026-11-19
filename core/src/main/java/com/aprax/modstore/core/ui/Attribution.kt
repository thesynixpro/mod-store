package com.aprax.modstore.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.domain.model.SecurityStatus
import com.aprax.modstore.domain.model.SourceAvailability
import com.aprax.modstore.domain.model.SourceStatus

/**
 * The security-status chip shown on the details screen.
 *
 * This is the app's honesty contract made visible. There is no "safe" or "verified clean"
 * state, because a third-party APK cannot be certified that way here. The strongest claim
 * the UI makes is that a checksum exists or that the file's own signing certificate was
 * read locally, and each chip says exactly that and no more.
 */
@Composable
fun SecurityStatusChip(
    status: SecurityStatus,
    modifier: Modifier = Modifier,
) {
    val (label, detail, tint) = when (status) {
        SecurityStatus.UNKNOWN -> Triple(
            "Not assessed",
            "This app has not been scanned. Only install software you trust.",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecurityStatus.CHECKSUM_AVAILABLE -> Triple(
            "Checksum available",
            "The source published a checksum, so this download can be integrity-checked.",
            MaterialTheme.colorScheme.secondary,
        )
        SecurityStatus.SIGNATURE_VERIFIED -> Triple(
            "Signature read",
            "The file's APK signing certificate was read locally. A valid signature " +
                "does not mean the app is safe or malware-free.",
            MaterialTheme.colorScheme.primary,
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.small))
            MetaPill(
                text = label,
                tint = tint,
                background = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.small))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The standing legal notice shown in details and settings.
 *
 * Required by the project's compliance rules: the app aggregates third-party listings, so
 * it must be explicit that it does not host, verify or endorse the APKs it links to.
 */
@Composable
fun LegalNotice(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(Spacing.small))
            Text(
                text = "Aprax Mods Store is an independent index. It does not host, mirror " +
                    "or modify any APK. Listings and downloads belong to the source sites " +
                    "named on each card, and opening one takes you to that site. No APK is " +
                    "certified as safe or malware-free.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Per-source attribution row used on details and in the sources list.
 *
 * Makes clear what the app can and cannot do with a given site, which is the honest
 * counterpart to the "aggregates from many sources" claim on the home screen.
 */
@Composable
fun SourceAttributionRow(
    status: SourceStatus,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status.info.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            val availability = status.availability
            val (label, tint) = when {
                !status.enabledByUser -> "Disabled" to MaterialTheme.colorScheme.onSurfaceVariant
                availability is SourceAvailability.Available ->
                    "Live" to MaterialTheme.colorScheme.primary
                availability is SourceAvailability.TemporarilyUnavailable ->
                    "Unreachable" to MaterialTheme.colorScheme.error
                availability is SourceAvailability.LinkOnly ->
                    "Link only" to MaterialTheme.colorScheme.secondary
                availability is SourceAvailability.NoCatalogue ->
                    "No catalogue" to MaterialTheme.colorScheme.onSurfaceVariant
                else -> "Unknown" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            MetaPill(text = label, tint = tint)
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = when (val availability = status.availability) {
                is SourceAvailability.NoCatalogue -> availability.reason
                is SourceAvailability.LinkOnly -> availability.reason
                is SourceAvailability.TemporarilyUnavailable -> availability.error.message
                else -> status.info.usageNote
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full-width section header used by every list screen. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        action?.invoke()
    }
}

/** Small labelled row for the details facts table. */
@Composable
fun DetailFactRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    // Facts the source did not publish are omitted rather than filled with a guess.
    if (value.isNullOrBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.small))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** Banner explaining that a source offers no programmatic access. */
@Composable
fun LinkOnlyNotice(
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    SourceWarningBanner(
        message = "$sourceName has no public API. Listings open on the source website.",
        modifier = modifier,
    )
}

/** Cloud-off glyph reused by several empty states. */
val CloudOffIcon: ImageVector get() = Icons.Filled.CloudOff
