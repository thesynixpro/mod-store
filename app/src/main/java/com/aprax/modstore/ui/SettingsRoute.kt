package com.aprax.modstore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aprax.modstore.R
import com.aprax.modstore.feature.settings.SettingsScreen

/**
 * Settings wrapped in the shell's own top bar.
 *
 * The settings screen lives in a feature module and deliberately has no app bar of its
 * own; navigation chrome is the shell's job, so this route composes a back arrow above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenSourceUrl: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        )

        SettingsScreen(
            onOpenPrivacy = onOpenPrivacy,
            onOpenTerms = onOpenTerms,
            onOpenSourceUrl = onOpenSourceUrl,
        )
    }
}
