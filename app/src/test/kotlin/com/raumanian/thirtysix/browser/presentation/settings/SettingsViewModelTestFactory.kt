package com.raumanian.thirtysix.browser.presentation.settings

import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ClearBrowsingDataUseCase
import com.raumanian.thirtysix.browser.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetDynamicColorEnabledUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetThemeModeUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeAppLanguageController
import com.raumanian.thirtysix.browser.testdoubles.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.testdoubles.FakeFaviconCache
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeScreenshotCache
import com.raumanian.thirtysix.browser.testdoubles.FakeSettingsRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeWebDataCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Spec 016 T030 — the one place a [SettingsViewModel] is assembled for JVM tests.
 *
 * Every parameter defaults to a fake, so a user story that adds a constructor parameter adds
 * one defaulted parameter here and no existing test changes.
 */
@Suppress("LongParameterList")
internal fun settingsViewModel(
    settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    appLanguageController: FakeAppLanguageController = FakeAppLanguageController(),
    historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
    webDataCleaner: FakeWebDataCleaner = FakeWebDataCleaner(),
    setThemeMode: SetThemeModeUseCase = SetThemeModeUseCase(settingsRepository),
    setDynamicColorEnabled: SetDynamicColorEnabledUseCase = SetDynamicColorEnabledUseCase(settingsRepository),
    setSearchEngine: SetSearchEngineUseCase = SetSearchEngineUseCase(settingsRepository),
    getAppLanguage: GetAppLanguageUseCase = GetAppLanguageUseCase(appLanguageController),
    setAppLanguage: SetAppLanguageUseCase = SetAppLanguageUseCase(appLanguageController, MainDispatchers),
    clearBrowsingData: ClearBrowsingDataUseCase = ClearBrowsingDataUseCase(
        historyRepository = historyRepository,
        webDataCleaner = webDataCleaner,
        cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
        faviconCache = FakeFaviconCache(),
        screenshotCache = FakeScreenshotCache(),
    ),
    changeHistoryRetention: ChangeHistoryRetentionUseCase =
        ChangeHistoryRetentionUseCase(settingsRepository, historyRepository),
    supportsDynamicColor: Boolean = true,
    appVersionName: String = TEST_APP_VERSION_NAME,
): SettingsViewModel = SettingsViewModel(
    observeUserSettings = ObserveUserSettingsUseCase(settingsRepository),
    setThemeMode = setThemeMode,
    setDynamicColorEnabled = setDynamicColorEnabled,
    setSearchEngine = setSearchEngine,
    getAppLanguage = getAppLanguage,
    setAppLanguage = setAppLanguage,
    clearBrowsingData = clearBrowsingData,
    changeHistoryRetention = changeHistoryRetention,
    supportsDynamicColor = supportsDynamicColor,
    appVersionName = appVersionName,
)

internal const val TEST_APP_VERSION_NAME: String = "9.9.9-test"

/**
 * Every dispatcher is `Dispatchers.Main`, which `SettingsViewModelTest` replaces with its test
 * dispatcher — so use-case hops run on the same scheduler `advanceUntilIdle` drives.
 */
private object MainDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Main
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Main
}
