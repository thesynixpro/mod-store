package com.aprax.modstore.core.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aprax.modstore.domain.model.ThemeMode

/**
 * Brand palette.
 *
 * The product direction is a premium dark marketplace: a deep charcoal canvas, a violet
 * primary that reads well against it, and a cyan secondary used for progress and source
 * accents. Every one of these has been contrast-checked against the surface it sits on.
 */
object ApraxPalette {

    // Dark canvas, from deepest to lightest.
    val Charcoal = Color(0xFF0B0D12)
    val CharcoalElevated = Color(0xFF14171F)
    val CharcoalRaised = Color(0xFF1D212B)
    val CharcoalOutline = Color(0xFF2C313D)

    // Light canvas.
    val Porcelain = Color(0xFFF7F8FC)
    val PorcelainSurface = Color(0xFFFFFFFF)
    val PorcelainRaised = Color(0xFFEDEFF6)
    val PorcelainOutline = Color(0xFFD3D7E3)

    // Brand.
    val Violet = Color(0xFF8B7CFF)
    val VioletDeep = Color(0xFF5B4BD6)
    val Cyan = Color(0xFF35D0E8)
    val Rose = Color(0xFFFF7A9C)
    val Amber = Color(0xFFFFC062)
    val Mint = Color(0xFF4ADE80)

    // Text.
    val InkHigh = Color(0xFFE9EBF2)
    val InkMedium = Color(0xFFA7AEC2)
    val InkLow = Color(0xFF6E7691)
    val InkOnBrand = Color(0xFF0B0D12)

    /** Background wash behind hero areas. Subtle, two-stop, never distracting. */
    val heroGradient: List<Color> = listOf(
        Color(0xFF241E4E),
        Color(0xFF141830),
        Charcoal,
    )

    /** Accent gradient for primary call-to-action surfaces. */
    val actionGradient: List<Color> = listOf(Violet, VioletDeep)
}

private val DarkScheme = darkColorScheme(
    primary = ApraxPalette.Violet,
    onPrimary = ApraxPalette.InkOnBrand,
    primaryContainer = ApraxPalette.VioletDeep,
    onPrimaryContainer = ApraxPalette.InkHigh,
    secondary = ApraxPalette.Cyan,
    onSecondary = ApraxPalette.InkOnBrand,
    secondaryContainer = Color(0xFF12414D),
    onSecondaryContainer = Color(0xFFB8EEF7),
    tertiary = ApraxPalette.Rose,
    onTertiary = ApraxPalette.InkOnBrand,
    background = ApraxPalette.Charcoal,
    onBackground = ApraxPalette.InkHigh,
    surface = ApraxPalette.CharcoalElevated,
    onSurface = ApraxPalette.InkHigh,
    surfaceVariant = ApraxPalette.CharcoalRaised,
    onSurfaceVariant = ApraxPalette.InkMedium,
    surfaceContainer = ApraxPalette.CharcoalElevated,
    surfaceContainerHigh = ApraxPalette.CharcoalRaised,
    surfaceContainerHighest = Color(0xFF252A36),
    surfaceContainerLow = Color(0xFF11141B),
    surfaceContainerLowest = ApraxPalette.Charcoal,
    outline = ApraxPalette.CharcoalOutline,
    outlineVariant = Color(0xFF232833),
    error = Color(0xFFFF6B6B),
    onError = ApraxPalette.InkOnBrand,
    errorContainer = Color(0xFF4A1D1D),
    onErrorContainer = Color(0xFFFFD9D9),
    scrim = Color(0xCC000000),
)

private val LightScheme = lightColorScheme(
    primary = ApraxPalette.VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3DEFF),
    onPrimaryContainer = Color(0xFF1B1436),
    secondary = Color(0xFF11798C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9EDF5),
    onSecondaryContainer = Color(0xFF06313A),
    tertiary = Color(0xFFB93B62),
    onTertiary = Color.White,
    background = ApraxPalette.Porcelain,
    onBackground = Color(0xFF14161C),
    surface = ApraxPalette.PorcelainSurface,
    onSurface = Color(0xFF14161C),
    surfaceVariant = ApraxPalette.PorcelainRaised,
    onSurfaceVariant = Color(0xFF4A5065),
    surfaceContainer = ApraxPalette.PorcelainSurface,
    surfaceContainerHigh = ApraxPalette.PorcelainRaised,
    surfaceContainerHighest = Color(0xFFE3E6EF),
    surfaceContainerLow = Color(0xFFFBFBFE),
    surfaceContainerLowest = Color.White,
    outline = ApraxPalette.PorcelainOutline,
    outlineVariant = Color(0xFFE2E5EE),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** True when the app is currently rendering the dark palette. */
val isDarkTheme: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminanceIsDark()

private fun Color.luminanceIsDark(): Boolean {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return (r + g + b) < 0.5f
}

/**
 * Applies the Aprax theme.
 *
 * [themeMode] comes from the user's DataStore-backed setting. Dynamic colour is opt-in and
 * silently ignored below API 31 where it does not exist, so the branded palette is the
 * default experience.
 */
@Composable
fun ApraxModsStoreTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColour: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colourScheme = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colourScheme,
        typography = ApraxTypography,
        shapes = ApraxShapes,
        content = content,
    )
}
