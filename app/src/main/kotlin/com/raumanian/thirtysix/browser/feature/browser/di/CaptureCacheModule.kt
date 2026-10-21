package com.raumanian.thirtysix.browser.feature.browser.di

import com.raumanian.thirtysix.browser.core.domain.repository.CaptureCache
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CaptureCacheImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureCacheModule {
    @Binds
    abstract fun bindCaptureCache(impl: CaptureCacheImpl): CaptureCache
}
