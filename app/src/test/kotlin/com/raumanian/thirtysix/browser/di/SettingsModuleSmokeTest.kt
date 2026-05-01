package com.raumanian.thirtysix.browser.di

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.data.mapper.SettingsMapper
import com.raumanian.thirtysix.browser.data.repository.SettingsRepositoryImpl
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Structural smoke for [SettingsDataStoreProviderModule] (Spec 006 SC-010).
 *
 * Invokes the @Provides method directly with a real Application context + a
 * minimal DispatcherProvider. Wires SettingsDataStore + SettingsMapper +
 * SettingsRepositoryImpl manually and verifies one observe + setter round-trip.
 *
 * Full @HiltAndroidTest end-to-end graph boot is deferred to Spec 016 when the
 * first ViewModel consumer materializes (heavier scaffolding than the data
 * layer alone justifies — same rationale as Spec 005's DatabaseModuleSmokeTest).
 */
@RunWith(AndroidJUnit4::class)
class SettingsModuleSmokeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main get() = Dispatchers.Unconfined
        override val io get() = Dispatchers.Unconfined
        override val default get() = Dispatchers.Unconfined
        override val unconfined get() = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        // Provider creates a real DataStore file at <files>/datastore/<name>.preferences_pb;
        // delete so subsequent test classes start clean.
        val dataStoreFile = File(
            context.filesDir,
            "datastore/${AppConstants.SETTINGS_DATASTORE_FILE_NAME}.preferences_pb",
        )
        if (dataStoreFile.exists()) dataStoreFile.delete()
    }

    @Test
    fun `provider yields a usable DataStore that the repository can round-trip`() = runBlocking {
        val rawDataStore = SettingsDataStoreProviderModule.provideSettingsDataStore(
            context,
            dispatcherProvider,
        )
        assertNotNull(rawDataStore)

        val repository = SettingsRepositoryImpl(SettingsDataStore(rawDataStore), SettingsMapper())

        // observeSettings yields defaults on first emission
        val initial = repository.observeSettings().first()
        assertEquals(ThemeMode.System, initial.themeMode)

        // Setter round-trip
        val writeResult = repository.setThemeMode(ThemeMode.Dark)
        assertTrue("setter must succeed", writeResult is Result.Success)

        val updated = repository.observeSettings().first()
        assertEquals(ThemeMode.Dark, updated.themeMode)
    }
}
