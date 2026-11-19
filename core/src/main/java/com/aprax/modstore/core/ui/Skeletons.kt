package com.aprax.modstore.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aprax.modstore.core.design.Spacing

/**
 * Shimmer used by every skeleton.
 *
 * A single infinite transition per skeleton group would be ideal, but Compose hoists these
 * cheaply and the alpha sweep is what makes the loading state feel intentional rather than
 * broken.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val progress by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    return Brush.linearGradient(
        listOf(
            base.copy(alpha = progress * 0.55f),
            base.copy(alpha = progress * 0.85f),
            base.copy(alpha = progress * 0.55f),
        ),
    )
}

/** One shimmering block. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush()),
    )
}

/** Placeholder that matches the real horizontal app card's dimensions. */
@Composable
fun AppCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(160.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.medium),
        verticalAlignment = Alignment.Top,
    ) {
        SkeletonBlock(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.width(Spacing.medium))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().height(13.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(11.dp))
            Spacer(Modifier.height(4.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(10.dp))
        }
    }
}

/** Placeholder that matches the real full-width list row. */
@Composable
fun AppRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.regular, vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.width(Spacing.medium))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.62f).height(14.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(11.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(10.dp))
        }
    }
}

/** A whole home-feed placeholder: two rows of cards plus a list of rows. */
@Composable
fun HomeFeedSkeleton(
    modifier: Modifier = Modifier,
    rowTitles: Int = 2,
    rowsPerTitle: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xlarge),
    ) {
        repeat(rowTitles) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(Modifier.width(140.dp).height(18.dp))
                }
                Row(
                    modifier = Modifier.padding(start = Spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    repeat(2) { AppCardSkeleton() }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SkeletonBlock(
                Modifier
                    .padding(horizontal = Spacing.screenHorizontal)
                    .width(160.dp)
                    .height(18.dp),
            )
            repeat(rowsPerTitle) { AppRowSkeleton() }
        }
    }
}

/** A details-screen placeholder: hero block, action buttons, description lines. */
@Composable
fun DetailsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.regular),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(Modifier.size(Spacing.iconHero), RoundedCornerShape(26.dp))
            Spacer(Modifier.width(Spacing.regular))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(20.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(13.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(12.dp))
            }
        }
        Spacer(Modifier.height(Spacing.small))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            SkeletonBlock(Modifier.weight(1f).height(50.dp), RoundedCornerShape(16.dp))
            SkeletonBlock(Modifier.weight(1f).height(50.dp), RoundedCornerShape(16.dp))
        }
        Spacer(Modifier.height(Spacing.small))
        repeat(6) { index ->
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(if (index == 5) 0.6f else 1f)
                    .height(12.dp),
            )
        }
    }
}

/** Generic grid of shimmer blocks, for category and library screens. */
@Composable
fun GridSkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    items: Int = 6,
    itemHeight: Dp = 96.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        var remaining = items
        while (remaining > 0) {
            val inRow = minOf(columns, remaining)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                repeat(inRow) {
                    SkeletonBlock(
                        Modifier.weight(1f).height(itemHeight),
                        MaterialTheme.shapes.medium,
                    )
                }
                // Keep the last row aligned with the rows above it.
                repeat(columns - inRow) {
                    Spacer(Modifier.weight(1f))
                }
            }
            remaining -= inRow
        }
    }
}
