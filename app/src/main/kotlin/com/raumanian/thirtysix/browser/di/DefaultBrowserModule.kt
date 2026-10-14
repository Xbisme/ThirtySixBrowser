package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.core.data.util.SystemMonotonicClock
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.util.MonotonicClock
import com.raumanian.thirtysix.browser.data.platform.AndroidDefaultBrowserController
import com.raumanian.thirtysix.browser.data.repository.DefaultBrowserInviteRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 020 — bindings for the default-browser and entry-point feature. The relays and the
 * external-tab registry are `@Singleton @Inject constructor` classes and need no binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DefaultBrowserModule {

    /** Research R6 — the duplicate-link window (FR-007) needs a clock that ignores wall-clock changes. */
    @Binds
    @Singleton
    abstract fun bindMonotonicClock(impl: SystemMonotonicClock): MonotonicClock

    /** Research R8 — the system's default-browser state (FR-020, FR-022). */
    @Binds
    @Singleton
    abstract fun bindDefaultBrowserController(impl: AndroidDefaultBrowserController): DefaultBrowserController

    /** Research R9 — the Home invitation's dismissed flag and launch count. */
    @Binds
    @Singleton
    abstract fun bindDefaultBrowserInviteRepository(
        impl: DefaultBrowserInviteRepositoryImpl,
    ): DefaultBrowserInviteRepository
}
