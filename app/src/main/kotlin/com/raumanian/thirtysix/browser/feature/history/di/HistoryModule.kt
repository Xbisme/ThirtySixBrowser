package com.raumanian.thirtysix.browser.feature.history.di

import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.feature.history.data.repository.HistoryRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository
}
