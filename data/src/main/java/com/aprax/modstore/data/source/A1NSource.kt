package com.aprax.modstore.data.source

import com.aprax.modstore.data.network.HttpFetcher
import com.aprax.modstore.domain.model.AppError
import com.aprax.modstore.domain.model.AppListing
import com.aprax.modstore.domain.model.Page
import com.aprax.modstore.domain.model.SourceAvailability
import com.aprax.modstore.domain.model.SourceId
import com.aprax.modstore.domain.model.SourceInfo
import com.aprax.modstore.domain.repository.CatalogSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A1N.
 *
 * a1n.com currently serves a "this domain is for sale" notice and operates no APK
 * catalogue. Rather than invent listings to fill a source slot, this provider reports that
 * honestly:
 *  * [availability] returns [SourceAvailability.NoCatalogue] with the reason;
 *  * every listing call returns the same typed refusal, which the UI renders as a clear
 *    "no catalogue available" state with a link to the site.
 *
 * It is registered exactly like the working sources, so the moment A1N publishes a
 * catalogue with an authorized access method, enabling it is a matter of implementing the
 * three listing methods below. Nothing else in the app changes.
 */
@Singleton
class A1NSource @Inject constructor(
    private val http: HttpFetcher,
) : CatalogSource {

    override val id: SourceId = SourceId.A1N

    override val info: SourceInfo = SourceInfo.A1N

    /**
     * Checks the live site rather than hard-coding the conclusion, so if A1N ever starts
     * serving a catalogue the app notices instead of being permanently stuck on "unavailable".
     */
    override suspend fun availability(): SourceAvailability {
        val reachable = http.ping(info.baseUrl, info.displayName)
        if (!reachable) {
            return SourceAvailability.TemporarilyUnavailable(
                AppError.SourceUnavailable(id, info.displayName)
            )
        }
        // The domain resolves but does not offer an indexable catalogue or an API.
        return SourceAvailability.NoCatalogue(
            "a1n.com currently serves a domain notice and publishes no APK listings. " +
                "No listings are shown for this source."
        )
    }

    override suspend fun recent(page: Int, pageSize: Int): Result<Page<AppListing>> =
        Result.failure(noCatalogue())

    override suspend fun byCategory(
        categorySlug: String,
        page: Int,
        pageSize: Int,
    ): Result<Page<AppListing>> = Result.failure(noCatalogue())

    override suspend fun search(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<Page<AppListing>> = Result.failure(noCatalogue())

    override suspend fun details(sourceListingUrl: String): Result<AppListing> =
        Result.failure(noCatalogue())

    private fun noCatalogue() = AppError.NoProgrammaticAccess(id, info.displayName)
}
