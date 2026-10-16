package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeAppLanguageController
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSettingsRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeWebDataCleaner
import com.raumanian.thirtysix.browser.feature.settings.domain.model.ClearBrowsingDataCategory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    // ──────── Foundational ────────

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
        viewModel.onAction(SettingsAction.ThemeRowClick)

        viewModel.onAction(SettingsAction.DismissDialog)

        assertNull(viewModel.uiState.value.dialog)
    }

    // ──────── Theme ────────

    @Test
    fun `theme row opens the theme chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onAction(SettingsAction.ThemeRowClick)

        assertEquals(SettingsDialog.ThemeChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting a different theme writes once and closes the chooser`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.ThemeRowClick)

        viewModel.onAction(SettingsAction.ThemeSelected(ThemeMode.Dark))
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
        viewModel.onAction(SettingsAction.ThemeRowClick)

        viewModel.onAction(SettingsAction.ThemeSelected(UserSettings.DEFAULT.themeMode))
        advanceUntilIdle()

        assertTrue(repository.writes.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a failed theme write emits SettingNotSaved and keeps the old theme`() = runTest {
        val repository = FakeSettingsRepository().apply { failNextWrite = true }
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onAction(SettingsAction.ThemeSelected(ThemeMode.Dark))
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.SettingNotSaved), events)
        assertEquals(UserSettings.DEFAULT.themeMode, viewModel.uiState.value.themeMode)
    }

    // ──────── Search engine ────────

    @Test
    fun `search engine row opens the search engine chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onAction(SettingsAction.SearchEngineRowClick)

        assertEquals(SettingsDialog.SearchEngineChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting a different engine writes once and closes the chooser`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.SearchEngineRowClick)

        viewModel.onAction(SettingsAction.SearchEngineSelected(SearchEngine.DuckDuckGo))
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
        viewModel.onAction(SettingsAction.SearchEngineRowClick)

        viewModel.onAction(SettingsAction.SearchEngineSelected(UserSettings.DEFAULT.searchEngine))
        advanceUntilIdle()

        assertTrue(repository.writes.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a failed engine write emits SettingNotSaved and keeps the old engine`() = runTest {
        val repository = FakeSettingsRepository().apply { failNextWrite = true }
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onAction(SettingsAction.SearchEngineSelected(SearchEngine.Bing))
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.SettingNotSaved), events)
        assertEquals(UserSettings.DEFAULT.searchEngine, viewModel.uiState.value.searchEngine)
    }

    // ──────── App language ────────

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

        // E.g. changed in Android 13+ system settings while the app was away.
        controller.currentLanguage = AppLanguage.Japanese
        viewModel.onAction(SettingsAction.Started)

        assertEquals(AppLanguage.Japanese, viewModel.uiState.value.appLanguage)
    }

    @Test
    fun `language row opens the language chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onAction(SettingsAction.LanguageRowClick)

        assertEquals(SettingsDialog.LanguageChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting the current language makes no platform call`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.German)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.LanguageRowClick)

        viewModel.onAction(SettingsAction.LanguageSelected(AppLanguage.German))
        advanceUntilIdle()

        assertTrue(controller.applied.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `selecting another language applies it once and shows it`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.FollowSystem)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.LanguageRowClick)

        viewModel.onAction(SettingsAction.LanguageSelected(AppLanguage.French))
        advanceUntilIdle()

        assertEquals(listOf(AppLanguage.French), controller.applied)
        assertEquals(AppLanguage.French, viewModel.uiState.value.appLanguage)
        // The chooser stays until the screen reports the language has landed.
        assertEquals(SettingsDialog.LanguageChooser, viewModel.uiState.value.dialog)
        viewModel.onAction(SettingsAction.LanguageApplied)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a rejected language change emits LanguageNotApplied and keeps the old language`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.English, applyResult = false)
        val viewModel = settingsViewModel(appLanguageController = controller)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onAction(SettingsAction.LanguageSelected(AppLanguage.Russian))
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.LanguageNotApplied), events)
        assertEquals(AppLanguage.English, viewModel.uiState.value.appLanguage)
    }

    // ──────── Clear browsing data ────────

    @Test
    fun `the clear dialog opens with the three default categories selected`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onAction(SettingsAction.ClearBrowsingDataRowClick)

        assertEquals(
            SettingsDialog.ClearBrowsingData(
                selection = ClearBrowsingDataCategory.DEFAULT_SELECTION,
                inProgress = false,
            ),
            viewModel.uiState.value.dialog,
        )
    }

    @Test
    fun `toggling a category removes it and adds it back`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.ClearBrowsingDataRowClick)

        viewModel.onAction(SettingsAction.ClearCategoryToggled(ClearBrowsingDataCategory.History))
        assertEquals(
            setOf(ClearBrowsingDataCategory.CookiesAndSiteData, ClearBrowsingDataCategory.CachedImagesAndFiles),
            clearDialog(viewModel).selection,
        )

        viewModel.onAction(SettingsAction.ClearCategoryToggled(ClearBrowsingDataCategory.History))
        assertEquals(ClearBrowsingDataCategory.DEFAULT_SELECTION, clearDialog(viewModel).selection)
    }

    @Test
    fun `confirming with nothing selected clears nothing`() = runTest {
        val log = mutableListOf<String>()
        val viewModel = settingsViewModel(
            historyRepository = FakeHistoryRepository(log),
            webDataCleaner = FakeWebDataCleaner(log),
        )
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.ClearBrowsingDataRowClick)
        ClearBrowsingDataCategory.DEFAULT_SELECTION.forEach { category ->
            viewModel.onAction(SettingsAction.ClearCategoryToggled(category))
        }

        viewModel.onAction(SettingsAction.ClearBrowsingDataConfirmed)
        advanceUntilIdle()

        assertTrue(log.isEmpty())
        assertFalse(clearDialog(viewModel).inProgress)
    }

    @Test
    fun `a running clear ignores a second confirm, a dismiss and a toggle`() = runTest {
        val log = mutableListOf<String>()
        val web = FakeWebDataCleaner(log).apply { siteDataGate = CompletableDeferred() }
        val viewModel = settingsViewModel(historyRepository = FakeHistoryRepository(log), webDataCleaner = web)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.ClearBrowsingDataRowClick)

        viewModel.onAction(SettingsAction.ClearBrowsingDataConfirmed)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.ClearBrowsingDataConfirmed)
        viewModel.onAction(SettingsAction.ClearBrowsingDataDismissed)
        viewModel.onAction(SettingsAction.ClearCategoryToggled(ClearBrowsingDataCategory.History))
        advanceUntilIdle()

        assertTrue(clearDialog(viewModel).inProgress)
        assertEquals(ClearBrowsingDataCategory.DEFAULT_SELECTION, clearDialog(viewModel).selection)
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
        viewModel.onAction(SettingsAction.ClearBrowsingDataRowClick)

        viewModel.onAction(SettingsAction.ClearBrowsingDataConfirmed)
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
        viewModel.onAction(SettingsAction.ClearBrowsingDataRowClick)

        viewModel.onAction(SettingsAction.ClearBrowsingDataConfirmed)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(listOf(SettingsEvent.BrowsingDataPartiallyCleared), events)
    }

    @Test
    fun `dismissing an idle clear dialog closes it`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.ClearBrowsingDataRowClick)

        viewModel.onAction(SettingsAction.ClearBrowsingDataDismissed)

        assertNull(viewModel.uiState.value.dialog)
    }

    // ──────── History retention ────────

    @Test
    fun `retention row opens the retention chooser`() = runTest {
        val viewModel = settingsViewModel()
        advanceUntilIdle()

        viewModel.onAction(SettingsAction.RetentionRowClick)

        assertEquals(SettingsDialog.RetentionChooser, viewModel.uiState.value.dialog)
    }

    @Test
    fun `a shorter window asks for confirmation before changing anything`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.RetentionRowClick)

        viewModel.onAction(SettingsAction.RetentionSelected(HistoryRetention.Days30))
        advanceUntilIdle()

        assertEquals(SettingsDialog.ConfirmShortenRetention(HistoryRetention.Days30), viewModel.uiState.value.dialog)
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a longer window applies at once without confirmation`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.RetentionRowClick)

        viewModel.onAction(SettingsAction.RetentionSelected(HistoryRetention.Days180))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(listOf(FakeSettingsRepository.CALL_SET_HISTORY_RETENTION), repository.writes)
        assertEquals(HistoryRetention.Days180, viewModel.uiState.value.historyRetention)
    }

    @Test
    fun `choosing the current window changes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.RetentionRowClick)

        viewModel.onAction(SettingsAction.RetentionSelected(UserSettings.DEFAULT.historyRetention))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `confirming a shorter window saves it and deletes older history`() = runTest {
        val history = FakeHistoryRepository()
        history.recordVisit("https://old.example.com/", "Old", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60))
        val viewModel = settingsViewModel(historyRepository = history)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.RetentionSelected(HistoryRetention.Days30))

        viewModel.onAction(SettingsAction.ShortenRetentionConfirmed)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(HistoryRetention.Days30, viewModel.uiState.value.historyRetention)
        assertTrue(history.recorded.isEmpty())
    }

    @Test
    fun `cancelling the warning changes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val history = FakeHistoryRepository()
        history.recordVisit("https://old.example.com/", "Old", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60))
        val viewModel = settingsViewModel(settingsRepository = repository, historyRepository = history)
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.RetentionSelected(HistoryRetention.Days7))

        viewModel.onAction(SettingsAction.ShortenRetentionCancelled)
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

        viewModel.onAction(SettingsAction.RetentionSelected(HistoryRetention.Days180))
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

        viewModel.onAction(SettingsAction.RetentionSelected(HistoryRetention.Days180))
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.RetentionPruneDeferred), events)
        assertEquals(HistoryRetention.Days180, viewModel.uiState.value.historyRetention)
    }

    // ──────── Dynamic color ────────

    @Test
    fun `turning swipe navigation off writes once and the state follows`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSwipeNavigationEnabled)

        viewModel.onAction(SettingsAction.SwipeNavigationToggled(false))
        advanceUntilIdle()
        viewModel.onAction(SettingsAction.SwipeNavigationToggled(false))
        advanceUntilIdle()

        assertEquals(listOf(FakeSettingsRepository.CALL_SET_SWIPE_NAVIGATION), repository.writes)
        assertFalse(viewModel.uiState.value.isSwipeNavigationEnabled)
    }

    @Test
    fun `turning dynamic color on writes once`() = runTest {
        // The default is off, so the first meaningful toggle turns it on.
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()

        viewModel.onAction(SettingsAction.DynamicColorToggled(true))
        advanceUntilIdle()

        assertEquals(listOf(FakeSettingsRepository.CALL_SET_DYNAMIC_COLOR), repository.writes)
        assertTrue(viewModel.uiState.value.isDynamicColorEnabled)
    }

    @Test
    fun `toggling dynamic color to its current value writes nothing`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()

        viewModel.onAction(SettingsAction.DynamicColorToggled(UserSettings.DEFAULT.isDynamicColorEnabled))
        advanceUntilIdle()

        assertTrue(repository.writes.isEmpty())
    }

    @Test
    fun `a failed dynamic color write emits SettingNotSaved`() = runTest {
        val repository = FakeSettingsRepository().apply { failNextWrite = true }
        val viewModel = settingsViewModel(settingsRepository = repository)
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        viewModel.onAction(SettingsAction.DynamicColorToggled(true))
        advanceUntilIdle()

        assertEquals(listOf(SettingsEvent.SettingNotSaved), events)
        assertFalse(viewModel.uiState.value.isDynamicColorEnabled)
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
