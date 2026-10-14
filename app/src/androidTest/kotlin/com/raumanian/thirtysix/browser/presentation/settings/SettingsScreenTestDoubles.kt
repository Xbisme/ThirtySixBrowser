package com.raumanian.thirtysix.browser.presentation.settings

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isSelectable
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.data.local.permission.OneTimeGrantStore
import com.raumanian.thirtysix.browser.data.local.protection.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ClearBrowsingDataUseCase
import com.raumanian.thirtysix.browser.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetDynamicColorEnabledUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetTextSizeUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetThemeModeUseCase
import com.raumanian.thirtysix.browser.presentation.history.InstrumentedFakeHistoryRepository
import com.raumanian.thirtysix.browser.presentation.history.InstrumentedImmediateDispatchers
import com.raumanian.thirtysix.browser.presentation.history.InstrumentedNoopFaviconCacheForHistory
import java.io.File
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Spec 016 T031 — shared in-memory doubles for the Settings Compose UI tests.
 *
 * The tests drive [SettingsViewModel] directly rather than through Hilt, the pattern
 * `HistoryScreenTestDoubles` established: the screen takes its view model as a parameter, so
 * a hand-built instance over in-memory fakes keeps the tests fast and free of the WebView and
 * Room setup the project has documented as flaky. The JVM doubles in `src/test` are not
 * visible here, hence these copies; where History's instrumented doubles already fit, they are
 * reused. Each user story extends this file with what it needs.
 */
internal class InstrumentedFakeSettingsRepository(
    initial: UserSettings = UserSettings.DEFAULT,
) : SettingsRepository {

    private val state: MutableStateFlow<UserSettings> = MutableStateFlow(initial)

    val current: UserSettings get() = state.value

    /** When set, exactly one following write fails without changing state (FR-043). */
    var failNextWrite: Boolean = false

    override fun observeSettings(): Flow<UserSettings> = state.asStateFlow()

    // Spec 018 — the one-shot read. Same snapshot observeSettings would emit.
    override suspend fun currentSettings(): UserSettings = state.value

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = write { copy(themeMode = mode) }

    override suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> =
        write { copy(isDynamicColorEnabled = enabled) }

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> = write { copy(searchEngine = engine) }

    override suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> =
        write { copy(historyRetention = retention) }

    override suspend fun setTextSize(size: com.raumanian.thirtysix.browser.core.domain.model.TextSize) =

        com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> =
        write { copy(isOnboardingCompleted = value) }

    private fun write(transform: UserSettings.() -> UserSettings): Result<Unit> {
        if (failNextWrite) {
            failNextWrite = false
            return Result.Error(IOException("simulated settings write failure"))
        }
        state.value = state.value.transform()
        return Result.Success(Unit)
    }
}

/**
 * Spec 016 T061 — an in-memory [AppLanguageController] that records every request instead of
 * asking the platform, so a test never recreates its own activity.
 */
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

/**
 * Spec 016 T077 — a [WebDataCleaner] that never touches the real web engine. When [gate] is
 * given, the site-data step waits for it, which keeps a clear in progress for as long as a test
 * needs to prove the dialog cannot be dismissed.
 */
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

/** Spec 024 — an empty fireproof list; the Settings screen only ever clears it. */
internal object InstrumentedNoopFireproofSiteRepository : com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository {
    override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.core.domain.model.FireproofSite>())
    override fun currentSites(): Set<String> = emptySet()
    override suspend fun add(site: String) = com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)
    override suspend fun remove(site: String) = com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)
    override suspend fun clearAll() = com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)
}

/** Spec 016 T077 — holds no incognito snapshot; the Settings screen only ever discards. */
internal object InstrumentedNoopCookieJarSnapshotManager : CookieJarSnapshotManager {
    override suspend fun captureSnapshot(origins: List<String>) = Unit
    override suspend fun restoreSnapshot() = Unit
    override fun hasSnapshot(): Boolean = false
    override suspend fun discardSetAsideCookies() = Unit
}

/** Spec 016 T077 — holds no tab previews. */
internal object InstrumentedNoopScreenshotCache : ScreenshotCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(tabId: Long, bitmap: Bitmap) = Unit
    override fun fileFor(tabId: Long): File? = null
    override suspend fun delete(tabId: Long) = Unit
    override suspend fun clearAll() = Unit
}

/** Assembles a [SettingsViewModel] over the doubles above; every parameter has a default. */
@Suppress("LongParameterList")
internal fun settingsViewModel(
    settingsRepository: SettingsRepository = InstrumentedFakeSettingsRepository(),
    appLanguageController: AppLanguageController = InstrumentedFakeAppLanguageController(),
    historyRepository: HistoryRepository = InstrumentedFakeHistoryRepository(),
    webDataCleaner: WebDataCleaner = InstrumentedFakeWebDataCleaner(),
    setThemeMode: SetThemeModeUseCase = SetThemeModeUseCase(settingsRepository),
    setDynamicColorEnabled: SetDynamicColorEnabledUseCase = SetDynamicColorEnabledUseCase(settingsRepository),
    setSearchEngine: SetSearchEngineUseCase = SetSearchEngineUseCase(settingsRepository),
    getAppLanguage: GetAppLanguageUseCase = GetAppLanguageUseCase(appLanguageController),
    setAppLanguage: SetAppLanguageUseCase =
        SetAppLanguageUseCase(appLanguageController, InstrumentedImmediateDispatchers),
    clearBrowsingData: ClearBrowsingDataUseCase = ClearBrowsingDataUseCase(
        historyRepository = historyRepository,
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

internal const val INSTRUMENTED_APP_VERSION_NAME: String = "9.9.9-instrumented"

internal const val SETTINGS_TEST_TIMEOUT_MS: Long = 10_000L

/**
 * Spec 019 — an option row in any Settings chooser. The choosers are drawn with the shared
 * `ChoiceDialog`, whose rows carry no test tag, so an option is matched by what it is: a
 * selectable (radio) node. Nothing else on the Settings screen is selectable.
 */
fun isSettingsChoiceOption(): SemanticsMatcher = isSelectable()

/** Spec 021 — the clear-data dialog's site permissions category, with nothing stored. */
internal object InstrumentedNoopSitePermissionRepository : SitePermissionRepository {
    override fun observeAll(): Flow<List<SitePermissionDecision>> = flowOf(emptyList())

    override suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision> = emptyMap()

    override suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit> =
        Result.Success(Unit)

    override suspend fun removeSite(origin: String): Result<Unit> = Result.Success(Unit)

    override suspend fun clearAll(): Result<Unit> = Result.Success(Unit)
}

/** Spec 023 — the Settings tests never touch sites without protection. */
internal object InstrumentedNoopProtectionExceptionRepository :
    com.raumanian.thirtysix.browser.core.domain.repository.ProtectionExceptionRepository {
    override fun observeAll() =
        kotlinx.coroutines.flow.flowOf(emptyList<com.raumanian.thirtysix.browser.core.domain.model.SiteProtectionException>())

    override fun currentSites(): Set<String> = emptySet()

    override suspend fun add(site: String) = com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)

    override suspend fun remove(site: String) = com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)

    override suspend fun clearAll() = com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)
}
