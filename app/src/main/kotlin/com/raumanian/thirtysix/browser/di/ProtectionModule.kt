package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.core.domain.repository.HttpsUpgradeFailures
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoProtectionExceptions
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionDataRepository
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.data.local.protection.HttpsUpgradeFailureStore
import com.raumanian.thirtysix.browser.data.local.protection.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.data.repository.ProtectionDataRepositoryImpl
import com.raumanian.thirtysix.browser.data.repository.ProtectionExceptionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Spec 023 — privacy protection data, exceptions and in-memory stores. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProtectionModule {
    @Binds
    @Singleton
    abstract fun bindProtectionDataRepository(impl: ProtectionDataRepositoryImpl): ProtectionDataRepository

    @Binds
    @Singleton
    abstract fun bindProtectionExceptionRepository(
        impl: ProtectionExceptionRepositoryImpl,
    ): ProtectionExceptionRepository

    @Binds
    abstract fun bindIncognitoProtectionExceptions(
        impl: IncognitoProtectionExceptionStore,
    ): IncognitoProtectionExceptions

    @Binds
    abstract fun bindHttpsUpgradeFailures(impl: HttpsUpgradeFailureStore): HttpsUpgradeFailures
}
