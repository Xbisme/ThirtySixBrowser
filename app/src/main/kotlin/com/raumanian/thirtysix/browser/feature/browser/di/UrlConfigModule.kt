package com.raumanian.thirtysix.browser.feature.browser.di

import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Provides the default home address. A binding rather than a direct constant so instrumented
 * tests can swap it, and singleton-scoped so the tab repository can seed empty state with it.
 * New tabs open on the reserved Home address.
 */
@Module
@InstallIn(SingletonComponent::class)
object UrlConfigModule {
    @Provides
    @Singleton
    @Named("default_home_url")
    fun provideDefaultHomeUrl(): String = UrlConstants.HOME_PAGE_URL
}
