package com.aprax.modstore.data.di

import com.aprax.modstore.data.repository.AggregatedCatalogRepository
import com.aprax.modstore.data.repository.DefaultDownloadRepository
import com.aprax.modstore.data.repository.DefaultLibraryRepository
import com.aprax.modstore.data.repository.DefaultSearchHistoryRepository
import com.aprax.modstore.data.repository.DataStoreSettingsRepository
import com.aprax.modstore.data.repository.PackageInstallerRepository
import com.aprax.modstore.data.source.A1NSource
import com.aprax.modstore.data.source.HappyModSource
import com.aprax.modstore.data.source.ModyoloSource
import com.aprax.modstore.domain.repository.CatalogRepository
import com.aprax.modstore.domain.repository.CatalogSource
import com.aprax.modstore.domain.repository.DownloadRepository
import com.aprax.modstore.domain.repository.InstallerRepository
import com.aprax.modstore.domain.repository.LibraryRepository
import com.aprax.modstore.domain.repository.SearchHistoryRepository
import com.aprax.modstore.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

/**
 * Source registration.
 *
 * Every source is contributed into one `Set<CatalogSource>`. Adding a fourth site means
 * writing one provider and adding one line here; the repository, view models and all seven
 * feature modules keep working untouched. Removing a source is deleting its line.
 */
@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @ElementsIntoSet
    fun provideSources(
        modyolo: ModyoloSource,
        happyMod: HappyModSource,
        a1n: A1NSource,
    ): Set<CatalogSource> = setOf(modyolo, happyMod, a1n)
}

/** Repository bindings. Each interface is bound to exactly one implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: AggregatedCatalogRepository): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: DefaultLibraryRepository): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(
        impl: DefaultSearchHistoryRepository,
    ): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DefaultDownloadRepository): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindInstallerRepository(impl: PackageInstallerRepository): InstallerRepository
}
