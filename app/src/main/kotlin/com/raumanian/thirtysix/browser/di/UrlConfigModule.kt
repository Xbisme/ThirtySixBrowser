package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Spec 007 — provides the default home URL to BrowserViewModel.
 *
 * The indirection (vs. referencing [UrlConstants.DEFAULT_HOME_URL] directly inside
 * the ViewModel) exists so the instrumented test can swap the binding via
 * `@TestInstallIn` to drive the offline-error path deterministically without
 * disabling the emulator's network — see `androidTest/.../di/TestUrlConfigModule.kt`
 * (tasks T014a, T036a, T036).
 *
 * Spec 011 — promoted from `ViewModelComponent` → `SingletonComponent` so
 * the new singleton-scope `TabRepositoryImpl` (data layer) can inject the
 * same `@Named("default_home_url")` constant for empty-state seeding (R5).
 * The change is binding-compatible: ViewModel-scope consumers
 * (BrowserViewModel) still resolve the binding from the parent SingletonC
 * scope without any constructor change.
 */
@Module
@InstallIn(SingletonComponent::class)
object UrlConfigModule {
    @Provides
    @Singleton
    @Named("default_home_url")
    fun provideDefaultHomeUrl(): String = UrlConstants.DEFAULT_HOME_URL
}
