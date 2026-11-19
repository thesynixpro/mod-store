package com.aprax.modstore.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.aprax.modstore.core.ui.AppRow
import com.aprax.modstore.core.ui.AppRowSkeleton
import com.aprax.modstore.core.ui.EmptyState
import com.aprax.modstore.core.ui.ErrorState
import com.aprax.modstore.core.ui.MetaPill
import com.aprax.modstore.core.ui.SectionHeader
import com.aprax.modstore.core.ui.SourceWarningBanner
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.core.ui.dataOrNull
import com.aprax.modstore.core.ui.errorOrNull
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.feature.search.R

@Composable
fun SearchScreen(
    onOpenListing: (AppListing) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::submit,
        )

        when {
            !state.active && state.recentSearches.isNotEmpty() -> RecentSearches(
                terms = state.recentSearches,
                onTermClick = viewModel::onQueryChange,
                onClear = viewModel::clearHistory,
            )

            !state.active && state.recentSearches.isEmpty() -> EmptyState(
                title = stringResource(R.string.search_empty_title),
                message = stringResource(R.string.search_empty_message),
                modifier = Modifier.fillMaxSize(),
            )

            state.catalog.shouldShowFullError -> {
                val error = state.catalog.state.errorOrNull
                if (error != null) {
                    ErrorState(
                        error = error,
                        modifier = Modifier.fillMaxSize(),
                        onRetry = viewModel::retry,
                    )
                }
            }

            state.catalog.state is UiState.Loading -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(8) {
                    AppRowSkeleton()
                }
            }

            state.catalog.state is UiState.Ready -> {
                val catalog = state.catalog
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    catalog.report.warningMessage { it.name }?.let { warning ->
                        item(key = "warning") {
                            SourceWarningBanner(
                                message = warning,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.screenHorizontal,
                                    vertical = Spacing.small,
                                ),
                                onRetry = viewModel::retry,
                            )
                        }
                    }
                    if (catalog.listings.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                title = stringResource(R.string.search_no_results),
                                message = stringResource(R.string.search_no_results_message),
                            )
                        }
                    } else {
                        items(catalog.listings, key = { it.id }) { listing ->
                            AppRow(
                                listing = listing,
                                onClick = { onOpenListing(listing) },
                            )
                        }
                        if (catalog.paging.hasMore) {
                            item(key = "loading-more") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.large),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear_history))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.width(Spacing.small))
        TextButton(onClick = onSearch) {
            Text(stringResource(R.string.search_action))
        }
    }
}

@Composable
private fun RecentSearches(
    terms: List<String>,
    onTermClick: (String) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            SectionHeader(
                title = stringResource(R.string.search_history_header),
                action = {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.search_clear_history))
                    }
                },
            )
        }
        items(terms, key = { it }) { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTermClick(term) }
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.medium))
                Text(
                    text = term,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
