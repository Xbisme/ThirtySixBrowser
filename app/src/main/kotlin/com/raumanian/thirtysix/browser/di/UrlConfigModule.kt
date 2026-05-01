package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import javax.inject.Named

/**
 * Spec 007 — provides the default home URL to BrowserViewModel.
 *
 * The indirection (vs. referencing [UrlConstants.DEFAULT_HOME_URL] directly inside
 * the ViewModel) exists so the instrumented test can swap the binding via
 * `@TestInstallIn` to drive the offline-error path deterministically without
 * disabling the emulator's network — see `androidTest/.../di/TestUrlConfigModule.kt`
 * (tasks T014a, T036a, T036).
 */
@Module
@InstallIn(ViewModelComponent::class)
object UrlConfigModule {
    @Provides
    @Named("default_home_url")
    fun provideDefaultHomeUrl(): String = UrlConstants.DEFAULT_HOME_URL
}
