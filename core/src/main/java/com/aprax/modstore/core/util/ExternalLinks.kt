package com.aprax.modstore.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/**
 * Everything that leaves the app goes through here.
 *
 * The brief forbids the app from pretending a source has an API it does not have, so the
 * common path for "get this app" is: open the source's own page in the user's browser.
 * That keeps the user on the site that published the listing, keeps the site's own
 * download flow (and its warnings) intact, and means the app never handles a URL the
 * source did not offer.
 */
object ExternalLinks {

    /**
     * Opens [url] in a browser.
     *
     * Refuses anything that is not HTTPS, so a bad listing field cannot smuggle a
     * `javascript:` or `intent:` URL into an implicit intent.
     *
     * @return true when a browser activity was actually started.
     */
    fun openSourceSite(context: Context, url: String?): Boolean {
        val safe = UrlSafety.sanitise(url) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, safe.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Opens the Play Store listing for a package, when one is known. */
    fun openPlayStore(context: Context, packageName: String?): Boolean {
        val pkg = packageName?.takeIf { PackageNames.isValid(it) } ?: return false
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(market)
            true
        } catch (_: Exception) {
            openSourceSite(context, "https://play.google.com/store/apps/details?id=$pkg")
        }
    }

    /** Opens the app's own settings page, used by the install-permission prompt. */
    fun openAppSettings(context: Context): Boolean = try {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    /** Opens Android's "install unknown apps" screen for this app. */
    fun openInstallPermissionSettings(context: Context): Boolean = try {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        openAppSettings(context)
    }

    /** Composes a takedown/copyright email. */
    fun composeTakedownEmail(context: Context, address: String, subject: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** Validation for Android package identifiers, so a bad field cannot build a broken intent. */
object PackageNames {

    private val PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]{2,})+$")

    fun isValid(candidate: String?): Boolean =
        candidate != null && candidate.length in 3..200 && PATTERN.matches(candidate)
}
