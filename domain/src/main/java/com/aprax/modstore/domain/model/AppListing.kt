package com.aprax.modstore.domain.model

/**
 * Identifies the external site a listing came from.
 *
 * Every listing in the app carries one of these so the UI can always tell the user which
 * third-party website published it. Nothing in the app re-hosts or re-signs content: the
 * source site remains the point of origin for both the metadata and the download.
 */
enum class SourceId {
    MODYOLO,
    HAPPYMOD,
    A1N;

    companion object {
        fun fromKey(key: String): SourceId? =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

/**
 * How much is actually known about a downloaded or listed artifact.
 *
 * This enum deliberately has no "SAFE" member. The app cannot verify that a third-party
 * APK is free of malware, so the strongest honest states are "we have a checksum the user
 * can compare" and "the APK's own signing certificate was verified locally".
 */
enum class SecurityStatus {
    /** Nothing was assessed. This is the default and the honest answer for most listings. */
    UNKNOWN,

    /** The source published a checksum, so the download can be integrity-checked. */
    CHECKSUM_AVAILABLE,

    /** The downloaded file's APK signing certificate was read and is internally consistent. */
    SIGNATURE_VERIFIED,
}

/**
 * One installable artifact offered by a listing.
 *
 * A single listing can expose several variants (for example an original build plus a
 * modified build, or architecture-specific splits), so the details screen renders these
 * as a list of download options.
 */
data class AppVariant(
    val label: String,
    val versionName: String? = null,
    val fileSize: String? = null,
    val modInfo: String? = null,
    /** Direct artifact URL only when the source exposes one publicly. Otherwise null. */
    val downloadUrl: String? = null,
    /** Always present: the human-facing page for this variant on the source site. */
    val sourceListingUrl: String,
)

/**
 * The canonical application model shared by every layer.
 *
 * Field names follow the project's agreed contract. Several fields are nullable on purpose:
 * third-party sites do not all publish the same metadata, and inventing a value (a fake
 * version code, a fake size, a fake rating) would be worse than showing "unknown".
 */
data class AppListing(
    /** Stable local identity, namespaced by source so two sites cannot collide. */
    val id: String,
    val appName: String,
    val sourceId: SourceId,
    /** Human-readable source name for attribution, e.g. "Modyolo". */
    val sourceName: String,
    /** The listing page on the source site. Always present; the basis of attribution. */
    val sourceListingUrl: String,

    val packageName: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    val versionName: String? = null,
    val versionCode: Long? = null,
    /** Size exactly as the source published it, e.g. "80M", because that is the evidence. */
    val fileSize: String? = null,
    /** Parsed byte count when the published size was unambiguous. */
    val fileSizeBytes: Long? = null,
    val category: String? = null,
    /** Direct APK URL only when the source makes one publicly available. Otherwise null. */
    val downloadUrl: String? = null,
    /** ISO-8601 date string when the source published one. */
    val lastUpdated: String? = null,
    val androidRequirements: String? = null,
    val securityStatus: SecurityStatus = SecurityStatus.UNKNOWN,

    val developer: String? = null,
    val modInfo: String? = null,
    /** Only ever set when the source itself publishes a rating. Never synthesised. */
    val ratingValue: Double? = null,
    /** Only ever set when the source itself publishes a download count. Never synthesised. */
    val downloadCount: Long? = null,
    val variants: List<AppVariant> = emptyList(),
    val permissions: List<String> = emptyList(),
) {
    /** True when [downloadUrl] is present, i.e. the app can hand the download to Android. */
    val hasDirectDownload: Boolean get() = !downloadUrl.isNullOrBlank()

    /**
     * True when at least one variant exposes a direct URL, so the details screen can offer
     * a working "Download APK" action instead of only the source link.
     */
    val hasAnyDirectDownload: Boolean
        get() = hasDirectDownload || variants.any { !it.downloadUrl.isNullOrBlank() }

    /** Package identity used to de-duplicate the same app across different sources. */
    val dedupeKey: String get() = packageName?.lowercase() ?: appName.trim().lowercase()
}
