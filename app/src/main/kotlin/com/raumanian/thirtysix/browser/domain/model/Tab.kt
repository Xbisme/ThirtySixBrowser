package com.raumanian.thirtysix.browser.domain.model

/**
 * A single browser tab as exposed to the rest of the app (Spec 011).
 *
 * Pure-Kotlin model — zero Android imports per Constitution §IV. The data layer
 * (`data/local/entity/TabEntity.kt` from Spec 005) is the only place that
 * touches the Room representation; mapping happens in
 * `data/mapper/TabMapper.kt`.
 *
 * @property id Stable identifier across sessions (Room PK auto-increment).
 * @property url Current URL of the tab. Updated on every WebView URL-change
 *               callback via `BrowserViewModel.onUrlChanged` →
 *               `UpdateActiveTabUrlAndTitleUseCase`.
 * @property title Page title. May be empty for freshly-created tabs until
 *                 `WebChromeClient.onReceivedTitle` fires; the display layer
 *                 falls back to the localized "New tab" string in that case.
 * @property position Insertion order. Reserved for future drag-to-reorder
 *                    (FR-034); v1.0 uses it only as a tiebreaker (FR-023).
 * @property createdAt Epoch millis at insert time. Set once.
 * @property lastActiveAt Epoch millis. Source of truth for the active-tab
 *                        pointer (`MAX(last_active_at)` per R1) AND the
 *                        switcher's most-recently-active-first ordering
 *                        (FR-014).
 * @property isIncognito Spec 012 — `true` for in-memory incognito tabs that
 *                       are NEVER persisted to Room. For tabs originating
 *                       from `TabRepository` (Room-backed) this is always
 *                       `false`. For tabs originating from
 *                       `IncognitoTabRepository` (in-memory) this is always
 *                       `true`. Default `false` preserves backwards
 *                       compatibility with all Spec 011 fixtures and tests.
 *                       ID-space invariant per R3: incognito tabs use
 *                       NEGATIVE Long ids (≤ -1) so the merged
 *                       `ObserveAllTabsUseCase` Flow has guaranteed-disjoint
 *                       keys across the two repository sources.
 */
data class Tab(
    val id: Long,
    val url: String,
    val title: String,
    val position: Int,
    val createdAt: Long,
    val lastActiveAt: Long,
    val isIncognito: Boolean = false,
)
