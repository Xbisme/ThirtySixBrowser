package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import com.raumanian.thirtysix.browser.core.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetThemeModeUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.testing.fake.FakeAppLanguageController
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFaviconCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeScreenshotCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSettingsRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeWebDataCleaner
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ClearBrowsingDataUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.SetDynamicColorEnabledUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.SetTextSizeUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Every parameter defaults to a fake, so a new constructor parameter changes no existing test. */
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
        sitePermissionRepository = FakeSitePermissionRepository(),
        oneTimeGrants = OneTimeGrantStore(),
        protectionExceptions = FakeProtectionExceptionRepository(),
        incognitoProtectionExceptions = IncognitoProtectionExceptionStore(),
        fireproofSites = com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository(),
    ),
    changeHistoryRetention: ChangeHistoryRetentionUseCase =
        ChangeHistoryRetentionUseCase(settingsRepository, historyRepository),
    supportsDynamicColor: Boolean = true,
    appVersionName: String = TEST_APP_VERSION_NAME,
    isDefaultBrowser: IsDefaultBrowserUseCase = IsDefaultBrowserUseCase { false },
    setTextSize: SetTextSizeUseCase = SetTextSizeUseCase(settingsRepository),
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
    isDefaultBrowser = isDefaultBrowser,
    setTextSize = setTextSize,
)

internal const val TEST_APP_VERSION_NAME: String = "9.9.9-test"

// Every dispatcher is Dispatchers.Main, which the test replaces, so advanceUntilIdle drives use-case hops too.
private object MainDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Main
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Main
}
