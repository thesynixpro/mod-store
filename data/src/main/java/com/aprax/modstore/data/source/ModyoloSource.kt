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
import com.aprax.modstore.domain.usecase.ByteFormat
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modyolo, read through the site's own public WordPress REST API.
 *
 * Why this is the one source the app can index properly:
 *  * `https://modyolo.com/robots.txt` allows general crawling and only disallows
 *    `/wp-admin/`, so reading published posts is permitted;
 *  * the site itself exposes `wp-json/wp/v2/posts`, a documented WordPress endpoint, and
 *    its own schema advertises the `?s={term}` search action;
 *  * the responses already contain structured fields (dates, categories, Yoast SEO
 *    metadata including the featured image and breadcrumb path), so there is no need to
 *    guess at a page layout.
 *
 * What this provider deliberately does not do:
 *  * it never constructs a download URL. Modyolo's own download pages state that direct
 *    links are blocked to prevent abuse, so the app sends the user to the source page
 *    instead and reports [SecurityStatus.UNKNOWN] because nothing was verified here;
 *  * it never fabricates a version, size or date — fields absent from the API stay null.
 */
@Singleton
class ModyoloSource @Inject constructor(
    private val http: HttpFetcher,
) : CatalogSource {

    override val id: SourceId = SourceId.MODYOLO

    override val info: SourceInfo = SourceInfo.MODYOLO

    private val baseUrl: String get() = info.baseUrl

    override suspend fun availability(): SourceAvailability =
        if (http.ping(baseUrl, info.displayName)) {
            SourceAvailability.Available
        } else {
            SourceAvailability.TemporarilyUnavailable(AppError.SourceUnavailable(id, info.displayName))
        }

    override suspend fun recent(page: Int, pageSize: Int): Result<Page<AppListing>> =
        fetchPosts(page = page, pageSize = pageSize)

    override suspend fun byCategory(
        categorySlug: String,
        page: Int,
        pageSize: Int,
    ): Result<Page<AppListing>> = fetchPosts(
        page = page,
        pageSize = pageSize,
        // WordPress accepts a comma-separated slug list for `categories`.
        extraQuery = mapOf("categories" to categorySlug),
    )

    override suspend fun search(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<Page<AppListing>> = fetchPosts(
        page = page,
        pageSize = pageSize,
        extraQuery = mapOf("search" to query),
    )

    /**
     * Reads a listing page and extracts the facts the page actually publishes.
     *
     * The REST payload already carries name, description, image and dates. Version, size,
     * publisher and package name only appear in the rendered page, so this fetches that
     * page and reads the labelled table rows rather than guessing a URL shape.
     */
    override suspend fun details(sourceListingUrl: String): Result<AppListing> {
        val safeUrl = UrlSafety.sanitise(sourceListingUrl)
            ?: return Result.failure(AppError.NotFound())
        if (!UrlSafety.isHostedOn(safeUrl, UrlSafety.hostOf(baseUrl) ?: return Result.failure(AppError.NotFound()))) {
            return Result.failure(AppError.NotFound())
        }

        val slug = safeUrl.substringAfterLast('/').substringBefore(".html")
        val summary = fetchPosts(
            page = 1,
            pageSize = 1,
            extraQuery = mapOf("slug" to slug),
        ).getOrElse { error -> return Result.failure(error) }

        val listing = summary.items.firstOrNull()
            ?: return Result.failure(AppError.NotFound())

        val html = http.getText(
            url = safeUrl,
            sourceId = id.name,
            sourceName = info.displayName,
            cachePolicy = CachePolicy.Default,
        ).getOrElse { error ->
            // The summary is still useful even when the detail page will not load, so the
            // caller gets the listing rather than a hard failure.
            return Result.success(listing)
        }

        return Result.success(parseDetailPage(listing, html, safeUrl))
    }

    // ---------------------------------------------------------------------------------
    // REST API path
    // ---------------------------------------------------------------------------------

    private suspend fun fetchPosts(
        page: Int,
        pageSize: Int,
        extraQuery: Map<String, String> = emptyMap(),
    ): Result<Page<AppListing>> {
        val url = buildString {
            append(baseUrl)
            append("/wp-json/wp/v2/posts?per_page=")
            append(pageSize.coerceIn(1, MAX_PAGE_SIZE))
            append("&page=")
            append(page.coerceAtLeast(1))
            append("&orderby=modified&order=desc")
            // Ask only for the fields actually used: a smaller payload is faster and
            // imposes less load on the source.
            append("&_fields=id,date,modified,slug,link,title,excerpt,categories,featured_media,yoast_head_json")
            extraQuery.forEach { (key, value) ->
                append('&')
                append(key)
                append('=')
                append(java.net.URLEncoder.encode(value, "UTF-8"))
            }
        }

        val body = http.getText(
            url = url,
            sourceId = id.name,
            sourceName = info.displayName,
            cachePolicy = CachePolicy.PreferCache,
        ).getOrElse { error -> return Result.failure(error) }

        return runCatching {
            val array = JSONArray(body)
            val items = buildList(array.length()) {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { add(parsePost(it)) }
                }
            }
            // A full page almost always means more behind it; a short page means the end.
            Page(items = items, page = page, hasMore = items.size >= pageSize)
        }.recoverCatching { throw AppError.Malformed(info.displayName, it) }
    }

    /** Maps one WordPress post object onto the app model. */
    private fun parsePost(post: JSONObject): AppListing {
        val slug = Json.string(post, "slug") ?: "post-${Json.long(post, "id") ?: 0L}"
        val link = Json.string(post, "link") ?: "$baseUrl/$slug.html"

        val rawTitle = Json.nestedString(post, "title", "rendered")
        val yoast = Json.objectOrNull(post, "yoast_head_json")
        val ogTitle = Json.string(yoast, "og_title")

        val appName = TextCleanup.cleanTitle(rawTitle)
            ?: TextCleanup.cleanTitle(ogTitle)
            ?: slug.replace('-', ' ')

        val description = TextCleanup.summarise(Json.nestedString(post, "excerpt", "rendered"))
            ?: TextCleanup.summarise(Json.string(yoast, "og_description"))

        val iconUrl = UrlSafety.sanitise(Json.string(Json.first(Json.arrayOrNull(yoast, "og_image")), "url"))

        val modified = Json.string(post, "modified") ?: Json.string(post, "date")
        val categoryPath = breadcrumbCategory(yoast)

        // Yoast titles embed the version, e.g. "Takashi Ninja Warrior v4.1.7 MOD APK …".
        val versionName = extractVersionFromTitle(ogTitle ?: rawTitle)

        return AppListing(
            id = ListingIdFactory.create(id.name, slug),
            appName = appName,
            sourceId = id,
            sourceName = info.displayName,
            sourceListingUrl = UrlSafety.sanitise(link) ?: "$baseUrl/$slug.html",
            description = description,
            iconUrl = iconUrl,
            versionName = versionName,
            category = categoryPath,
            // No direct URL: the app links to the source page rather than guessing one.
            downloadUrl = null,
            lastUpdated = modified,
            securityStatus = SecurityStatus.UNKNOWN,
        )
    }

    /**
     * Reads the category out of the Yoast breadcrumb, e.g.
     * Home > Games > Role Playing > App  ->  "Role Playing".
     */
    private fun breadcrumbCategory(yoast: JSONObject?): String? {
        val graph = Json.arrayOrNull(Json.objectOrNull(yoast, "schema"), "@graph") ?: return null
        for (index in 0 until graph.length()) {
            val node = graph.optJSONObject(index) ?: continue
            if (Json.string(node, "@type") != "BreadcrumbList") continue
            val items = Json.arrayOrNull(node, "itemListElement") ?: continue
            // Positions: 1 Home, 2 top-level (Games/Apps), 3 the real category, 4 the app.
            val category = items.optJSONObject(2)?.let { Json.string(it, "name") }
            if (!category.isNullOrBlank()) return category
            // Fall back to the deepest non-appcrumb node when the shape is shorter.
            val last = items.optJSONObject(items.length() - 2)?.let { Json.string(it, "name") }
            if (!last.isNullOrBlank() && !last.equals("Home", true)) return last
        }
        return null
    }

    // ---------------------------------------------------------------------------------
    // HTML detail path
    // ---------------------------------------------------------------------------------

    /**
     * Enriches a listing with the labelled facts published on the listing page.
     *
     * The page renders a definition-style table ("App Name", "Publisher", "Genre", "Size",
     * "Latest Version", "MOD Info") plus a Play Store link that carries the package name.
     * Only values actually present are taken.
     */
    internal fun parseDetailPage(
        listing: AppListing,
        html: String,
        pageUrl: String,
    ): AppListing {
        val document = Jsoup.parse(html, pageUrl)

        val facts = readSpecTable(document)

        val packageName = readPackageName(document, pageUrl)
        val screenshots = readScreenshots(document, pageUrl, listing.iconUrl)
        val developer = facts["Publisher"] ?: facts["Developer"]

        val rawSize = facts["Size"]
        val sizeBytes = ByteFormat.parseSourceSize(rawSize)

        val versionFromPage = facts["Latest Version"] ?: facts["Version"]
        val modInfo = facts["MOD Info"] ?: facts["Mod Info"]

        val downloadUrl = findDirectDownload(UrlSafety.hostOf(pageUrl) ?: "modyolo.com")

        return listing.copy(
            packageName = packageName ?: listing.packageName,
            description = listing.description ?: TextCleanup.summarise(
                document.selectFirst(".entry-content p, article p")?.text(),
                maxChars = 400,
            ),
            screenshots = screenshots,
            versionName = versionFromPage ?: listing.versionName,
            fileSize = rawSize ?: listing.fileSize,
            fileSizeBytes = sizeBytes ?: listing.fileSizeBytes,
            developer = developer,
            modInfo = modInfo,
            category = facts["Genre"] ?: listing.category,
            androidRequirements = facts["Requires"] ?: facts["Android"],
            downloadUrl = downloadUrl,
            variants = buildVariants(listing, rawSize, versionFromPage, modInfo, downloadUrl),
        )
    }

    /**
     * Reads the label/value pairs from the page's fact rows.
     *
     * The layout has changed shape over the site's life, so this handles both a real
     * `<table>` and the definition-list/article-row variant, and keys are normalised.
     */
    private fun readSpecTable(document: org.jsoup.nodes.Document): Map<String, String> {
        val facts = linkedMapOf<String, String>()

        // Variant 1: a genuine table with two columns.
        document.select("table tr").forEach { row ->
            val cells = row.select("td, th")
            if (cells.size >= 2) {
                val key = TextCleanup.stripHtml(cells[0].text())
                val value = TextCleanup.stripHtml(cells[1].text())
                if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                    facts[key] = value
                }
            }
        }

        // Variant 2: heading/strong label followed by a sibling value node.
        document.select("p, div, li").forEach { element ->
            val label = element.selectFirst("strong, b, span")?.text()?.let { TextCleanup.stripHtml(it) }
            val value = element.ownText()?.let { TextCleanup.stripHtml(it) }
            if (!label.isNullOrBlank() && !value.isNullOrBlank() && value.length in 1..120) {
                val cleaned = label.trimEnd(':', ' ')
                if (cleaned in KNOWN_LABELS && cleaned !in facts) {
                    facts[cleaned] = value
                }
            }
        }

        return facts
    }

    /** Finds the Play Store link and pulls the package name out of it. */
    private fun readPackageName(document: org.jsoup.nodes.Document, pageUrl: String): String? {
        document.select("a[href*=play.google.com], a[href*=id=]").forEach { anchor ->
            val href = anchor.attr("href")
            val packageName = PACKAGE_REGEX.find(href)?.groupValues?.get(1)
            if (!packageName.isNullOrBlank()) return packageName
        }
        // Some listings print the package name as plain text.
        val text = document.text()
        return PACKAGE_REGEX.find(text)?.groupValues?.get(1)
    }

    /** Collects distinct screenshot images hosted by the source. */
    private fun readScreenshots(
        document: org.jsoup.nodes.Document,
        pageUrl: String,
        iconUrl: String?,
    ): List<String> {
        val host = UrlSafety.hostOf(pageUrl) ?: return emptyList()
        val found = linkedSetOf<String>()
        document.select("img").forEach { image ->
            val source = image.attr("src").ifBlank { image.attr("data-src") }
            val resolved = UrlSafety.resolve(pageUrl, source) ?: return@forEach
            if (!UrlSafety.isHostedOn(resolved, host)) return@forEach
            if (resolved == iconUrl) return@forEach
            // Skip obvious chrome: logos, avatars, sprites.
            val lowered = resolved.lowercase()
            if (SKIP_IMAGE_MARKERS.any { lowered.contains(it) }) return@forEach
            found.add(resolved)
            if (found.size >= MAX_SCREENSHOTS) return@forEach
        }
        return found.toList()
    }

    /**
     * Looks for a download link the source publishes openly.
     *
     * Deliberately returns null. Modyolo's download pages state that third-party direct
     * links are blocked to prevent abuse, and a `/download/<slug>` path is a landing page
     * that records the user's intent rather than an artifact URL. Handing it to the
     * download manager would misrepresent it, so the app exposes the source page instead
     * and the UI offers "Visit original source". If the site ever publishes a real direct
     * link, this is the single place to return it.
     */
    private fun findDirectDownload(@Suppress("UNUSED_PARAMETER") sourceHost: String): String? = null

    private fun buildVariants(
        listing: AppListing,
        size: String?,
        version: String?,
        modInfo: String?,
        directUrl: String?,
    ): List<AppVariant> {
        val label = buildString {
            append(listing.appName)
            if (!version.isNullOrBlank()) append(" v").append(version)
        }
        return listOf(
            AppVariant(
                label = label,
                versionName = version ?: listing.versionName,
                fileSize = size ?: listing.fileSize,
                modInfo = modInfo,
                downloadUrl = directUrl,
                sourceListingUrl = listing.sourceListingUrl,
            )
        )
    }

    /** Pulls a version out of a title like "Name v4.1.7 MOD APK (…)". */
    private fun extractVersionFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        return VERSION_IN_TITLE.find(title)?.groupValues?.get(1)
    }

    private companion object {
        const val MAX_PAGE_SIZE = 40
        const val MAX_SCREENSHOTS = 8

        val KNOWN_LABELS = setOf(
            "App Name", "Publisher", "Developer", "Genre", "Category", "Size",
            "Latest Version", "Version", "MOD Info", "Mod Info", "Requires", "Android",
            "Updated", "Price", "Installs",
        )

        val PACKAGE_REGEX = Regex("(?:id=|package=)([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)")

        val VERSION_IN_TITLE = Regex("\\bv(\\d+(?:\\.\\d+)*[\\w.\\-]*)", RegexOption.IGNORE_CASE)

        val SKIP_IMAGE_MARKERS = listOf(
            "logo", "avatar", "gravatar", "icon-", "sprite", "placeholder",
            "flag", "banner-ads", "ads-",
        )
    }
}
