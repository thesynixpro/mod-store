package com.aprax.modstore.data.di

import android.content.Context
import androidx.room.Room
import com.aprax.modstore.data.local.ApraxDatabase
import com.aprax.modstore.data.local.DownloadDao
import com.aprax.modstore.data.local.LibraryDao
import com.aprax.modstore.data.local.ListingDao
import com.aprax.modstore.data.local.SearchHistoryDao
import com.aprax.modstore.data.local.SourceHealthDao
import com.aprax.modstore.data.network.HostRateLimiter
import com.aprax.modstore.data.network.HttpClientFactory
import com.aprax.modstore.data.network.HttpFetcher
import com.aprax.modstore.data.network.NetworkMonitor
import com.aprax.modstore.data.network.AndroidNetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** Persistence bindings. The database is a single instance for the whole process. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ApraxDatabase =
        Room.databaseBuilder(context, ApraxDatabase::class.java, ApraxDatabase.NAME)
            // The listing cache is disposable: if a schema ever mismatches, dropping and
            // refetching is correct and far better than crashing on launch.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideListingDao(database: ApraxDatabase): ListingDao = database.listingDao()

    @Provides
    fun provideLibraryDao(database: ApraxDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideSearchHistoryDao(database: ApraxDatabase): SearchHistoryDao =
        database.searchHistoryDao()

    @Provides
    fun provideDownloadDao(database: ApraxDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideSourceHealthDao(database: ApraxDatabase): SourceHealthDao =
        database.sourceHealthDao()
}

/** Networking bindings. One client, one fetcher, one limiter for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        HttpClientFactory.create(context)

    @Provides
    @Singleton
    fun provideHttpFetcher(
        client: OkHttpClient,
        monitor: NetworkMonitor,
        rateLimiter: HostRateLimiter,
    ): HttpFetcher = HttpFetcher(client, monitor, rateLimiter)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor
}
