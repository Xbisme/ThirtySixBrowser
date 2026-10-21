package com.raumanian.thirtysix.browser.core.di

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// The real application would open the settings store first.
@Config(application = Application::class)
class SettingsDataStoreCorruptionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a damaged settings file is replaced with defaults`() = runTest {
        context.preferencesDataStoreFile(AppConstants.SETTINGS_DATASTORE_FILE_NAME).apply {
            parentFile?.mkdirs()
            writeBytes(GARBAGE)
        }
        val store = SettingsDataStoreProviderModule.provideSettingsDataStore(context, TestDispatchers)

        assertTrue(store.data.first().asMap().isEmpty())
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    private companion object {
        val GARBAGE = byteArrayOf(0x7F, 0x00, 0x13, 0x37, 0x42)
    }
}
