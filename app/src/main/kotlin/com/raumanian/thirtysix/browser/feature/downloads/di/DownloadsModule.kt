package com.raumanian.thirtysix.browser.feature.downloads.di

import com.raumanian.thirtysix.browser.core.domain.repository.DownloadManagerGateway
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.ExternalFileOpener
import com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local.AndroidDownloadManagerGateway
import com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local.AndroidExternalFileOpener
import com.raumanian.thirtysix.browser.feature.downloads.data.repository.DownloadsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 015 — binds the two halves of the hybrid source of truth.
 *
 * [DownloadsRepository] owns the persisted half; [DownloadManagerGateway] owns the live
 * half. They are deliberately separate bindings joined only at the use-case layer, so no
 * repository ever depends on an Android system service (Constitution §IV).
 *
 * `AppDatabase` and `DownloadRecordDao` come from the Spec 005 `DatabaseModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadsModule {

    @Binds
    @Singleton
    abstract fun bindDownloadsRepository(impl: DownloadsRepositoryImpl): DownloadsRepository

    @Binds
    @Singleton
    abstract fun bindDownloadManagerGateway(impl: AndroidDownloadManagerGateway): DownloadManagerGateway

    @Binds
    @Singleton
    abstract fun bindExternalFileOpener(impl: AndroidExternalFileOpener): ExternalFileOpener
}
