package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.data.repository.HistoryRepositoryImpl
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 014 — binds [HistoryRepository] to [HistoryRepositoryImpl] at the application graph.
 * `AppDatabase` and `HistoryDao` are already provided by the Spec 005 `DatabaseModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository
}
