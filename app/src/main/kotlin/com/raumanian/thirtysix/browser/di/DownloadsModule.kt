package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.data.local.download.AndroidDownloadManagerGateway
import com.raumanian.thirtysix.browser.data.local.download.AndroidExternalFileOpener
import com.raumanian.thirtysix.browser.data.local.download.DownloadManagerGateway
import com.raumanian.thirtysix.browser.data.local.download.ExternalFileOpener
import com.raumanian.thirtysix.browser.data.repository.DownloadsRepositoryImpl
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
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
