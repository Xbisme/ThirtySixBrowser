package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.data.repository.SitePermissionRepositoryImpl
import com.raumanian.thirtysix.browser.domain.repository.SitePermissionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Spec 021 — remembered site permission decisions (research R8). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SitePermissionModule {
    @Binds
    @Singleton
    abstract fun bindSitePermissionRepository(impl: SitePermissionRepositoryImpl): SitePermissionRepository
}
