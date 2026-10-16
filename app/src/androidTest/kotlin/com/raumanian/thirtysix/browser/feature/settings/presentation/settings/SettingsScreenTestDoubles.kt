package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isSelectable
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.core.domain.usecase.ClearBrowsingHistoryUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.NoopTabSessionRepository
import com.raumanian.thirtysix.browser.feature.history.presentation.history.InstrumentedFakeHistoryRepository
import com.raumanian.thirtysix.browser.feature.history.presentation.history.InstrumentedImmediateDispatchers
import com.raumanian.thirtysix.browser.feature.history.presentation.history.InstrumentedNoopFaviconCacheForHistory
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.feature.settings.domain.usecase.ClearBrowsingDataUseCase
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The tests build [SettingsViewModel] by hand instead of through Hilt, avoiding the flaky WebView and
 * Room setup. The JVM doubles in src/test are not visible here, hence these copies.
 */
internal class InstrumentedFakeSettingsRepository(
    initial: UserSettings = UserSettings.DEFAULT,
) : SettingsRepository {

    private val state: MutableStateFlow<UserSettings> = MutableStateFlow(initial)

    val current: UserSettings get() = state.value

    /** When set, exactly one following write fails without changing state. */
    var failNextWrite: Boolean = false

    override fun observeSettings(): Flow<UserSettings> = state.asStateFlow()

    override suspend fun currentSettings(): UserSettings = state.value

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = write { copy(themeMode = mode) }

    override suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> =
        write { copy(isDynamicColorEnabled = enabled) }

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = write { copy(searchEngine = engine) }

    override suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> =
        write { copy(historyRetention = retention) }

    override suspend fun setTextSize(size: TextSize) =

        Result.Success(Unit)

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> =
        write { copy(isOnboardingCompleted = value) }

    override suspend fun setSwipeNavigationEnabled(enabled: Boolean): Result<Unit> =
        write { copy(isSwipeNavigationEnabled = enabled) }

    private fun write(transform: UserSettings.() -> UserSettings): Result<Unit> {
        if (failNextWrite) {
            failNextWrite = false
            return Result.Error(IOException("simulated settings write failure"))
        }
        state.value = state.value.transform()
        return Result.Success(Unit)
    }
}

// Records requests instead of asking the platform, so a test never recreates its own activity.
internal class InstrumentedFakeAppLanguageController(
    @Volatile var currentLanguage: AppLanguage = AppLanguage.FollowSystem,
) : AppLanguageController {

    val applied: MutableList<AppLanguage> = Collections.synchronizedList(mutableListOf())

    override fun current(): AppLanguage = currentLanguage

    override fun apply(language: AppLanguage): Boolean {
        applied += language
        currentLanguage = language
        return true
    }
}

// When [gate] is given, the site-data step waits for it, keeping a clear in progress as long as a test needs.
internal class InstrumentedFakeWebDataCleaner(
    private val gate: CompletableDeferred<Unit>? = null,
) : WebDataCleaner {

    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override suspend fun clearCookiesAndSiteData(): SiteDataClearOutcome {
        calls += CALL_SITE_DATA
        gate?.await()
        return SiteDataClearOutcome.Complete
    }

    override suspend fun clearWebCache(): Boolean {
        calls += CALL_WEB_CACHE
        return true
    }

    override suspend fun storageOrigins(): Set<String> = emptySet()

    override suspend fun clearExcept(
        keepSites: Set<String>,
        candidateHosts: Set<String>,
        isKept: (host: String) -> Boolean,
    ): SiteDataPath = SiteDataPath.PerSite

    companion object {
        const val CALL_SITE_DATA: String = "siteData"
        const val CALL_WEB_CACHE: String = "webCache"
    }
}

internal object InstrumentedNoopFireproofSiteRepository : FireproofSiteRepository {
    override fun observeAll() = flowOf(emptyList<FireproofSite>())
    override fun currentSites(): Set<String> = emptySet()
    override suspend fun add(site: String) = Result.Success(Unit)
    override suspend fun remove(site: String) = Result.Success(Unit)
    override suspend fun clearAll() = Result.Success(Unit)
}

internal object InstrumentedNoopCookieJarSnapshotManager : CookieJarSnapshotManager {
    override suspend fun captureSnapshot(origins: List<String>) = Unit
    override suspend fun restoreSnapshot() = Unit
    override fun hasSnapshot(): Boolean = false
    override suspend fun discardSetAsideCookies() = Unit
}

internal object InstrumentedNoopScreenshotCache : ScreenshotCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
    override fun fileFor(tabId: Long): File? = null
    override suspend fun delete(tabId: Long) = Unit
    override suspend fun clearAll() = Unit
}

@Suppress("LongParameterList")
internal fun settingsViewModel(
    settingsRepository: SettingsRepository = InstrumentedFakeSettingsRepository(),
    appLanguageController: AppLanguageController = InstrumentedFakeAppLanguageController(),
    historyRepository: HistoryRepository = InstrumentedFakeHistoryRepository(),
    webDataCleaner: WebDataCleaner = InstrumentedFakeWebDataCleaner(),
    setAppLanguage: SetAppLanguageUseCase =
        SetAppLanguageUseCase(appLanguageController, InstrumentedImmediateDispatchers),
    clearBrowsingData: ClearBrowsingDataUseCase = ClearBrowsingDataUseCase(
        clearHistory = ClearBrowsingHistoryUseCase(historyRepository, NoopTabSessionRepository),
        webDataCleaner = webDataCleaner,
        cookieJarSnapshotManager = InstrumentedNoopCookieJarSnapshotManager,
        faviconCache = InstrumentedNoopFaviconCacheForHistory,
        screenshotCache = InstrumentedNoopScreenshotCache,
        sitePermissionRepository = InstrumentedNoopSitePermissionRepository,
        oneTimeGrants = OneTimeGrantStore(),
        protectionExceptions = InstrumentedNoopProtectionExceptionRepository,
        incognitoProtectionExceptions = IncognitoProtectionExceptionStore(),
        fireproofSites = InstrumentedNoopFireproofSiteRepository,
    ),
    changeHistoryRetention: ChangeHistoryRetentionUseCase =
        ChangeHistoryRetentionUseCase(settingsRepository, historyRepository),
    supportsDynamicColor: Boolean = true,
    appVersionName: String = INSTRUMENTED_APP_VERSION_NAME,
    defaultBrowser: DefaultBrowserController = DefaultBrowserController { false },
): SettingsViewModel = SettingsViewModel(
    settings = settingsRepository,
    languages = appLanguageController,
    setAppLanguage = setAppLanguage,
    clearBrowsingData = clearBrowsingData,
    changeHistoryRetention = changeHistoryRetention,
    defaultBrowser = defaultBrowser,
    supportsDynamicColor = supportsDynamicColor,
    appVersionName = appVersionName,
)

internal const val INSTRUMENTED_APP_VERSION_NAME: String = "9.9.9-instrumented"

internal const val SETTINGS_TEST_TIMEOUT_MS: Long = 10_000L

// ChoiceDialog rows carry no test tag, so an option is matched as a selectable node; nothing else is selectable.
fun isSettingsChoiceOption(): SemanticsMatcher = isSelectable()

internal object InstrumentedNoopSitePermissionRepository : SitePermissionRepository {
    override fun observeAll(): Flow<List<SitePermissionDecision>> = flowOf(emptyList())

    override suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision> = emptyMap()

    override suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit> =
        Result.Success(Unit)

    override suspend fun removeSite(origin: String): Result<Unit> = Result.Success(Unit)

    override suspend fun clearAll(): Result<Unit> = Result.Success(Unit)
}

internal object InstrumentedNoopProtectionExceptionRepository :
    ProtectionExceptionRepository {
    override fun observeAll() =
        flowOf(emptyList<SiteProtectionException>())

    override fun currentSites(): Set<String> = emptySet()

    override suspend fun add(site: String) = Result.Success(Unit)

    override suspend fun remove(site: String) = Result.Success(Unit)

    override suspend fun clearAll() = Result.Success(Unit)
}
