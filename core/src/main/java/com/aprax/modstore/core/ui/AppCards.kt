package com.aprax.modstore.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.usecase.ByteFormat
import com.aprax.modstore.domain.usecase.RelativeDate

/**
 * Square app artwork with a graceful fallback.
 *
 * Source sites sometimes 404 an icon; rather than show a broken image the card falls back
 * to a neutral glyph tile, so a missing asset never looks like a crash.
 */
@Composable
fun AppIcon(
    iconUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 16,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
    }
}

/**
 * The compact card used in the home carousels and search grid.
 *
 * Deliberately shows only facts the listing actually has: a missing size or date renders
 * as nothing rather than as a placeholder value.
 */
@Composable
fun AppCard(
    listing: AppListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavourite: Boolean = false,
    onToggleFavourite: (() -> Unit)? = null,
    showSource: Boolean = true,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(Spacing.medium)) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(
                    iconUrl = listing.iconUrl,
                    contentDescription = listing.appName,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.weight(1f))
                if (onToggleFavourite != null) {
                    FavouriteToggle(
                        isFavourite = isFavourite,
                        onToggle = onToggleFavourite,
                        compact = true,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.medium))

            Text(
                text = listing.appName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val subtitle = listing.description?.takeIf { it.isNotBlank() }
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(Spacing.small))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
                listing.versionName?.let { MetaPill(text = "v$it") }
                listing.fileSize?.let { MetaPill(text = it) }
            }

            if (showSource) {
                Spacer(Modifier.height(Spacing.small))
                Text(
                    text = listing.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The full-width row used in vertical lists (search results, categories, library).
 *
 * Includes the update badge when the caller has determined the source version is newer
 * than what is installed, and the source name so attribution is never ambiguous.
 */
@Composable
fun AppRow(
    listing: AppListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavourite: Boolean = false,
    onToggleFavourite: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    updateAvailable: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.regular, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            iconUrl = listing.iconUrl,
            contentDescription = listing.appName,
            modifier = Modifier.size(56.dp),
        )

        Spacer(Modifier.width(Spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listing.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (updateAvailable) {
                    Spacer(Modifier.width(Spacing.small))
                    MetaPill(
                        text = "Update",
                        tint = MaterialTheme.colorScheme.secondary,
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        leadingIcon = Icons.Filled.SystemUpdate,
                    )
                }
            }

            val subtitle = listing.description?.takeIf { it.isNotBlank() }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(Spacing.small))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tiny),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listing.versionName?.let { MetaPill(text = "v$it") }
                listing.fileSize?.let { MetaPill(text = it) }
                RelativeDate.describe(listing.lastUpdated)?.let {
                    MetaPill(text = it, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Source: ${listing.sourceName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (trailing != null) {
            Spacer(Modifier.width(Spacing.small))
            trailing()
        } else if (onToggleFavourite != null) {
            FavouriteToggle(isFavourite = isFavourite, onToggle = onToggleFavourite)
        }
    }
}

/** Heart toggle that scales slightly on change so the tap registers visually. */
@Composable
fun FavouriteToggle(
    isFavourite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scale by animateFloatAsState(if (isFavourite) 1.08f else 1f, label = "favScale")
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(if (compact) 32.dp else 40.dp),
    ) {
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
            tint = if (isFavourite) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.scale(scale).size(if (compact) 18.dp else 22.dp),
        )
    }
}

/** Bookmark toggle for "save for later". */
@Composable
fun SaveToggle(
    isSaved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            contentDescription = if (isSaved) "Remove from saved" else "Save for later",
            tint = if (isSaved) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Wide hero card used at the top of the home feed.
 *
 * Draws the source artwork full-bleed with a scrim gradient so the title stays legible
 * over an unpredictable image.
 */
@Composable
fun FeaturedCard(
    listing: AppListing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            if (!listing.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = listing.iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Scrim so text is readable over any artwork.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Transparent,
                            0.55f to androidx.compose.ui.graphics.Color(0x66000000),
                            1f to androidx.compose.ui.graphics.Color(0xE6000000),
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.regular),
            ) {
                Text(
                    text = listing.appName,
                    style = MaterialTheme.typography.titleLarge,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOfNotNull(
                        listing.versionName?.let { "v$it" },
                        listing.fileSize,
                        listing.sourceName,
                    ).joinToString("  •  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color(0xE6FFFFFF),
                    maxLines = 1,
                )
            }
        }
    }
}
