// Spec 016 contract — use-case signatures and behavioural rules.
// Target package: app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/
//
// Bodies are intentionally omitted. Each block states what the implementation MUST guarantee,
// which is what the unit tests in tasks.md are written against.
//
// Kept:     ObserveUserSettingsUseCase, SetThemeModeUseCase, SetSearchEngineUseCase (Spec 006)
// Removed:  SetLanguageOverrideUseCase (FR-017, research R4)
// Amended:  PruneOldHistoryUseCase (Spec 014)
// New:      SetDynamicColorEnabledUseCase, GetAppLanguageUseCase, SetAppLanguageUseCase,
//           ChangeHistoryRetentionUseCase, ClearBrowsingDataUseCase

package com.raumanian.thirtysix.browser.domain.usecase

// ─────────────────────────────────────────────────────────────────────────────
// Dynamic color
// ─────────────────────────────────────────────────────────────────────────────

/** FR-009. Pure delegation to `SettingsRepository.setDynamicColorEnabled`. */
class SetDynamicColorEnabledUseCase /* @Inject constructor(repository: SettingsRepository) */ {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> = TODO()
}

// ─────────────────────────────────────────────────────────────────────────────
// App language — through the platform seam, never through SettingsRepository
// ─────────────────────────────────────────────────────────────────────────────

/** FR-003, FR-016. Reads the platform's current app language via `AppLanguageController`. */
class GetAppLanguageUseCase /* @Inject constructor(controller: AppLanguageController) */ {
    operator fun invoke(): AppLanguage = TODO()
}

/**
 * FR-013 – FR-015.
 *
 *  - Selecting the language already reported by the controller returns `true` without calling
 *    the platform, so nothing is rebuilt and no page reloads (FR-006).
 *  - Otherwise applies the change on the main dispatcher (research R1) and returns whether the
 *    platform accepted it. `false` is surfaced by the view-model as `LanguageNotApplied`.
 *  - Never throws.
 */
class SetAppLanguageUseCase /* @Inject constructor(controller: AppLanguageController, dispatchers: DispatcherProvider) */ {
    suspend operator fun invoke(language: AppLanguage): Boolean = TODO()
}

// ─────────────────────────────────────────────────────────────────────────────
// History retention
// ─────────────────────────────────────────────────────────────────────────────

/** Outcome of a retention change. */
sealed interface HistoryRetentionChangeResult {
    /** The window was saved and history older than it was deleted. */
    data class Applied(val rowsRemoved: Int) : HistoryRetentionChangeResult

    /**
     * The window was saved, but deleting older history failed. The start-up sweep enforces
     * the window on the next launch (FR-024); the screen tells the user so (FR-043).
     */
    data object SavedPruneDeferred : HistoryRetentionChangeResult

    /** The window could not be saved. Nothing was deleted. */
    data object NotSaved : HistoryRetentionChangeResult
}

/**
 * FR-020 – FR-023. Called only after the view-model has obtained the FR-021 confirmation for a
 * shorter window; lengthening needs no confirmation.
 *
 *  1. Write the new window via `SettingsRepository.setHistoryRetention`.
 *     On `Result.Error` → return `NotSaved` and DO NOT prune.
 *  2. Prune via `HistoryRepository.pruneOlderThan(now − retention.days)`.
 *     On failure → return `SavedPruneDeferred`.
 *  3. Otherwise return `Applied(rowsRemoved)`.
 *
 * Write-before-prune is deliberate (data-model §2, research R8): the reverse order could
 * delete history while leaving the old window recorded.
 * Pruning runs on every change; when lengthening it removes nothing extra.
 * A `CancellationException` is rethrown, never mapped to an outcome.
 *
 * @param now injectable clock for tests.
 */
class ChangeHistoryRetentionUseCase /* @Inject constructor(settings: SettingsRepository, history: HistoryRepository) */ {
    suspend operator fun invoke(
        retention: HistoryRetention,
        now: Long = System.currentTimeMillis(),
    ): HistoryRetentionChangeResult = TODO()
}

/**
 * AMENDED (Spec 014) — FR-024. Still runs once per process start from `ThirtySixApplication`.
 *
 * Change: the cutoff is derived from the persisted `UserSettings.historyRetention` (first
 * emission of `SettingsRepository.observeSettings()`) instead of the retired
 * `BrowserLimits.MAX_HISTORY_DAYS`. A failure to read settings propagates to the caller, whose
 * existing failure boundary logs it and retries on the next launch — the sweep never falls
 * back to a hard-coded window.
 */
class PruneOldHistoryUseCase /* @Inject constructor(history: HistoryRepository, settings: SettingsRepository) */ {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): Int = TODO()
}

// ─────────────────────────────────────────────────────────────────────────────
// Clear browsing data
// ─────────────────────────────────────────────────────────────────────────────

/**
 * FR-025 – FR-033. Coordinates several repositories and platform seams, which is exactly the
 * use-case layer's job under Constitution §IV — no repository gains a dependency on another.
 *
 * Dependencies: HistoryRepository, WebDataCleaner, CookieJarSnapshotManager, FaviconCache,
 * ScreenshotCache.
 *
 * Execution, for each category present in [categories], in this fixed order:
 *  - History              → `HistoryRepository.clearAll()`
 *  - CookiesAndSiteData   → `CookieJarSnapshotManager.discardSetAsideCookies()` FIRST (R7),
 *                           then `WebDataCleaner.clearCookiesAndSiteData()` (R5).
 *                           `Failed` → category failed. `Complete` and `Partial` → cleared;
 *                           `Partial` is the documented older-engine limitation (spec A17),
 *                           not a failure.
 *  - CachedImagesAndFiles → `WebDataCleaner.clearWebCache()` — SKIPPED when this same call
 *                           already ran CookiesAndSiteData with outcome `Complete`, because the
 *                           engine emptied the web cache then (spec A16, R6) — followed by
 *                           `FaviconCache.clearAll()` and `ScreenshotCache.clearAll()`.
 *                           Cookies run before cache precisely so that outcome is known.
 *
 * Guarantees:
 *  - Every selected category is attempted even if an earlier one fails (FR-033).
 *  - Within a category, every step is attempted; the category is recorded as failed if ANY
 *    step threw, returned `false`, or returned `SiteDataClearOutcome.Failed`.
 *  - Returns normally in all cases; never throws. A `CancellationException` is rethrown.
 *  - Touches nothing outside the three categories — no tabs, bookmarks, downloads or
 *    settings (FR-031).
 *
 * @param categories non-empty; the dialog disables confirm otherwise (FR-026).
 *                   An empty set is a programming error (`require`).
 */
class ClearBrowsingDataUseCase /* @Inject constructor(...) */ {
    suspend operator fun invoke(categories: Set<ClearBrowsingDataCategory>): ClearBrowsingDataResult = TODO()
}
