package com.raumanian.thirtysix.browser.feature.settings.di

import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.feature.settings.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.plus

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsRepositoryBindingModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository
}
