package com.aprax.modstore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprax.modstore.R
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.core.ui.AppRow
import com.aprax.modstore.core.ui.AppRowSkeleton
import com.aprax.modstore.core.ui.EmptyState
import com.aprax.modstore.core.ui.ErrorState
import com.aprax.modstore.core.ui.SourceWarningBanner
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.Category

/**
 * Lists every listing the sources publish under one category.
 *
 * The brief's taxonomy maps each key to per-source slugs, so this screen queries each
 * enabled source with the slug it actually understands and merges the results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    category: Category,
    onBack: () -> Unit,
    onOpenListing: (AppListing) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(category.key) {
        viewModel.load(category)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(category.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )

        when (val uiState = state.catalog.state) {
            is UiState.Loading -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(8) { AppRowSkeleton() }
            }

            is UiState.Failed -> ErrorState(
                error = uiState.error,
                modifier = Modifier.fillMaxSize(),
                onRetry = viewModel::retry,
            )

            is UiState.Ready -> {
                val listings = uiState.data
                if (listings.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.browse_empty_title),
                        message = stringResource(R.string.browse_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        state.catalog.report.warningMessage { it.name }?.let { warning ->
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
                        items(listings, key = { it.id }) { listing ->
                            AppRow(
                                listing = listing,
                                onClick = { onOpenListing(listing) },
                            )
                        }
                    }
                }
            }

            is UiState.Stale -> Unit
        }
    }
}
