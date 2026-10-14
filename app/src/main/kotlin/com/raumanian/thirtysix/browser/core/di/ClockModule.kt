package com.raumanian.thirtysix.browser.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Spec 019 (research R14) — the wall clock, injected so the Home greeting can be tested at
 * any time of day.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
