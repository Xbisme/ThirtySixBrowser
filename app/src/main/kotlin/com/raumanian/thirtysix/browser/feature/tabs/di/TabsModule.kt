package com.raumanian.thirtysix.browser.feature.tabs.di

import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.TabRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 011 — binds [TabRepository] → [TabRepositoryImpl].
 *
 * Sibling to [SettingsModule] (Spec 006) and [SearchEngineModule] (Spec 010);
 * one module per feature surface keeps the project's discoverability
 * convention. Singleton scope matches the other repository bindings — a
 * single instance is reused per-app and the internal seeding [Mutex] inside
 * the impl is shared across collectors.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TabsModule {

    @Binds
    @Singleton
    abstract fun bindTabRepository(impl: TabRepositoryImpl): TabRepository
}
