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
 * to the same 5th `IconButton` via `Modifier.combinedClickable`. Bundle is
 * now 6 fields — exactly at detekt's `LongParameterList.functionThreshold = 6`
 * (PASSES). Any future addition would have to re-bundle into nested groups
 * per Spec 008's precedent.
 */
data class NavigationBottomBarCallbacks(
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onHome: () -> Unit,
    val onBookmarksClick: () -> Unit,
    val onTabsSwitcherClick: () -> Unit,
    val onTabsSwitcherLongClick: () -> Unit,
)
