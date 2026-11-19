package com.aprax.modstore.feature.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprax.modstore.core.design.Spacing
import com.aprax.modstore.core.ui.SectionHeader
import com.aprax.modstore.core.ui.UiState
import com.aprax.modstore.domain.model.SourceAvailability
import com.aprax.modstore.domain.model.SourceStatus
import com.aprax.modstore.domain.model.ThemeMode
import com.aprax.modstore.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSourceUrl: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings

    var showAbout by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "appearance") {
            SectionHeader(
                title = stringResource(R.string.settings_appearance),
                modifier = Modifier.padding(top = Spacing.medium),
            )
        }

        item(key = "theme") {
            ThemeSetting(
                current = settings.themeMode,
                onSelect = viewModel::setThemeMode,
            )
        }

        item(key = "dynamic-colour") {
            SettingSwitchRow(
                title = stringResource(R.string.settings_dynamic_colour),
                subtitle = stringResource(R.string.settings_dynamic_colour_summary),
                checked = settings.dynamicColour,
                onCheckedChange = viewModel::setDynamicColour,
            )
        }

        item(key = "downloads-header") {
            SectionHeader(
                title = stringResource(R.string.settings_downloads),
                modifier = Modifier.padding(top = Spacing.large),
            )
        }

        item(key = "wifi-only") {
            SettingSwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_summary),
                checked = settings.wifiOnlyDownloads,
                onCheckedChange = viewModel::setWifiOnlyDownloads,
            )
        }

        item(key = "notifications") {
            SettingSwitchRow(
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_summary),
                checked = settings.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled,
            )
        }

        item(key = "auto-delete") {
            SettingSwitchRow(
                title = stringResource(R.string.settings_auto_delete),
                subtitle = stringResource(R.string.settings_auto_delete_summary),
                checked = settings.autoDeleteInstalledApks,
                onCheckedChange = viewModel::setAutoDeleteInstalledApks,
            )
        }

        item(key = "sources-header") {
            SectionHeader(
                title = stringResource(R.string.settings_sources),
                subtitle = stringResource(R.string.settings_sources_summary),
                modifier = Modifier.padding(top = Spacing.large),
                action = {
                    IconButton(onClick = viewModel::refreshSources) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                },
            )
        }

        when (val sourceState = state.sources) {
            is UiState.Ready -> items(
                sourceState.data,
                key = { it.info.id.name },
            ) { status ->
                SourceStatusRow(
                    status = status,
                    onToggle = { enabled ->
                        viewModel.toggleSource(status.info.id, enabled)
                    },
                    onOpenSource = { onOpenSourceUrl(status.info.baseUrl) },
                )
            }

            is UiState.Loading -> item(key = "sources-loading") {
                Text(
                    text = "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.medium),
                )
            }

            is UiState.Failed -> Unit

            is UiState.Stale -> Unit
        }

        item(key = "about-header") {
            SectionHeader(
                title = stringResource(R.string.settings_about),
                modifier = Modifier.padding(top = Spacing.large),
            )
        }

        item(key = "about") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_about_app),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.small))
                    Text(
                        text = stringResource(R.string.settings_source_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.small))
                    Row {
                        TextButton(onClick = onOpenPrivacy) {
                            Text(stringResource(R.string.settings_privacy))
                        }
                        TextButton(onClick = onOpenTerms) {
                            Text(stringResource(R.string.settings_terms))
                        }
                    }
                }
            }
        }

        item(key = "takedown") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showAbout = true }) {
                    Text(stringResource(R.string.settings_takedown))
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.settings_takedown)) },
            text = { Text(stringResource(R.string.settings_takedown_message)) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSetting(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_theme)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SourceStatusRow(
    status: SourceStatus,
    onToggle: (Boolean) -> Unit,
    onOpenSource: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.info.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = status.info.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = status.enabledByUser,
                onCheckedChange = onToggle,
            )
        }

        val stateNote = when (val availability = status.availability) {
            is SourceAvailability.Available ->
                stringResource(R.string.settings_source_available)

            is SourceAvailability.NoCatalogue ->
                stringResource(R.string.settings_source_no_catalogue)

            is SourceAvailability.LinkOnly ->
                stringResource(R.string.settings_source_link_only)

            is SourceAvailability.TemporarilyUnavailable ->
                stringResource(R.string.settings_source_unavailable)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (status.availability) {
                    is SourceAvailability.Available -> Icons.Filled.CheckCircle
                    is SourceAvailability.TemporarilyUnavailable -> Icons.Filled.Warning
                    else -> Icons.Filled.ErrorOutline
                },
                contentDescription = null,
                tint = when (status.availability) {
                    is SourceAvailability.Available -> MaterialTheme.colorScheme.secondary
                    is SourceAvailability.TemporarilyUnavailable -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.small))
            Text(
                text = stateNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSource) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(status.info.baseUrl.removePrefix("https://"))
            }
        }
    }
}
