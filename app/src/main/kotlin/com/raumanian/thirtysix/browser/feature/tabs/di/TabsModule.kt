package com.raumanian.thirtysix.browser.feature.tabs.di

import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.TabRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Singleton so the impl's seeding mutex is shared across collectors. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TabsModule {

    @Binds
    @Singleton
    abstract fun bindTabRepository(impl: TabRepositoryImpl): TabRepository
}
