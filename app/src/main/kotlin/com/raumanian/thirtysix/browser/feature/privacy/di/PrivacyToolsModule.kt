package com.raumanian.thirtysix.browser.feature.privacy.di

import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptBundles
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.util.ApplicationScope
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.CookieJarSnapshotManagerImpl
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.PrivacyScriptBundlesImpl
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.PrivacyScriptSessionImpl
import com.raumanian.thirtysix.browser.feature.privacy.data.repository.FireMarkerRepositoryImpl
import com.raumanian.thirtysix.browser.feature.privacy.data.repository.FireproofSiteRepositoryImpl
import com.raumanian.thirtysix.browser.feature.privacy.domain.repository.FireMarkerRepository
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.ConfigurableFireStepObserver
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.FireCoordinator
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.FireCoordinatorImpl
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.FireStepObserver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class PrivacyToolsModule {
    @Binds
    @Singleton
    abstract fun bindCookieJarSnapshotManager(impl: CookieJarSnapshotManagerImpl): CookieJarSnapshotManager

    @Binds
    @Singleton
    abstract fun bindFireproofSiteRepository(impl: FireproofSiteRepositoryImpl): FireproofSiteRepository

    @Binds
    abstract fun bindPrivacyScriptSession(impl: PrivacyScriptSessionImpl): PrivacyScriptSession

    @Binds
    @Singleton
    abstract fun bindPrivacyScriptBundles(impl: PrivacyScriptBundlesImpl): PrivacyScriptBundles

    @Binds
    abstract fun bindFireMarkerRepository(impl: FireMarkerRepositoryImpl): FireMarkerRepository

    @Binds
    abstract fun bindFireCoordinator(impl: FireCoordinatorImpl): FireCoordinator

    @Binds
    abstract fun bindFireStepObserver(impl: ConfigurableFireStepObserver): FireStepObserver

    companion object {
        /** Lives as long as the process; a failing child never cancels it. */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.default)
    }
}
