package com.aprax.modstore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.aprax.modstore.core.ui.EmptyState
import com.aprax.modstore.core.ui.GridSkeleton
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.feature.library.R

@Composable
fun LibraryScreen(
    onOpenListing: (AppListing) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.tab.ordinal) {
            Tab(
                selected = state.tab == LibraryTab.FAVOURITES,
                onClick = { viewModel.selectTab(LibraryTab.FAVOURITES) },
                text = { Text(stringResource(R.string.library_favourites)) },
            )
            Tab(
                selected = state.tab == LibraryTab.SAVED,
                onClick = { viewModel.selectTab(LibraryTab.SAVED) },
                text = { Text(stringResource(R.string.library_saved)) },
            )
        }

        when (state.tab) {
            LibraryTab.FAVOURITES -> LibraryList(
                state = state.favourites,
                emptyTitle = stringResource(R.string.library_empty),
                emptyMessage = stringResource(R.string.library_empty_message),
                onOpenListing = onOpenListing,
                onRemove = viewModel::remove,
            )

            LibraryTab.SAVED -> LibraryList(
                state = state.saved,
                emptyTitle = stringResource(R.string.library_empty),
                emptyMessage = stringResource(R.string.library_empty_message),
                onOpenListing = onOpenListing,
                onRemove = viewModel::remove,
            )
        }
    }
}

@Composable
private fun LibraryList(
    state: UiState<List<AppListing>>,
    emptyTitle: String,
    emptyMessage: String,
    onOpenListing: (AppListing) -> Unit,
    onRemove: (AppListing) -> Unit,
) {
    when (state) {
        is UiState.Loading -> GridSkeleton(modifier = Modifier.fillMaxSize())

        is UiState.Ready -> {
            if (state.data.isEmpty()) {
                EmptyState(
                    title = emptyTitle,
                    message = emptyMessage,
                    icon = Icons.Filled.Bookmark,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.data, key = { it.id }) { listing ->
                        AppRow(
                            listing = listing,
                            onClick = { onOpenListing(listing) },
                            isFavourite = true,
                            trailing = {
                                IconButton(onClick = { onRemove(listing) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.library_remove_action),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        is UiState.Failed -> EmptyState(
            title = stringResource(R.string.library_empty),
            message = emptyMessage,
            icon = Icons.Filled.Bookmark,
            modifier = Modifier.fillMaxSize(),
        )

        is UiState.Stale -> Unit
    }
}
