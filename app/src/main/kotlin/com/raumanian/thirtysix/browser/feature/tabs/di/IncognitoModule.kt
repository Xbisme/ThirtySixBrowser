package com.raumanian.thirtysix.browser.feature.tabs.di

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.IncognitoTabRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Singletons, so incognito state survives ViewModel recreation; process death wipes it, so no
 * incognito tab is restored after a cold start.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IncognitoModule {

    @Binds
    @Singleton
    abstract fun bindIncognitoTabRepository(
        impl: IncognitoTabRepositoryImpl,
    ): IncognitoTabRepository
}
