package com.raumanian.thirtysix.browser.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.mapper.SettingsMapper
import com.raumanian.thirtysix.browser.data.repository.SettingsRepositoryImpl
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 T018 — DataStore read/write on a real device.
 *
 * Constitution §VI: "Instrumented tests MUST cover: Room DAOs, DataStore read/write, WebView
 * basic load". Every other settings test runs on the JVM, so this is the one that proves the
 * two Spec 016 keys persist and decode through the production repository on Android itself.
 *
 * Each test owns a throw-away preferences file in the instrumentation context's cache
 * directory, so tests never touch the app's real `thirtysix_settings` file or each other.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDataStoreInstrumentedTest {

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = File(context.cacheDir, "settings-instrumented-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repository = SettingsRepositoryImpl(SettingsDataStore(dataStore), SettingsMapper())
    }

    @After
    fun tearDown() {
        scope.cancel("test teardown")
        file.delete()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun missingFile_yieldsDocumentedDefaults() = runBlocking {
        assertEquals(UserSettings.DEFAULT, repository.observeSettings().first())
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun dynamicColor_roundTripsBothWays() = runBlocking {
        assertTrue(repository.setDynamicColorEnabled(false) is Result.Success)
        assertEquals(false, repository.observeSettings().first().isDynamicColorEnabled)

        assertTrue(repository.setDynamicColorEnabled(true) is Result.Success)
        assertEquals(true, repository.observeSettings().first().isDynamicColorEnabled)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun historyRetention_roundTrips() = runBlocking {
        assertTrue(repository.setHistoryRetention(HistoryRetention.Days7) is Result.Success)
        assertEquals(HistoryRetention.Days7, repository.observeSettings().first().historyRetention)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun outOfSetRetentionOnDisk_decodesToNinetyDays() = runBlocking {
        dataStore.edit { it[StorageKeys.HISTORY_RETENTION_DAYS] = OUT_OF_SET_RETENTION_DAYS }
        assertEquals(HistoryRetention.Days90, repository.observeSettings().first().historyRetention)
    }

    private companion object {
        const val TEST_TIMEOUT_MS: Long = 15_000L

        /** A day count that is not one of the four Spec 016 windows. */
        const val OUT_OF_SET_RETENTION_DAYS: Int = 45
    }
}
