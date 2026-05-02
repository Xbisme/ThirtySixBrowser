package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.data.repository.SearchEngineRepositoryImpl
import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 010 — binds [SearchEngineRepository] → [SearchEngineRepositoryImpl].
 *
 * Sibling to [SettingsModule]; one module per feature surface keeps the
 * project's discoverability convention. Singleton scope matches
 * `SettingsRepository` so a single instance is reused per-app and avoids
 * per-ViewModel allocation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchEngineModule {

    @Binds
    @Singleton
    abstract fun bindSearchEngineRepository(
        impl: SearchEngineRepositoryImpl,
    ): SearchEngineRepository
}
