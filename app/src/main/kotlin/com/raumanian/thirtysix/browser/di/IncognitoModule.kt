package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoWebProfile
import com.raumanian.thirtysix.browser.data.local.cookies.CookieJarSnapshotManagerImpl
import com.raumanian.thirtysix.browser.data.repository.IncognitoTabRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 012 — binds the incognito tab + cookie-snapshot infrastructure.
 *
 * Both bindings are `@Singleton` so a single in-memory state survives
 * ViewModel recreation across configuration changes / process backstack
 * pops. Process death wipes the singleton, which is exactly the FR-012
 * contract — no incognito tab restored after cold start.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IncognitoModule {

    @Binds
    @Singleton
    abstract fun bindIncognitoTabRepository(
        impl: IncognitoTabRepositoryImpl,
    ): IncognitoTabRepository

    @Binds
    @Singleton
    abstract fun bindCookieJarSnapshotManager(
        impl: CookieJarSnapshotManagerImpl,
    ): CookieJarSnapshotManager

    companion object {
        /** The incognito session's WebView profile; one process-wide object (see [IncognitoProfiles]). */
        @Provides
        fun provideIncognitoWebProfile(): IncognitoWebProfile = IncognitoProfiles
    }
}
