package com.raumanian.thirtysix.browser.feature.settings.di

import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.feature.settings.data.repository.SearchEngineRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchEngineModule {

    @Binds
    @Singleton
    abstract fun bindSearchEngineRepository(
        impl: SearchEngineRepositoryImpl,
    ): SearchEngineRepository
}
