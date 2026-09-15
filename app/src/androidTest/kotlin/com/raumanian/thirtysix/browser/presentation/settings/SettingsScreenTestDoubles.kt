package com.raumanian.thirtysix.browser.presentation.settings

import android.graphics.Bitmap
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.SiteDataClearOutcome
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.domain.repository.WebDataCleaner
import com.raumanian.thirtysix.browser.domain.usecase.ChangeHistoryRetentionUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ClearBrowsingDataUseCase
import com.raumanian.thirtysix.browser.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetDynamicColorEnabledUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSearchEngineUseCase
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

    companion object {
        const val CALL_SITE_DATA: String = "siteData"
        const val CALL_WEB_CACHE: String = "webCache"
    }
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
    ),
    changeHistoryRetention: ChangeHistoryRetentionUseCase =
        ChangeHistoryRetentionUseCase(settingsRepository, historyRepository),
    supportsDynamicColor: Boolean = true,
    appVersionName: String = INSTRUMENTED_APP_VERSION_NAME,
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

internal const val INSTRUMENTED_APP_VERSION_NAME: String = "9.9.9-instrumented"

internal const val SETTINGS_TEST_TIMEOUT_MS: Long = 10_000L
