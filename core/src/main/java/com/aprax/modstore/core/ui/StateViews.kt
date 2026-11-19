package com.aprax.modstore.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.domain.model.AppError

/**
 * The empty/error surface used everywhere.
 *
 * Every list screen shares these states rather than each inventing its own, which is what
 * keeps loading, empty and failure screens looking like one product.
 */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    primaryAction: StateAction? = null,
    secondaryAction: StateAction? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xlarge, vertical = Spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(22.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(Spacing.regular))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.small))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (primaryAction != null || secondaryAction != null) {
            Spacer(Modifier.height(Spacing.large))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondaryAction?.let { action ->
                    TextButton(onClick = action.onClick) { Text(action.label) }
                }
                primaryAction?.let { action ->
                    androidx.compose.material3.Button(onClick = action.onClick) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

/** A labelled action on a [StateMessage]. */
data class StateAction(val label: String, val onClick: () -> Unit)

/**
 * Maps a typed [AppError] onto the right icon and wording.
 *
 * Centralising this means a source being down, a rate limit and a genuine parsing failure
 * all read differently to the user, and the "Try again" affordance appears only when
 * retrying could actually help.
 */
@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
) {
    val icon = when (error) {
        is AppError.Offline -> Icons.Filled.WifiOff
        is AppError.SourceUnavailable -> Icons.Filled.CloudOff
        is AppError.NoProgrammaticAccess -> Icons.Filled.LinkOff
        is AppError.NotFound -> Icons.Filled.SearchOff
        else -> Icons.Filled.ErrorOutline
    }

    val title = when (error) {
        is AppError.Offline -> "No connection"
        is AppError.SourceUnavailable -> "${error.sourceName} is unavailable"
        is AppError.RateLimited -> "Slow down for a moment"
        is AppError.NoProgrammaticAccess -> "No API on ${error.sourceName}"
        is AppError.NotFound -> "No longer listed"
        is AppError.Malformed, is AppError.SourceChanged -> "Unreadable response"
        is AppError.Storage -> "Local storage problem"
        is AppError.Timeout -> "${error.sourceName} timed out"
        is AppError.Unexpected -> "Something went wrong"
    }

    StateMessage(
        icon = icon,
        title = title,
        message = error.message,
        modifier = modifier,
        accent = if (error is AppError.Offline) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.error,
        primaryAction = if (error.isRetryable && onRetry != null) {
            StateAction("Try again", onRetry)
        } else {
            null
        },
        secondaryAction = onOpenSource?.let { StateAction("Open source site", it) },
    )
}

/** Empty state with an optional call to action. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.SearchOff,
    action: StateAction? = null,
) {
    StateMessage(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier,
        primaryAction = action,
    )
}

/**
 * Non-blocking banner shown when some — but not all — sources failed.
 *
 * The screen still renders the listings that did arrive, and this strip tells the user
 * exactly which site is missing rather than silently showing less content.
 */
@Composable
fun SourceWarningBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.small))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

/** A small pill used for version, size and source labels on cards. */
@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    leadingIcon: ImageVector? = null,
) {
    val animatedTint by animateColorAsState(tint, label = "metaPillTint")
    Row(
        modifier = modifier
            .background(background.copy(alpha = 0.7f), RoundedCornerShape(50))
            .border(1.dp, animatedTint.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = animatedTint,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = animatedTint,
            maxLines = 1,
        )
    }
}

/** Divider that respects the theme's outline colour. */
@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Marks a purely decorative element so TalkBack skips it. */
fun Modifier.decorative(): Modifier = clearAndSetSemantics { }
