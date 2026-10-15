package com.raumanian.thirtysix.browser.feature.sitepermission.di

import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.feature.sitepermission.data.repository.SitePermissionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SitePermissionModule {
    @Binds
    @Singleton
    abstract fun bindSitePermissionRepository(impl: SitePermissionRepositoryImpl): SitePermissionRepository
}
