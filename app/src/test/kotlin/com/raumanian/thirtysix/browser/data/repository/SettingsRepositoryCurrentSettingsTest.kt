package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.data.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.data.local.datastore.createTestSettingsDataStore
import com.raumanian.thirtysix.browser.data.mapper.SettingsMapper
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.usecase.GetUserSettingsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Spec 018 T007 — the one-shot settings read, and the use case over it.
 *
 * This read exists so the start-up decision can be made before the navigation graph is built.
 * The distinction these tests pin is the one the whole feature hinges on: `currentSettings()`
 * returns what is actually PERSISTED, never `UserSettings.DEFAULT` as a placeholder. Returning
 * the defaults on a fresh install is correct — there, the defaults ARE the persisted truth.
 */
class SettingsRepositoryCurrentSettingsTest {

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
        producerScope.cancel()
    }

    @Test
    fun `fresh install returns the documented defaults`() = runTest {
        val settings = repository.currentSettings()

        // On a fresh install the defaults are not a placeholder — they are the truth.
        assertEquals(AppDefaults.THEME_MODE, settings.themeMode)
        assertEquals(AppDefaults.SEARCH_ENGINE, settings.searchEngine)
        assertEquals(AppDefaults.IS_ONBOARDING_COMPLETED, settings.isOnboardingCompleted)
        assertFalse("onboarding must start incomplete", settings.isOnboardingCompleted)
    }

    @Test
    fun `persisted values are returned instead of the defaults`() = runTest {
        repository.setThemeMode(ThemeMode.Dark)
        repository.setSearchEngine(SearchEngine.DuckDuckGo)
        repository.setOnboardingCompleted(true)

        val settings = repository.currentSettings()

        assertEquals(ThemeMode.Dark, settings.themeMode)
        assertEquals(SearchEngine.DuckDuckGo, settings.searchEngine)
        assertTrue(settings.isOnboardingCompleted)
    }

    @Test
    fun `the completed flag survives being read twice`() = runTest {
        repository.setOnboardingCompleted(true)

        // A returning user's launch must not depend on read ordering or on a cached first
        // emission: every read reports the persisted value.
        assertTrue(repository.currentSettings().isOnboardingCompleted)
        assertTrue(repository.currentSettings().isOnboardingCompleted)
    }

    @Test
    fun `use case returns what the repository returns`() = runTest {
        val getUserSettings = GetUserSettingsUseCase(repository)

        assertFalse(getUserSettings().isOnboardingCompleted)
        repository.setOnboardingCompleted(true)
        assertTrue(getUserSettings().isOnboardingCompleted)
    }
}
