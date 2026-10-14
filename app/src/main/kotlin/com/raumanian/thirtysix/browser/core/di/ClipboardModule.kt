package com.raumanian.thirtysix.browser.core.di

import com.raumanian.thirtysix.browser.core.data.datasource.local.AndroidClipboardWriter
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClipboardModule {

    @Binds
    @Singleton
    abstract fun bindClipboardWriter(impl: AndroidClipboardWriter): ClipboardWriter
}
