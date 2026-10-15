package com.raumanian.thirtysix.browser.feature.browser.di

import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class BrowserPageModule {
    // Every holder of the browser screen shares one instance, which survives configuration changes as they do.
    @Binds
    @ActivityRetainedScoped
    abstract fun bindCurrentPageRepository(impl: CurrentPageRepositoryImpl): CurrentPageRepository
}
