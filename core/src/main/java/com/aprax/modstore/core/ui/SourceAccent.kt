package com.aprax.modstore.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Source accent colours.
 *
 * Each source gets a stable hue so a card's provenance is recognisable at a glance, and
 * the accent is always the *only* thing colour-coding the source — the source name is
 * also printed, so colour is never the sole carrier of that information.
 */
@Composable
fun sourceAccent(sourceName: String): Color = when {
    sourceName.contains("Modyolo", ignoreCase = true) -> MaterialTheme.colorScheme.primary
    sourceName.contains("HappyMod", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
    sourceName.contains("A1N", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
