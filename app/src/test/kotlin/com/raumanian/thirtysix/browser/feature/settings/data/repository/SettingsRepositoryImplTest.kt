package com.raumanian.thirtysix.browser.feature.settings.data.repository

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.createTestSettingsDataStore
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import com.raumanian.thirtysix.browser.feature.settings.data.mapper.SettingsMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A write reaches every subscriber within one observation cycle. */
class SettingsRepositoryImplTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var producerScope: CoroutineScope
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setup() {
        producerScope = CoroutineScope(SupervisorJob())
        val ds = SettingsDataStore(
            createTestSettingsDataStore(tempFolder.newFolder(), scope = producerScope),
        )
        repository = SettingsRepositoryImpl(ds, SettingsMapper())
    }

    @After
    fun tearDown() {
        producerScope.cancel("test teardown")
    }

    @Test
    fun observeSettings_emitsDefaults_then_emitsAfterWrite() = runTest {
        repository.observeSettings().test {
            val first = awaitItem()
            // First emission equals the documented defaults.
            assertEquals_appDefaults(first)

            repository.setThemeMode(ThemeMode.Dark)

            val updated = awaitItem()
            assertEquals(ThemeMode.Dark, updated.themeMode)
            // Other fields unchanged
            assertEquals(AppDefaults.DYNAMIC_COLOR_ENABLED, updated.isDynamicColorEnabled)
            assertEquals(AppDefaults.SEARCH_ENGINE, updated.searchEngine)
            assertEquals(AppDefaults.HISTORY_RETENTION, updated.historyRetention)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun observeSettings_distinctUntilChanged_collapsesDuplicateWrites() = runTest {
        repository.observeSettings().test {
            awaitItem() // defaults

            // Write the SAME value twice — distinctUntilChanged should suppress the second emission
            repository.setThemeMode(ThemeMode.Dark)
            val first = awaitItem()
            assertEquals(ThemeMode.Dark, first.themeMode)

            repository.setThemeMode(ThemeMode.Dark)
            // No further emission expected
            expectNoEvents()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun dynamicColorAndRetentionWrites_areObserved() = runTest {
        repository.observeSettings().test {
            awaitItem() // defaults

            repository.setDynamicColorEnabled(true)
            assertEquals(true, awaitItem().isDynamicColorEnabled)

            repository.setHistoryRetention(HistoryRetention.Days180)
            val updated = awaitItem()
            assertEquals(HistoryRetention.Days180, updated.historyRetention)
            assertEquals(true, updated.isDynamicColorEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    private fun assertEquals_appDefaults(snapshot: UserSettings) {
        assertEquals(UserSettings.DEFAULT, snapshot)
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
