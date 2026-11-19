package com.aprax.modstore.feature.categories

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.core.ui.GridSkeleton
import com.aprax.modstore.core.ui.SectionHeader
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.Category
import com.aprax.modstore.feature.categories.R

@Composable
fun CategoriesScreen(
    onOpenCategory: (String) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val uiState = state.state) {
        is UiState.Loading -> GridSkeleton(modifier = Modifier.fillMaxSize())

        is UiState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "games-header") {
                SectionHeader(
                    title = stringResource(R.string.categories_games),
                    modifier = Modifier.padding(top = Spacing.small),
                )
            }
            items(state.games, key = { it.key }) { category ->
                CategoryRow(category = category, onClick = { onOpenCategory(category.key) })
            }
            item(key = "apps-header") {
                SectionHeader(
                    title = stringResource(R.string.categories_apps),
                    modifier = Modifier.padding(top = Spacing.large),
                )
            }
            items(state.apps, key = { it.key }) { category ->
                CategoryRow(category = category, onClick = { onOpenCategory(category.key) })
            }
        }

        is UiState.Failed -> EmptyCategories(
            onRetry = viewModel::load,
        )

        is UiState.Stale -> Unit
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
) {
    val icon: ImageVector = if (category.isGame) Icons.Filled.SportsEsports else Icons.Filled.Apps
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(Spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val description = category.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCategories(
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.categories_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Spacing.small))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}
