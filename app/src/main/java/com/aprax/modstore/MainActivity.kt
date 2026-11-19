package com.aprax.modstore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprax.modstore.core.design.ApraxModsStoreTheme
import com.aprax.modstore.core.util.ListingIdFactory
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SourceInfo
import com.aprax.modstore.ui.AppNavHost
import com.aprax.modstore.ui.AppViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host.
 *
 * The app has one activity and one nav graph; every feature screen is a destination.
 * The theme (user's SYSTEM/LIGHT/DARK choice and dynamic colour) is applied here, above
 * the graph, so every destination renders under the same theme.
 *
 * A source-page link (the manifest's VIEW intent filter) lands here; the URL is turned
 * into a minimal listing and queued for the nav host, which opens its details route.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle the intent that started this activity (e.g. a modyolo.com link).
        handleDeepLink(intent)?.let(appViewModel::queueDeepLink)

        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()

            ApraxModsStoreTheme(
                themeMode = settings.themeMode,
                dynamicColour = settings.dynamicColour,
            ) {
                AppNavHost(appViewModel = appViewModel)
            }
        }
    }

    /** The app is singleTop, so a second source link reuses this activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)?.let(appViewModel::queueDeepLink)
    }

    /**
     * Turns a source listing URL into a minimal listing. Unknown hosts and non-listing
     * paths are ignored so the system browser keeps handling everything else.
     */
    private fun handleDeepLink(intent: Intent?): AppListing? {
        val data = intent?.data ?: return null
        if (data.scheme != "https") return null
        val host = data.host?.lowercase() ?: return null
        val path = data.path?.trim('/') ?: return null
        if (path.isBlank()) return null

        val source = when (host) {
            "modyolo.com", "www.modyolo.com" -> SourceId.MODYOLO
            // Source link-only sites (HappyMod, A1N) open in the system browser instead.
            else -> return null
        }

        // The slug without its extension matches the identity ModyoloSource itself
        // produces (ListingIdFactory.create(id.name, slug)), so favourites, saves and
        // the download history agree with a listing the user reached normally.
        val slug = path.substringBeforeLast(".html")
        if (slug.isBlank()) return null

        return AppListing(
            id = ListingIdFactory.create(source.name, slug),
            appName = slug.replace('-', ' ').trim().ifBlank { slug },
            sourceId = source,
            sourceName = SourceInfo.MODYOLO.displayName,
            sourceListingUrl = "https://$host/$path",
        )
    }
}
