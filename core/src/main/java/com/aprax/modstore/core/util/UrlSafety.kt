package com.aprax.modstore.core.util

import java.net.URI
import java.net.URISyntaxException

/**
 * URL hygiene.
 *
 * Every outbound link in the app passes through here. The rules exist because the sources
 * are third-party sites whose content the app renders and links to, so it must never
 * launch a non-HTTPS, credentials-bearing, or malformed URL, and must be able to prove
 * which host a link belongs to before opening it in an external browser.
 */
object UrlSafety {

    /** Schemes the app will hand to an external viewer. */
    private val ALLOWED_SCHEMES = setOf("https")

    /**
     * Returns the URL when it is a well-formed HTTPS URL, otherwise null.
     *
     * Rejects http, javascript:, data:, intent:, file: and anything carrying embedded
     * credentials, which are the shapes used to smuggle a link past a naive check.
     */
    fun sanitise(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme !in ALLOWED_SCHEMES) return null
            if (uri.host.isNullOrBlank()) return null
            // Reject `https://user:pass@host/...`, which would leak credentials.
            if (uri.userInfo != null) return null
            trimmed
        } catch (_: URISyntaxException) {
            null
        }
    }

    /** True when [url] is safe to open in a browser. */
    fun isSafe(url: String?): Boolean = sanitise(url) != null

    /** The lowercase host of a URL, or null when unusable. */
    fun hostOf(url: String?): String? =
        sanitise(url)?.let { runCatching { URI(it).host?.lowercase() }.getOrNull() }

    /**
     * True when [url] belongs to [host] or one of its subdomains.
     *
     * Used to confirm a listing's download URL really lives on the source that published
     * it, rather than being redirected to an unrelated host.
     */
    fun isHostedOn(url: String?, host: String): Boolean {
        val actual = hostOf(url) ?: return false
        val expected = host.lowercase().removePrefix("www.")
        val normalisedActual = actual.removePrefix("www.")
        return normalisedActual == expected || normalisedActual.endsWith(".$expected")
    }

    /** Resolves a possibly-relative href found while parsing a page against its base URL. */
    fun resolve(baseUrl: String, href: String?): String? {
        if (href.isNullOrBlank()) return null
        val candidate = href.trim()
        if (candidate.startsWith("//")) {
            return sanitise("https:$candidate")
        }
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            return sanitise(candidate)
        }
        return try {
            val base = URI(baseUrl)
            val resolved = base.resolve(candidate)
            sanitise(resolved.toString())
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Strips query and fragment so the same page links to one identity. */
    fun canonical(url: String?): String? {
        val safe = sanitise(url) ?: return null
        return runCatching {
            val uri = URI(safe)
            URI(uri.scheme, uri.host, uri.path, null, null).toString()
        }.getOrNull() ?: safe
    }
}

/**
 * Builds the stable local identity for a listing: `<source>:<slug>`.
 *
 * Keeping the source in the key means the same app listed on two sites stays two rows,
 * each correctly attributed, instead of one row that silently steals the other's identity.
 */
object ListingIdFactory {

    private val UNSAFE = Regex("[^a-z0-9]+")

    fun create(sourceKey: String, slugOrUrl: String): String {
        val normalised = UNSAFE
            .replace(slugOrUrl.lowercase(), "-")
            .trim('-')
            .take(120)
        return "${sourceKey.lowercase()}:$normalised"
    }
}
