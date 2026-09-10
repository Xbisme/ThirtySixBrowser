package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.data.local.clipboard.AndroidClipboardWriter
import com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 014 FR-021 — binds the clipboard seam used by the History screen's
 * "Copy URL" action. Sibling to [FaviconCacheModule]; one Hilt module per
 * related surface keeps the project's discoverability convention.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClipboardModule {

    @Binds
    @Singleton
    abstract fun bindClipboardWriter(impl: AndroidClipboardWriter): ClipboardWriter
}
