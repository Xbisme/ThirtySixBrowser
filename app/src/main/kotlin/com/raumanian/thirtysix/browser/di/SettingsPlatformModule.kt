package com.raumanian.thirtysix.browser.di

import android.os.Build
import com.raumanian.thirtysix.browser.BuildConfig
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.data.local.locale.AppCompatAppLanguageController
import com.raumanian.thirtysix.browser.data.local.webdata.AndroidWebDataCleaner
import com.raumanian.thirtysix.browser.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.domain.repository.WebDataCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Spec 016 — platform facts the Settings screen needs, supplied as plain values.
 *
 * Injected rather than read inside `SettingsViewModel` so the view model stays free of
 * `Build` and `BuildConfig` and its tests can exercise both dynamic-color branches on the JVM
 * (research.md R9, R10).
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsPlatformModule {

    /** FR-010 — Material dynamic color exists only on Android 12 (API 31) and later. */
    @Provides
    @Named(AppConstants.QUALIFIER_SUPPORTS_DYNAMIC_COLOR)
    fun provideSupportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** FR-034 — the installed version, as the platform reports it for this package. */
    @Provides
    @Named(AppConstants.QUALIFIER_APP_VERSION_NAME)
    fun provideAppVersionName(): String = BuildConfig.VERSION_NAME
}

/**
 * Spec 016 — `@Binds` for the two platform seams the Settings screen drives: the app-language
 * controller (US3) and the web-data cleaner (US4).
 *
 * The object provider and the abstract binder cannot share a class, hence two modules in one
 * file — the layout `SettingsModule.kt` already uses. Each story adds its own binding here so
 * neither depends on the other.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsPlatformBindingModule {

    /** US3 — FR-016, FR-042. */
    @Binds
    @Singleton
    abstract fun bindAppLanguageController(
        impl: AppCompatAppLanguageController,
    ): AppLanguageController

    /** US4 — FR-028, FR-029, FR-042. */
    @Binds
    @Singleton
    abstract fun bindWebDataCleaner(
        impl: AndroidWebDataCleaner,
    ): WebDataCleaner
}
