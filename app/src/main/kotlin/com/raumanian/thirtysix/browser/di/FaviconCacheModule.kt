package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.data.local.cache.DiskFaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.DiskScreenshotCache
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 011 favicon + screenshot amendments (2026-05-03) — binds the two
 * disk-backed cache interfaces to their concrete implementations.
 *
 * Sibling to [TabsModule]; one Hilt module per related-cache surface keeps
 * the project's discoverability convention. `@Singleton` so each cache
 * directory + the in-memory `version` StateFlow are shared per app process.
 */
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
