package com.raumanian.thirtysix.browser.core.di

import android.webkit.CookieManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object CookieManagerModule {
    /** Behind a `Provider`, so a test can swap in a cookie engine that never answers. */
    @Provides
    fun provideCookieManager(): CookieManager = CookieManager.getInstance()
}
