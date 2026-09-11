package com.raumanian.thirtysix.browser.presentation.settings

import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.testdoubles.FakeAppLanguageController
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeSettingsRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeWebDataCleaner
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 016 — [SettingsViewModel] behaviour. Each user story extends this class; every case
 * builds its view model through [settingsViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── Foundational (T030) ────────

    @Test
    fun `initial state mirrors the documented defaults`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(UserSettings.DEFAULT.themeMode, state.themeMode)
        assertEquals(UserSettings.DEFAULT.isDynamicColorEnabled, state.isDynamicColorEnabled)
        assertEquals(UserSettings.DEFAULT.searchEngine, state.searchEngine)
        assertEquals(UserSettings.DEFAULT.historyRetention, state.historyRetention)
        assertNull(state.dialog)
    }

    @Test
    fun `state follows repository emissions`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()

        repository.setThemeMode(ThemeMode.Dark)
        repository.setDynamicColorEnabled(false)
        repository.setSearchEngine(SearchEngine.Bing)
        repository.setHistoryRetention(HistoryRetention.Days7)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ThemeMode.Dark, state.themeMode)
        assertFalse(state.isDynamicColorEnabled)
        assertEquals(SearchEngine.Bing, state.searchEngine)
        assertEquals(HistoryRetention.Days7, state.historyRetention)
    }

    @Test
    fun `injected platform values reach the state`() = runTest {
        val supported = settingsViewModel(supportsDynamicColor = true, appVersionName = "1.2.3")
        val unsupported = settingsViewModel(supportsDynamicColor = false)
        advanceUntilIdle()

        assertTrue(supported.uiState.value.isDynamicColorSupported)
        assertEquals("1.2.3", supported.uiState.value.appVersionName)
        assertFalse(unsupported.uiState.value.isDynamicColorSupported)
        assertEquals(TEST_APP_VERSION_NAME, unsupported.uiState.value.appVersionName)
    }

    @Test
    fun `dismissDialog closes an open dialog`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()
        viewModel.onThemeRowClick()

        viewModel.dismissDialog()

        assertNull(viewModel.uiState.value.dialog)
    }

    // ──────── US1 — theme (T036) ────────

    @Test
    fun `theme row opens the theme chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onThemeRowClick()

        assertEquals(SettingsDialog.ThemeChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting a different theme writes once and closes the chooser`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onThemeRowClick()

        viewModel.onThemeSelected(ThemeMode.Dark)
        advanceUntilIdle()

        assertEquals(listOf(FakeSettingsRepository.CALL_SET_THEME_MODE), repository.writes)
        assertEquals(ThemeMode.Dark, viewModel.uiState.value.themeMode)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting the current theme writes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onThemeRowClick()

        viewModel.onThemeSelected(UserSettings.DEFAULT.themeMode)
        advanceUntilIdle()

        assertTrue("FR-006", repository.writes.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a failed theme write emits SettingNotSaved and keeps the old theme`() = runTest {
        val repository = FakeSettingsRepository().apply { failNextWrite = true }
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onThemeSelected(ThemeMode.Dark)
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.SettingNotSaved), events)
        assertEquals(UserSettings.DEFAULT.themeMode, viewModel.uiState.value.themeMode)
    }

    // ──────── US2 — search engine (T043) ────────

    @Test
    fun `search engine row opens the search engine chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onSearchEngineRowClick()

        assertEquals(SettingsDialog.SearchEngineChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting a different engine writes once and closes the chooser`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onSearchEngineRowClick()

        viewModel.onSearchEngineSelected(SearchEngine.DuckDuckGo)
        advanceUntilIdle()

        assertEquals(listOf(FakeSettingsRepository.CALL_SET_SEARCH_ENGINE), repository.writes)
        assertEquals(SearchEngine.DuckDuckGo, viewModel.uiState.value.searchEngine)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting the current engine writes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onSearchEngineRowClick()

        viewModel.onSearchEngineSelected(UserSettings.DEFAULT.searchEngine)
        advanceUntilIdle()

        assertTrue("FR-006", repository.writes.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a failed engine write emits SettingNotSaved and keeps the old engine`() = runTest {
        val repository = FakeSettingsRepository().apply { failNextWrite = true }
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onSearchEngineSelected(SearchEngine.Bing)
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.SettingNotSaved), events)
        assertEquals(UserSettings.DEFAULT.searchEngine, viewModel.uiState.value.searchEngine)
    }

    // ──────── US3 — app language (T060) ────────

    @Test
    fun `the platform language is read when the view model is created`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.Korean)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()

        assertEquals(AppLanguage.Korean, viewModel.uiState.value.appLanguage)
    }

    @Test
    fun `onScreenStarted refreshes the language from the platform`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.FollowSystem)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()

        // Research R12 — e.g. changed from Android 13+ system settings while the app was away.
        controller.currentLanguage = AppLanguage.Japanese
        viewModel.onScreenStarted()

        assertEquals(AppLanguage.Japanese, viewModel.uiState.value.appLanguage)
    }

    @Test
    fun `language row opens the language chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onLanguageRowClick()

        assertEquals(SettingsDialog.LanguageChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting the current language makes no platform call`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.German)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()
        viewModel.onLanguageRowClick()

        viewModel.onLanguageSelected(AppLanguage.German)
        advanceUntilIdle()

        assertTrue("FR-006", controller.applied.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting another language applies it once and shows it`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.FollowSystem)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()
        viewModel.onLanguageRowClick()

        viewModel.onLanguageSelected(AppLanguage.French)
        advanceUntilIdle()

        assertEquals(listOf(AppLanguage.French), controller.applied)
        assertEquals(AppLanguage.French, viewModel.uiState.value.appLanguage)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a rejected language change emits LanguageNotApplied and keeps the old language`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.English, applyResult = false)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onLanguageSelected(AppLanguage.Russian)
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.LanguageNotApplied), events)
        assertEquals(AppLanguage.English, viewModel.uiState.value.appLanguage)
    }

    // ──────── US4 — clear browsing data (T076) ────────

    @Test
    fun `the clear dialog opens with all three categories selected`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onClearBrowsingDataRowClick()

        assertEquals(
            SettingsDialog.ClearBrowsingData(selection = ClearBrowsingDataCategory.entries.toSet(), inProgress = false),
            viewModel.uiState.value.dialog,
        )
    }

    @Test
    fun `toggling a category removes it and adds it back`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()
        viewModel.onClearBrowsingDataRowClick()

        viewModel.onClearCategoryToggled(ClearBrowsingDataCategory.History)
        assertEquals(
            setOf(ClearBrowsingDataCategory.CookiesAndSiteData, ClearBrowsingDataCategory.CachedImagesAndFiles),
            clearDialog(viewModel).selection,
        )

        viewModel.onClearCategoryToggled(ClearBrowsingDataCategory.History)
        assertEquals(ClearBrowsingDataCategory.entries.toSet(), clearDialog(viewModel).selection)
    }

    @Test
    fun `confirming with nothing selected clears nothing`() = runTest {
        val log = mutableListOf<String>()
        val viewModel = settingsViewModel(
            historyRepository = FakeHistoryRepository(log),
            webDataCleaner = FakeWebDataCleaner(log),
        )
        advanceUntilIdle()
        viewModel.onClearBrowsingDataRowClick()
        ClearBrowsingDataCategory.entries.forEach(viewModel::onClearCategoryToggled)

        viewModel.onClearBrowsingDataConfirmed()
        advanceUntilIdle()

        assertTrue("FR-026", log.isEmpty())
        assertFalse(clearDialog(viewModel).inProgress)
    }

    @Test
    fun `a running clear ignores a second confirm, a dismiss and a toggle`() = runTest {
        val log = mutableListOf<String>()
        val web = FakeWebDataCleaner(log).apply { siteDataGate = CompletableDeferred() }
        val viewModel = settingsViewModel(historyRepository = FakeHistoryRepository(log), webDataCleaner = web)
        advanceUntilIdle()
        viewModel.onClearBrowsingDataRowClick()

        viewModel.onClearBrowsingDataConfirmed()
        advanceUntilIdle()
        viewModel.onClearBrowsingDataConfirmed()
        viewModel.onClearBrowsingDataDismissed()
        viewModel.onClearCategoryToggled(ClearBrowsingDataCategory.History)
        advanceUntilIdle()

        assertTrue("FR-032", clearDialog(viewModel).inProgress)
        assertEquals(ClearBrowsingDataCategory.entries.toSet(), clearDialog(viewModel).selection)
        assertEquals(1, log.count { it == FakeWebDataCleaner.CALL_CLEAR_SITE_DATA })

        web.siteDataGate?.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a successful clear closes the dialog and emits BrowsingDataCleared`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()
        val events = collectEvents(viewModel)
        viewModel.onClearBrowsingDataRowClick()

        viewModel.onClearBrowsingDataConfirmed()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(listOf(SettingsEvent.BrowsingDataCleared), events)
    }

    @Test
    fun `a clear with a failed category emits BrowsingDataPartiallyCleared`() = runTest {
        val viewModel = settingsViewModel(
            webDataCleaner = FakeWebDataCleaner(siteDataOutcome = SiteDataClearOutcome.Failed),
        )
        advanceUntilIdle()
        val events = collectEvents(viewModel)
        viewModel.onClearBrowsingDataRowClick()

        viewModel.onClearBrowsingDataConfirmed()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(listOf(SettingsEvent.BrowsingDataPartiallyCleared), events)
    }

    @Test
    fun `dismissing an idle clear dialog closes it`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()
        viewModel.onClearBrowsingDataRowClick()

        viewModel.onClearBrowsingDataDismissed()

        assertNull(viewModel.uiState.value.dialog)
    }

    // ──────── US5 — history retention (T088) ────────

    @Test
    fun `retention row opens the retention chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onRetentionRowClick()

        assertEquals(SettingsDialog.RetentionChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `a shorter window asks for confirmation before changing anything`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onRetentionRowClick()

        viewModel.onRetentionSelected(HistoryRetention.Days30)
        advanceUntilIdle()

        assertEquals(SettingsDialog.ConfirmShortenRetention(HistoryRetention.Days30), viewModel.uiState.value.dialog)
        assertTrue("FR-021", repository.writes.isEmpty())
    }

    @Test
    fun `a longer window applies at once without confirmation`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onRetentionRowClick()

        viewModel.onRetentionSelected(HistoryRetention.Days180)
        advanceUntilIdle()

        assertNull("FR-023", viewModel.uiState.value.dialog)
        assertEquals(listOf(FakeSettingsRepository.CALL_SET_HISTORY_RETENTION), repository.writes)
        assertEquals(HistoryRetention.Days180, viewModel.uiState.value.historyRetention)
    }

    @Test
    fun `choosing the current window changes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onRetentionRowClick()

        viewModel.onRetentionSelected(UserSettings.DEFAULT.historyRetention)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertTrue("FR-006", repository.writes.isEmpty())
    }

    @Test
    fun `confirming a shorter window saves it and deletes older history`() = runTest {
        val history = FakeHistoryRepository()
        history.recordVisit("https://old.example.com/", "Old", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60))
        val viewModel = settingsViewModel(historyRepository = history)
        advanceUntilIdle()
        viewModel.onRetentionSelected(HistoryRetention.Days30)

        viewModel.onShortenRetentionConfirmed()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(HistoryRetention.Days30, viewModel.uiState.value.historyRetention)
        assertTrue("FR-022", history.recorded.isEmpty())
    }

    @Test
    fun `cancelling the warning changes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val history = FakeHistoryRepository()
        history.recordVisit("https://old.example.com/", "Old", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60))
        val viewModel = settingsViewModel(settingsRepository = repository, historyRepository = history)
        advanceUntilIdle()
        viewModel.onRetentionSelected(HistoryRetention.Days7)

        viewModel.onShortenRetentionCancelled()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertTrue(repository.writes.isEmpty())
        assertEquals(1, history.recorded.size)
        assertEquals(UserSettings.DEFAULT.historyRetention, viewModel.uiState.value.historyRetention)
    }

    @Test
    fun `a failed retention write emits SettingNotSaved`() = runTest {
        val repository = FakeSettingsRepository().apply { failNextWrite = true }
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onRetentionSelected(HistoryRetention.Days180)
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.SettingNotSaved), events)
        assertEquals(UserSettings.DEFAULT.historyRetention, viewModel.uiState.value.historyRetention)
    }

    @Test
    fun `a failed prune emits RetentionPruneDeferred`() = runTest {
        val history = FakeHistoryRepository().apply { pruneError = IOException("disk") }
        val viewModel = settingsViewModel(historyRepository = history)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onRetentionSelected(HistoryRetention.Days180)
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.RetentionPruneDeferred), events)
        assertEquals(HistoryRetention.Days180, viewModel.uiState.value.historyRetention)
    }

    // ──────── US6 — dynamic color (T096) ────────

    @Test
    fun `turning dynamic color off writes once`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()

        viewModel.onDynamicColorToggled(false)
        advanceUntilIdle()

        assertEquals(listOf(FakeSettingsRepository.CALL_SET_DYNAMIC_COLOR), repository.writes)
        assertFalse(viewModel.uiState.value.isDynamicColorEnabled)
    }

    @Test
    fun `toggling dynamic color to its current value writes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()

        viewModel.onDynamicColorToggled(UserSettings.DEFAULT.isDynamicColorEnabled)
        advanceUntilIdle()

        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a failed dynamic color write emits SettingNotSaved`() = runTest {
        val repository = FakeSettingsRepository().apply { failNextWrite = true }
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onDynamicColorToggled(false)
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.SettingNotSaved), events)
        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)
    }

    private fun clearDialog(viewModel: SettingsViewModel): SettingsDialog.ClearBrowsingData =
        viewModel.uiState.value.dialog as SettingsDialog.ClearBrowsingData

    /** Subscribes before anything is emitted — the event channel has no replay. */
    private fun TestScope.collectEvents(viewModel: SettingsViewModel): List<SettingsEvent> {
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }
        return events
    }
}
