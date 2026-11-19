package com.aprax.modstore.data.source

import com.aprax.modstore.core.util.ListingIdFactory
import com.aprax.modstore.core.util.UrlSafety
import com.aprax.modstore.data.network.CachePolicy
import com.aprax.modstore.data.network.HttpFetcher
import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.AppVariant
import com.aprax.modstore.domain.model.Page
import com.aprax.modstore.domain.model.SecurityStatus
import com.aprax.modstore.domain.model.SourceAvailability
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SourceInfo
import com.aprax.modstore.domain.repository.CatalogSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HappyMod, read from the public result pages a browser receives.
 *
 * Honest scope of this provider:
 *  * HappyMod publishes no public API, and this app does not attempt to bypass any
 *    protection, sign-in or anti-bot measure. It requests the same search and category
 *    pages an ordinary visitor would load, with an identifying User-Agent, through the
 *    shared per-host rate limiter and HTTP cache.
 *  * It never constructs a download URL — not even a "download page" URL — and it never
 *    fabricates a rating, download count or version.
 *  * Every listing it returns carries the HappyMod page as [AppListing.sourceListingUrl],
 *    which is what the UI opens when the user taps through.
 *
 * Because HTML layouts change without warning, every extraction is defensive: a failed
 * selector yields fewer fields or an empty page, never an exception that reaches the UI.
 */
@Singleton
class HappyModSource @Inject constructor(
    private val http: HttpFetcher,
) : CatalogSource {

    override val id: SourceId = SourceId.HAPPYMOD

    override val info: SourceInfo = SourceInfo.HAPPYMOD

    private val baseUrl: String get() = info.baseUrl

    override suspend fun availability(): SourceAvailability =
        if (http.ping(baseUrl, info.displayName)) {
            SourceAvailability.Available
        } else {
            SourceAvailability.TemporarilyUnavailable(
                AppError.SourceUnavailable(id, info.displayName)
            )
        }

    override suspend fun recent(page: Int, pageSize: Int): Result<Page<AppListing>> =
        browse(path = if (page <= 1) "/new/latest/" else "/new/latest/$page.html", page = page, pageSize = pageSize)

    override suspend fun byCategory(
        categorySlug: String,
        page: Int,
        pageSize: Int,
    ): Result<Page<AppListing>> = browse(
        // Games and apps share slug names on HappyMod, so a category is queried under both
        // roots and the results merged; a slug that does not exist simply returns nothing.
        path = if (page <= 1) {
            "/games/$categorySlug/"
        } else {
            "/games/$categorySlug/$page.html"
        },
        page = page,
        pageSize = pageSize,
        secondaryPath = if (page <= 1) "/apps/$categorySlug/" else "/apps/$categorySlug/$page.html",
    )

    override suspend fun search(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<Page<AppListing>> = browse(
        path = buildSearchPath(query, page),
        page = page,
        pageSize = pageSize,
    )

    override suspend fun details(sourceListingUrl: String): Result<AppListing> {
        val safeUrl = UrlSafety.sanitise(sourceListingUrl)
            ?: return Result.failure(AppError.NotFound())
        val baseHost = UrlSafety.hostOf(baseUrl) ?: return Result.failure(AppError.NotFound())
        if (!UrlSafety.isHostedOn(safeUrl, baseHost)) {
            return Result.failure(AppError.NotFound())
        }

        val html = http.getText(
            url = safeUrl,
            sourceId = id.name,
            sourceName = info.displayName,
            cachePolicy = CachePolicy.Default,
        ).getOrElse { error -> return Result.failure(error) }

        return runCatching { parseDetailPage(html, safeUrl) }
            .recoverCatching { throw AppError.Malformed(info.displayName, it) }
    }

    private fun buildSearchPath(query: String, page: Int): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return if (page <= 1) {
            "/search.html?q=$encoded"
        } else {
            "/search.html?q=$encoded&page=$page"
        }
    }

    private suspend fun browse(
        path: String,
        page: Int,
        pageSize: Int,
        secondaryPath: String? = null,
    ): Result<Page<AppListing>> {
        val primaryResult = fetchAndParse(path, pageSize)
        if (secondaryPath == null) {
            return primaryResult.map { items ->
                Page(items = items.take(pageSize), page = page, hasMore = items.size >= pageSize)
            }
        }

        // Merge the games and apps roots, tolerating either one failing.
        val primary = primaryResult.getOrDefault(emptyList())
        val secondary = fetchAndParse(secondaryPath, pageSize).getOrDefault(emptyList())
        val merged = dedupeByIdentity(primary + secondary).take(pageSize)
        return Result.success(
            Page(items = merged, page = page, hasMore = merged.size >= pageSize)
        )
    }

    private suspend fun fetchAndParse(path: String, pageSize: Int): Result<List<AppListing>> {
        val url = "$baseUrl$path"
        val html = http.getText(
            url = url,
            sourceId = id.name,
            sourceName = info.displayName,
            cachePolicy = CachePolicy.PreferCache,
        ).getOrElse { error -> return Result.failure(error) }

        return runCatching {
            parseListingPage(html, url).take(pageSize)
        }.recoverCatching { throw AppError.Malformed(info.displayName, it) }
    }

    private fun dedupeByIdentity(listings: List<AppListing>): List<AppListing> {
        val seen = HashSet<String>(listings.size)
        return listings.filter { seen.add(it.dedupeKey) }
    }

    // ---------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------

    /**
     * Extracts result rows from a listing page.
     *
     * HappyMod renders results as a series of cards; the anchors carry the title and link,
     * and a sibling node carries the version/mod tags and the published rating. Each card
     * is read independently so one malformed card cannot discard the rest of the page.
     */
    internal fun parseListingPage(html: String, pageUrl: String): List<AppListing> {
        val document = Jsoup.parse(html, pageUrl)
        val host = UrlSafety.hostOf(pageUrl) ?: return emptyList()

        // The result anchors are the app titles; selecting on them is far more robust than
        // depending on generated class names, which the site rotates.
        val anchors = document.select(
            "a.comment-child, a[href\$=.html], .news-item a[href], .list-page a[href]"
        ).filter { anchor ->
            val text = anchor.text().trim()
            text.length in 3..120 && looksLikeAppLink(anchor.attr("href"))
        }

        val results = linkedMapOf<String, AppListing>()
        for (anchor in anchors) {
            val listing = toListing(anchor, pageUrl, host) ?: continue
            results.putIfAbsent(listing.id, listing)
        }
        return results.values.toList()
    }

    private fun looksLikeAppLink(href: String): Boolean {
        if (href.isBlank()) return false
        // Skip navigation, category and pagination links.
        val skipMarkers = listOf("/search", "/category/", "/games/", "/apps/", "javascript:", "#")
        return skipMarkers.none { href.startsWith(it) }
    }

    private fun toListing(anchor: Element, pageUrl: String, host: String): AppListing? {
        val href = anchor.attr("href")
        val resolved = UrlSafety.resolve(pageUrl, href) ?: return null
        if (!UrlSafety.isHostedOn(resolved, host)) return null

        val rawTitle = anchor.attr("title").ifBlank { anchor.text() }
        val appName = TextCleanup.cleanTitle(rawTitle) ?: return null
        if (appName.isBlank()) return null

        val card = anchor.parent() ?: anchor

        val iconUrl = card.selectFirst("img[src], img[data-src]")?.let { image ->
            val source = image.attr("src").ifBlank { image.attr("data-src") }
            UrlSafety.resolve(pageUrl, source)
        }

        val version = extractVersion(rawTitle)
        val modInfo = extractModTags(rawTitle)

        // The card prints a rating; take it only when it is a real number. Nothing is invented.
        val rating = card.text()
            .let { RATING_REGEX.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

        val slug = resolved.substringAfterLast('/').substringBeforeLast(".html")

        return AppListing(
            id = ListingIdFactory.create(id.name, slug),
            appName = appName,
            sourceId = id,
            sourceName = info.displayName,
            sourceListingUrl = resolved,
            description = null,
            iconUrl = iconUrl,
            versionName = version,
            category = null,
            // Never a constructed download URL. The source page is the destination.
            downloadUrl = null,
            lastUpdated = null,
            securityStatus = SecurityStatus.UNKNOWN,
            modInfo = modInfo,
            ratingValue = rating,
        )
    }

    /** Reads the detail page's published facts. */
    internal fun parseDetailPage(html: String, pageUrl: String): AppListing {
        val document = Jsoup.parse(html, pageUrl)

        val name = document.selectFirst("h1")?.text()?.let { TextCleanup.cleanTitle(it) }
        val title = document.title()?.let { TextCleanup.cleanTitle(it) }
        val appName = name ?: title ?: pageUrl.substringAfterLast('/').substringBefore(".html")

        val version = readMeta(document, "Version")
            ?: extractVersion(document.title() ?: "")
        val size = readMeta(document, "Size")
        val publisher = readMeta(document, "Publisher")
            ?: readMeta(document, "Developer")
        val category = readMeta(document, "Category")
        val requirements = readMeta(document, "Requires")

        val description = TextCleanup.summarise(
            document.selectFirst(".description, .app-desc, article p")?.text(),
            maxChars = 400,
        )

        val iconUrl = UrlSafety.resolve(pageUrl, document.selectFirst("img.app-icon, .app-info img, img[src]")?.attr("src"))
        val screenshots = document.select(".screenshot img, .carousel img")
            .mapNotNull { UrlSafety.resolve(pageUrl, it.attr("src").ifBlank { it.attr("data-src") }) }
            .distinct()
            .take(MAX_SCREENSHOTS)

        val packageName = PACKAGE_REGEX.find(document.text())?.groupValues?.get(1)
        val slug = pageUrl.substringAfterLast('/').substringBeforeLast(".html")

        val base = AppListing(
            id = ListingIdFactory.create(id.name, slug),
            appName = appName,
            sourceId = id,
            sourceName = info.displayName,
            sourceListingUrl = pageUrl,
            description = description,
            iconUrl = iconUrl,
            screenshots = screenshots,
            versionName = version,
            fileSize = size,
            fileSizeBytes = com.aprax.modstore.domain.usecase.ByteFormat.parseSourceSize(size),
            category = category,
            packageName = packageName,
            developer = publisher,
            androidRequirements = requirements,
            lastUpdated = null,
            downloadUrl = null,
            securityStatus = SecurityStatus.UNKNOWN,
        )

        return base.copy(
            variants = listOf(
                AppVariant(
                    label = listOfNotNull(base.appName, version?.let { "v$it" }).joinToString(" "),
                    versionName = version,
                    fileSize = size,
                    downloadUrl = null,
                    sourceListingUrl = pageUrl,
                )
            )
        )
    }

    /** Reads a labelled value from a spec table or definition list. */
    private fun readMeta(document: Document, label: String): String? {
        document.select("tr").forEach { row ->
            val cells = row.select("td, th")
            if (cells.size >= 2 && cells[0].text().trim().startsWith(label, ignoreCase = true)) {
                return TextCleanup.stripHtml(cells[1].text())
            }
        }
        document.select(".app-info li, .spec li, dl dt").forEach { element ->
            val text = element.text()
            if (text.startsWith(label, ignoreCase = true)) {
                val value = text.substringAfter(':', "").trim()
                if (value.isNotBlank() && value != text) return TextCleanup.stripHtml(value)
            }
        }
        return null
    }

    /** Extracts the version from a title such as "Name Mod Apk 1.21.90.23 [Mod Menu]". */
    private fun extractVersion(title: String): String? =
        VERSION_REGEX.find(title)?.groupValues?.get(1)

    /** Extracts bracketed mod tags, e.g. "[Mod Menu][Unlimited Money]", as reported. */
    private fun extractModTags(title: String): String? {
        val tags = MOD_TAG_REGEX.findAll(title).map { it.groupValues[1] }.toList()
        return tags.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private companion object {
        const val MAX_SCREENSHOTS = 8

        val VERSION_REGEX = Regex(
            "\\b(?:v|version\\s*)?(\\d+(?:\\.\\d+){1,3}[a-z0-9.\\-]*)\\b",
            RegexOption.IGNORE_CASE,
        )
        val MOD_TAG_REGEX = Regex("\\[([^\\]]{2,40})\\]")
        val RATING_REGEX = Regex("\\b([0-5](?:\\.\\d)?)\\b")
        val PACKAGE_REGEX = Regex("\\b([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]{2,}){2,})\\b")
    }
}
