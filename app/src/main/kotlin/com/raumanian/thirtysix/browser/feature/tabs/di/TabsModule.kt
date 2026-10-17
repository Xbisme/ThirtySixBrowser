package com.raumanian.thirtysix.browser.feature.tabs.di

import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabSessionRepository
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.TabRepositoryImpl
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.TabSessionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Singletons: the tab seeding mutex and the in-memory incognito sessions must be shared. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TabsModule {

    @Binds
    @Singleton
    abstract fun bindTabRepository(impl: TabRepositoryImpl): TabRepository

    @Binds
    @Singleton
    abstract fun bindTabSessionRepository(impl: TabSessionRepositoryImpl): TabSessionRepository
}
