package com.raumanian.thirtysix.browser.core.di

import com.raumanian.thirtysix.browser.core.dispatcher.DefaultDispatcherProvider
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}
