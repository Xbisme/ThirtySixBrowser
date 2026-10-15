package com.raumanian.thirtysix.browser.core.di

import com.raumanian.thirtysix.browser.core.data.datasource.local.cache.DiskFaviconCache
import com.raumanian.thirtysix.browser.core.data.datasource.local.cache.DiskScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Singletons so each cache directory and its in-memory `version` flow are shared per process. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FaviconCacheModule {

    @Binds
    @Singleton
    abstract fun bindFaviconCache(impl: DiskFaviconCache): FaviconCache

    @Binds
    @Singleton
    abstract fun bindScreenshotCache(impl: DiskScreenshotCache): ScreenshotCache
}
