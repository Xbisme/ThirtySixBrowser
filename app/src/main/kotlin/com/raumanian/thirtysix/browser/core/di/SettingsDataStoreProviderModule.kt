package com.raumanian.thirtysix.browser.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * A singleton because two DataStores over the same file throw at runtime. The SupervisorJob
 * keeps one consumer's failure from cancelling the whole pipeline.
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
