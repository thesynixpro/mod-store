package com.aprax.modstore.domain.model

/** Static, human-facing facts about a source. Used for attribution and the sources screen. */
data class SourceInfo(
    val id: SourceId,
    val displayName: String,
    val baseUrl: String,
    /** Short description of what the site publishes. */
    val summary: String,
    /**
     * True when the app has an authorized, publicly documented way to read listings.
     * When false the app only ever links out to the site.
     */
    val supportsProgrammaticAccess: Boolean,
    /** Plain-language note about how this source is used, shown in Settings. */
    val usageNote: String,
) {
    companion object {
        val MODYOLO = SourceInfo(
            id = SourceId.MODYOLO,
            displayName = "Modyolo",
            baseUrl = "https://modyolo.com",
            summary = "Modded apps and games with published version and size details.",
            supportsProgrammaticAccess = true,
            usageNote = "Read through Modyolo's own public WordPress REST API. " +
                "robots.txt allows general crawling, requests are rate-limited and cached, " +
                "and downloads always go to the source's own page.",
        )

        val HAPPYMOD = SourceInfo(
            id = SourceId.HAPPYMOD,
            displayName = "HappyMod",
            baseUrl = "https://happymod.com",
            summary = "Large catalogue of modded games and apps.",
            supportsProgrammaticAccess = false,
            usageNote = "HappyMod publishes no public API, and this app does not bypass " +
                "its protections. Search results are read from the public result pages " +
                "that are served to any browser, requests are rate-limited, and every " +
                "download is handed to HappyMod's own page in a browser.",
        )

        val A1N = SourceInfo(
            id = SourceId.A1N,
            displayName = "A1N",
            baseUrl = "https://a1n.com",
            summary = "Domain that does not currently operate an APK catalogue.",
            supportsProgrammaticAccess = false,
            usageNote = "At the time of writing a1n.com serves a domain-for-sale notice " +
                "and hosts no app listings. The app therefore reports A1N as having no " +
                "available catalogue rather than inventing listings. If A1N later " +
                "publishes a catalogue and an authorized access method, it can be enabled " +
                "by switching its provider on; no other code needs to change.",
        )

        val All: List<SourceInfo> = listOf(MODYOLO, HAPPYMOD, A1N)
    }
}

/** Why a source currently cannot be used, or [Available] when it can. */
sealed interface SourceAvailability {
    /** The source is reachable and queryable. */
    data object Available : SourceAvailability

    /** The source has no catalogue to read (for example a parked domain). */
    data class NoCatalogue(val reason: String) : SourceAvailability

    /** The source has a catalogue but exposes no authorized programmatic access. */
    data class LinkOnly(val reason: String) : SourceAvailability

    /** A transient problem; worth retrying. */
    data class TemporarilyUnavailable(val error: AppError) : SourceAvailability
}

/** A source's identity plus its current state, as rendered by the UI. */
data class SourceStatus(
    val info: SourceInfo,
    val availability: SourceAvailability,
    /** Whether the user has this source switched on in Settings. */
    val enabledByUser: Boolean,
) {
    val isUsable: Boolean
        get() = enabledByUser &&
            (availability is SourceAvailability.Available ||
                availability is SourceAvailability.TemporarilyUnavailable)

    val isLinkOnly: Boolean get() = availability is SourceAvailability.LinkOnly
}
