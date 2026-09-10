package com.raumanian.thirtysix.browser.presentation.browser.components

/**
 * Spec 008 — click callback bundle for [NavigationBottomBar].
 *
 * Mirrors the `BrowserWebViewCallbacks` pattern from Spec 007 to keep the
 * Composable's parameter count under detekt's `LongParameterList.functionThreshold = 6`.
 *
 * Each lambda is invoked exactly once per user tap on the corresponding
 * affordance. The dispatch decision for [onReloadOrStop] (which underlying
 * action runs — `reload()` vs `stopLoading()`) lives at the call site (in
 * `BrowserScreen.kt`), NOT inside `NavigationBottomBar`, so the bar stays
 * purely presentational and the click handler reads the freshest
 * `loadingState` from the `BrowserUiState` snapshot at click time.
 *
 * Spec 011 (Q2 / R6) adds [onTabsSwitcherClick] (single-tap → navigate to
 * the tab switcher route) and [onTabsSwitcherLongClick] (long-press → open
 * a fresh home tab inline without showing the switcher). Both lambdas attach
 * to the same `IconButton` via `Modifier.combinedClickable`.
 *
 * Spec 015 replaces the separate `onBookmarksClick` / `onHistoryClick` fields with a single
 * [onOverflowClick]; those two destinations now live in `BrowserOverflowMenu` alongside
 * Downloads (FR-042). The bundle stands at **7 fields**.
 *
 * > Correcting a stale note that stood here from Spec 011 until Spec 015: it claimed the
 * > bundle was "now 6 fields — exactly at detekt's `functionThreshold = 6` (PASSES). Any
 * > future addition would have to re-bundle into nested groups." Specs 013 and 014 each
 * > appended a field without updating it, so by Spec 015 it read 8 while claiming 6, and
 * > the stated ceiling was never what kept it passing: detekt's `LongParameterList` sets
 * > `ignoreDataClasses: true` (detekt.yml), so a data class is exempt from the rule
 * > entirely. Judge additions on whether the bundle still reads clearly, not on that count.
 */
data class NavigationBottomBarCallbacks(
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onHome: () -> Unit,
    /** Spec 015 FR-042 — opens the overflow menu holding Bookmarks · History · Downloads. */
    val onOverflowClick: () -> Unit,
    val onTabsSwitcherClick: () -> Unit,
    val onTabsSwitcherLongClick: () -> Unit,
)
