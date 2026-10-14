package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptBundles
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.util.ApplicationScope
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.privacy.PrivacyScriptBundlesImpl
import com.raumanian.thirtysix.browser.data.local.privacy.PrivacyScriptSessionImpl
import com.raumanian.thirtysix.browser.data.repository.FireMarkerRepositoryImpl
import com.raumanian.thirtysix.browser.data.repository.FireproofSiteRepositoryImpl
import com.raumanian.thirtysix.browser.domain.fire.ConfigurableFireStepObserver
import com.raumanian.thirtysix.browser.domain.fire.FireCoordinator
import com.raumanian.thirtysix.browser.domain.fire.FireCoordinatorImpl
import com.raumanian.thirtysix.browser.domain.fire.FireStepObserver
import com.raumanian.thirtysix.browser.domain.repository.FireMarkerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Spec 024 — fireproof sites, the Fire coordinator and the privacy-script session. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrivacyToolsModule {
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
        /** Spec 024 research R10 — lives as long as the process; a failing child never cancels it. */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.default)
    }
}
