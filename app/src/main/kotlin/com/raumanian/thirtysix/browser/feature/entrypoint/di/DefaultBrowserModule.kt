package com.raumanian.thirtysix.browser.feature.entrypoint.di

import com.raumanian.thirtysix.browser.core.data.util.SystemMonotonicClock
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.util.MonotonicClock
import com.raumanian.thirtysix.browser.feature.entrypoint.data.datasource.local.AndroidDefaultBrowserController
import com.raumanian.thirtysix.browser.feature.entrypoint.data.repository.DefaultBrowserInviteRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// The relays and the external-tab registry are `@Singleton @Inject constructor` classes and need no binding.
@Module
@InstallIn(SingletonComponent::class)
abstract class DefaultBrowserModule {

    /** The duplicate-link window needs a clock that ignores wall-clock changes. */
    @Binds
    @Singleton
    abstract fun bindMonotonicClock(impl: SystemMonotonicClock): MonotonicClock

    @Binds
    @Singleton
    abstract fun bindDefaultBrowserController(impl: AndroidDefaultBrowserController): DefaultBrowserController

    @Binds
    @Singleton
    abstract fun bindDefaultBrowserInviteRepository(
        impl: DefaultBrowserInviteRepositoryImpl,
    ): DefaultBrowserInviteRepository
}
