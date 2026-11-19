package com.aprax.modstore.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aprax.modstore.BuildConfig
import com.aprax.modstore.R
import com.aprax.modstore.core.util.ExternalLinks
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.CategoryTaxonomy
import com.aprax.modstore.domain.model.SourceInfo
import com.aprax.modstore.domain.repository.DownloadedApk
import com.aprax.modstore.feature.categories.CategoriesScreen
import com.aprax.modstore.feature.details.DetailsScreen
import com.aprax.modstore.feature.downloads.DownloadsScreen
import com.aprax.modstore.feature.home.HomeScreen
import com.aprax.modstore.feature.library.LibraryScreen
import com.aprax.modstore.feature.search.SearchScreen

/** The five tab destinations. */
private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    SEARCH("search", R.string.nav_search, Icons.Filled.Search),
    CATEGORIES("categories", R.string.nav_categories, Icons.Filled.Category),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Filled.Download),
    LIBRARY("library", R.string.nav_library, Icons.Filled.Bookmark),
}

private const val ROUTE_DETAILS = "details/{listingId}"
private const val ARG_LISTING_ID = "listingId"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CATEGORY = "category/{categoryKey}"
private const val ARG_CATEGORY_KEY = "categoryKey"

/**
 * The single navigation graph.
 *
 * Five tab destinations live under a [Scaffold] bottom bar. Details, settings and the
 * category browser are full-screen destinations with the bar hidden, so they feel like a
 * drilled-in page rather than another tab.
 */
@Composable
fun AppNavHost(
    appViewModel: AppViewModel,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // A source-page link arrives through the view model; open its details route once.
    val deepLink by appViewModel.deepLink.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink) {
        val listing = deepLink ?: return@LaunchedEffect
        navController.navigate("details/${listing.id}")
        appViewModel.consumeDeepLink(listing)
    }

    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenListing = { listing ->
                        appViewModel.rememberListing(listing)
                        navController.navigate(
                            "details/${listing.id}",
                        )
                    },
                    onOpenCategory = { key -> navController.navigate("category/$key") },
                    onOpenSearch = {
                        navController.navigate(TopLevelDestination.SEARCH.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(ROUTE_SETTINGS)
                    },
                )
            }

            composable(TopLevelDestination.SEARCH.route) {
                SearchScreen(
                    onOpenListing = { listing ->
                        appViewModel.rememberListing(listing)
                        navController.navigate("details/${listing.id}")
                    },
                )
            }

            composable(TopLevelDestination.CATEGORIES.route) {
                CategoriesScreen(
                    onOpenCategory = { key -> navController.navigate("category/$key") },
                )
            }

            composable(TopLevelDestination.DOWNLOADS.route) {
                DownloadsScreen(
                    onOpenListing = { apk -> openDownloadedApk(navController, appViewModel, apk) },
                )
            }

            composable(TopLevelDestination.LIBRARY.route) {
                LibraryScreen(
                    onOpenListing = { listing ->
                        appViewModel.rememberListing(listing)
                        navController.navigate("details/${listing.id}")
                    },
                )
            }

            composable(
                route = ROUTE_DETAILS,
                arguments = listOf(navArgument(ARG_LISTING_ID) { type = NavType.StringType }),
            ) { entry ->
                val listingId = entry.arguments?.getString(ARG_LISTING_ID) ?: return@composable
                val listing = appViewModel.listingById(listingId)
                if (listing != null) {
                    DetailsScreen(
                        listing = listing,
                        onBack = { navController.popBackStack() },
                        wifiOnly = appViewModel.wifiOnly,
                    )
                } else {
                    // Registry miss: process death restored the back stack, or the user opened
                    // a modyolo.com link directly. A minimal listing still resolves the source
                    // page; the details screen fetches full details and degrades gracefully.
                    fallbackListingFromId(listingId)?.let { fallback ->
                        DetailsScreen(
                            listing = fallback,
                            onBack = { navController.popBackStack() },
                            wifiOnly = appViewModel.wifiOnly,
                        )
                    }
                }
            }

            composable(ROUTE_SETTINGS) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenPrivacy = {
                        ExternalLinks.openSourceSite(context, BuildConfig.PRIVACY_URL)
                    },
                    onOpenTerms = {
                        ExternalLinks.openSourceSite(context, BuildConfig.TERMS_URL)
                    },
                    onOpenSourceUrl = { url ->
                        ExternalLinks.openSourceSite(context, url)
                    },
                )
            }

            composable(
                route = ROUTE_CATEGORY,
                arguments = listOf(navArgument(ARG_CATEGORY_KEY) { type = NavType.StringType }),
            ) { entry ->
                val key = entry.arguments?.getString(ARG_CATEGORY_KEY) ?: return@composable
                val category = CategoryTaxonomy.byKey(key)
                if (category != null) {
                    BrowseScreen(
                        category = category,
                        onBack = { navController.popBackStack() },
                        onOpenListing = { listing ->
                            appViewModel.rememberListing(listing)
                            navController.navigate("details/${listing.id}")
                        },
                    )
                }
            }
        }
    }
}

/** Rebuilds a details-openable listing from a download-history row and opens it. */
private fun openDownloadedApk(
    navController: NavHostController,
    appViewModel: AppViewModel,
    apk: DownloadedApk,
) {
    val listing = AppListing(
        id = apk.listingId,
        appName = apk.appName,
        sourceId = apk.sourceId,
        sourceName = SourceInfo.All.firstOrNull { it.id == apk.sourceId }?.displayName
            ?: apk.sourceId.name,
        sourceListingUrl = apk.sourceListingUrl,
        versionName = apk.versionName,
        packageName = apk.packageName,
        fileSizeBytes = apk.fileSizeBytes,
    )
    appViewModel.rememberListing(listing)
    navController.navigate("details/${apk.listingId}")
}

/**
 * A minimal listing reconstructed from a bare `source:slug` id, for the registry-miss case:
 * the process was restored from the back stack, or a modyolo.com link opened the app cold.
 *
 * Modyolo's canonical listing URL is always `https://modyolo.com/<slug>.html`, so rebuilding
 * it from the slug is exact, not a guess. No other facts are invented; the details screen
 * fills the rest from the source and degrades to this summary card if it cannot.
 */
private fun fallbackListingFromId(listingId: String): AppListing? {
    val colon = listingId.indexOf(':')
    if (colon <= 0 || colon == listingId.lastIndex) return null
    val sourceKey = listingId.substring(0, colon)
    val slug = listingId.substring(colon + 1)
    if (slug.isBlank()) return null

    val source = when (sourceKey.lowercase()) {
        "modyolo" -> SourceInfo.MODYOLO
        else -> return null
    }

    val appName = slug
        .substringBeforeLast(".html")
        .replace('-', ' ')
        .trim()

    return AppListing(
        id = listingId,
        appName = appName.ifBlank { slug },
        sourceId = source.id,
        sourceName = source.displayName,
        sourceListingUrl = "https://modyolo.com/$slug.html",
    )
}
