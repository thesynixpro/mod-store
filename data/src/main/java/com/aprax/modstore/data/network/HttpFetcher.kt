package com.aprax.modstore.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.aprax.modstore.domain.model.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects connectivity so failures can be reported as "you're offline" rather than as
 * "the source is broken", which is a materially different thing to tell a user.
 */
interface NetworkMonitor {
    fun isOnline(): Boolean
}

@Singleton
class AndroidNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * The single HTTP entry point for every source provider.
 *
 * Centralising requests buys four things the brief requires:
 *  * one disk cache, so a repeat visit costs no network at all;
 *  * one rate limiter per host, so the app is a well-behaved client;
 *  * one honest User-Agent that identifies the app, so a site owner can see who is
 *    calling and contact us rather than just blocking an anonymous scraper;
 *  * one place that translates transport failures into typed [AppError]s.
 */
@Singleton
class HttpFetcher @Inject constructor(
    private val client: OkHttpClient,
    private val monitor: NetworkMonitor,
    private val rateLimiter: HostRateLimiter,
) {

    /** Fetches [url] as text, using the cache when it is fresh enough. */
    suspend fun getText(
        url: String,
        sourceId: String,
        sourceName: String,
        cachePolicy: CachePolicy = CachePolicy.PreferCache,
        headers: Map<String, String> = emptyMap(),
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!monitor.isOnline()) return@withContext Result.failure(AppError.Offline())

        val permitted = rateLimiter.acquire(url)
        if (!permitted) {
            return@withContext Result.failure(AppError.RateLimited())
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .apply {
                headers.forEach { (key, value) -> header(key, value) }
                cacheControl(cachePolicy.toOkHttpCacheControl())
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    return@withContext Result.failure(AppError.RateLimited(retryAfter))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        if (response.code == 404) AppError.NotFound()
                        else AppError.SourceUnavailable(
                            sourceId = com.aprax.modstore.domain.model.SourceId.fromKey(sourceId)
                                ?: com.aprax.modstore.domain.model.SourceId.MODYOLO,
                            sourceName = sourceName,
                        )
                    )
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Result.failure(AppError.Malformed(sourceName))
                } else {
                    Result.success(body)
                }
            }
        } catch (error: UnknownHostException) {
            Result.failure(AppError.SourceUnavailable(sourceIdOf(sourceId), sourceName, error))
        } catch (error: SocketTimeoutException) {
            Result.failure(AppError.Timeout(sourceName))
        } catch (error: IOException) {
            Result.failure(AppError.SourceUnavailable(sourceIdOf(sourceId), sourceName, error))
        } catch (error: Exception) {
            Result.failure(AppError.Unexpected(error))
        }
    }

    /**
     * Checks that a URL is reachable without downloading the body.
     *
     * Used by [availability] checks so a provider can report honestly whether a site is
     * actually serving content, at the cost of a single HEAD request.
     */
    suspend fun ping(url: String, sourceName: String): Boolean = withContext(Dispatchers.IO) {
        if (!monitor.isOnline()) return@withContext false
        // A ping deliberately bypasses the rate limiter's hard reject: it is one cheap
        // request and backing off must not make every source look dead.
        delay(PING_SPACING_MS)
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", USER_AGENT)
            .cacheControl(CacheControl.Builder().noCache().build())
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful || it.code in 300..399 }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val PING_SPACING_MS = 120L

        /**
         * Identifies the app honestly. A site owner seeing unexpected traffic can tell what
         * is calling and reach the project rather than silently blocking it.
         */
        const val USER_AGENT =
            "ApraxModsStore/1.0 (Android; app listing index; +https://aprax.example/mods-store)"

        private fun sourceIdOf(key: String) =
            com.aprax.modstore.domain.model.SourceId.fromKey(key)
                ?: com.aprax.modstore.domain.model.SourceId.MODYOLO
    }
}

/** How aggressively to use the HTTP cache. */
enum class CachePolicy {
    /** Serve from cache when present; refresh in the background (stale-while-revalidate). */
    PreferCache,

    /** Normal HTTP caching semantics. */
    Default,

    /** Skip the cache entirely. */
    NoCache;

    fun toOkHttpCacheControl(): CacheControl = when (this) {
        PreferCache -> CacheControl.Builder()
            .maxStale(30, TimeUnit.MINUTES)
            .maxAge(5, TimeUnit.MINUTES)
            .build()
        Default -> CacheControl.Builder().build()
        NoCache -> CacheControl.Builder().noCache().build()
    }
}

/**
 * A simple per-host rate limiter.
 *
 * The brief requires respecting each site: no source is polled faster than
 * [MIN_INTERVAL_MS] apart, and a burst is refused rather than queued, so a stuck screen can
 * never turn into a hammering loop. Refusal surfaces as a typed rate-limit error the UI
 * shows as "try again shortly".
 */
@Singleton
class HostRateLimiter @Inject constructor() {

    private val lastRequestAt = mutableMapOf<String, Long>()
    private val lock = Any()

    /** @return true when the request may proceed now. */
    fun acquire(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return true
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val previous = lastRequestAt[host]
            if (previous != null && now - previous < MIN_INTERVAL_MS) {
                return false
            }
            lastRequestAt[host] = now
            return true
        }
    }

    /** Allows the next call for [host] through even if it is inside the window. */
    fun forceReset(host: String) {
        synchronized(lock) { lastRequestAt.remove(host) }
    }

    companion object {
        /** Minimum gap between two requests to the same host. */
        const val MIN_INTERVAL_MS = 900L
    }
}

/** Builds the shared client. Kept out of the Hilt module so it is easy to unit test. */
object HttpClientFactory {

    private const val CACHE_SIZE_BYTES = 24L * 1024 * 1024

    fun create(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http-source-cache")
        return OkHttpClient.Builder()
            .cache(Cache(cacheDir, CACHE_SIZE_BYTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
