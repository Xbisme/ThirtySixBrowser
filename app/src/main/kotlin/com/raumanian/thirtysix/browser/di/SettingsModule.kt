package com.raumanian.thirtysix.browser.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.repository.SettingsRepositoryImpl
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

/**
 * Provides the application-scoped DataStore<Preferences> singleton (Spec 006).
 *
 * - Singleton REQUIRED by DataStore: creating two instances over the same file
 *   throws IllegalStateException at runtime.
 * - Scope = io dispatcher + SupervisorJob so one consumer's failure does not
 *   cancel the entire DataStore pipeline.
 * - File location resolved via Context.preferencesDataStoreFile(name) →
 *   <files-dir>/datastore/<name>.preferences_pb.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreProviderModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        dispatcherProvider: DispatcherProvider,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcherProvider.io + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(AppConstants.SETTINGS_DATASTORE_FILE_NAME) },
    )
}

/**
 * Binds SettingsRepository → SettingsRepositoryImpl. Two modules in one file is
 * idiomatic Hilt when grouping by feature surface (object provider + abstract
 * @Binds cannot share a class).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsRepositoryBindingModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository
}
