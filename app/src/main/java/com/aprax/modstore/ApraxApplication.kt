package com.aprax.modstore

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Wires three things that must exist before any screen renders:
 *  * Hilt, so the object graph is available to every view model and worker;
 *  * WorkManager with a custom factory, so the download worker can be injected;
 *  * a single shared image loader with a bounded disk cache, so scrolling the home feed
 *    does not re-download the same artwork on every visit.
 */
@HiltAndroidApp
class ApraxApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager is initialised lazily via this provider rather than through its default
     * initialiser (which is removed in the manifest). Without this, the injected
     * HiltWorkerFactory is ignored and the download worker fails to instantiate.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Coil's default singleton must point at the configured loader before the first
        // image request, which can happen during the first composition.
        SingletonImageLoader.setSafe(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    // A percentage of the app's allowed memory, not of the whole device.
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image-cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
