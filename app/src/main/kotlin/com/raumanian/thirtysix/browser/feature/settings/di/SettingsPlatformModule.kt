package com.raumanian.thirtysix.browser.feature.settings.di

import android.os.Build
import com.raumanian.thirtysix.browser.BuildConfig
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.core.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.feature.settings.data.datasource.local.AndroidWebDataCleaner
import com.raumanian.thirtysix.browser.feature.settings.data.datasource.local.AppCompatAppLanguageController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

// Injected as plain values so SettingsViewModel's tests can exercise both dynamic-color branches on the JVM.
@Module
@InstallIn(SingletonComponent::class)
object SettingsPlatformModule {

    @Provides
    @Named(AppConstants.QUALIFIER_SUPPORTS_DYNAMIC_COLOR)
    fun provideSupportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @Provides
    @Named(AppConstants.QUALIFIER_APP_VERSION_NAME)
    fun provideAppVersionName(): String = BuildConfig.VERSION_NAME
}

// An object provider and an abstract binder cannot share a class, hence two modules in one file.
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsPlatformBindingModule {

    @Binds
    @Singleton
    abstract fun bindAppLanguageController(
        impl: AppCompatAppLanguageController,
    ): AppLanguageController

    @Binds
    @Singleton
    abstract fun bindWebDataCleaner(
        impl: AndroidWebDataCleaner,
    ): WebDataCleaner
}
