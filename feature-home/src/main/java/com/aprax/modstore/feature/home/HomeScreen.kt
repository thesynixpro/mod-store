package com.aprax.modstore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.core.ui.AppCard
import com.aprax.modstore.core.ui.AppRow
import com.aprax.modstore.core.ui.EmptyState
import com.aprax.modstore.core.ui.ErrorState
import com.aprax.modstore.core.ui.HomeFeedSkeleton
import com.aprax.modstore.core.ui.SectionHeader
import com.aprax.modstore.core.ui.SourceLoadReport
import com.aprax.modstore.core.ui.SourceWarningBanner
import com.aprax.modstore.core.ui.StateAction
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.CategoryTaxonomy
import com.aprax.modstore.feature.home.R

@Composable
fun HomeScreen(
    onOpenListing: (AppListing) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
        )

        when (val uiState = state.state) {
            is UiState.Loading -> HomeFeedSkeleton(
                modifier = Modifier.fillMaxSize(),
            )

            is UiState.Failed -> ErrorState(
                error = uiState.error,
                modifier = Modifier.fillMaxSize(),
                onRetry = viewModel::load,
                onOpenSource = null,
            )

            is UiState.Ready -> {
                val feed = uiState.data
                val report = SourceLoadReport(
                    succeeded = feed.succeededSources,
                    failed = feed.failedSources,
                )
                if (feed.isEmpty) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.CloudOff,
                        action = StateAction(
                            label = stringResource(R.string.refresh),
                            onClick = viewModel::load,
                        ),
                    )
                } else {
                    HomeContent(
                        feed = feed,
                        report = report,
                        onOpenListing = onOpenListing,
                        onOpenCategory = onOpenCategory,
                        onRetry = viewModel::load,
                    )
                }
            }

            is UiState.Stale -> Unit
        }
    }
}

@Composable
private fun TopBar(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.settings_title))
        }
    }
}

@Composable
private fun HomeContent(
    feed: GetHomeFeedUseCase.HomeFeed,
    report: SourceLoadReport,
    onOpenListing: (AppListing) -> Unit,
    onOpenCategory: (String) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.large),
    ) {
        report.warningMessage { sourceName ->
            sourceName
        }?.let { warning ->
            item(key = "warning") {
                SourceWarningBanner(
                    message = warning,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenHorizontal,
                        vertical = Spacing.small,
                    ),
                    onRetry = onRetry,
                )
            }
        }

        if (feed.newest.isNotEmpty()) {
            item(key = "newest-header") {
                SectionHeader(
                    title = stringResource(R.string.home_newest_section),
                    subtitle = null,
                )
            }
            items(feed.newest, key = { it.id }) { listing ->
                AppRow(
                    listing = listing,
                    onClick = { onOpenListing(listing) },
                )
            }
        }

        for ((categoryKey, listings) in feed.byCategory) {
            if (listings.isEmpty()) continue
            val category = CategoryTaxonomy.byKey(categoryKey)
            item(key = "header-$categoryKey") {
                SectionHeader(
                    title = category?.title ?: categoryKey,
                    modifier = Modifier.padding(top = Spacing.large),
                    action = {
                        if (category != null) {
                            TextButton(onClick = { onOpenCategory(categoryKey) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.home_see_all))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .height(16.dp)
                                            .width(16.dp),
                                    )
                                }
                            }
                        }
                    },
                )
            }
            item(key = "row-$categoryKey") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                    items(listings, key = { it.id }) { listing ->
                        AppCard(
                            listing = listing,
                            onClick = { onOpenListing(listing) },
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }
            }
        }
    }
}
