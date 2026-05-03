@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalFoundationApi::class)

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.R

/**
 * Spec 008 — bottom navigation bar wrapping four affordances:
 * Back · Forward · Reload-or-Stop · Home (left → right; order locked by FR-013
 * after `/speckit-clarify` Q3).
 *
 * Spec 011 (Q2 / R6) adds a 5th button — Tabs switcher with a [BadgedBox]
 * showing the current tab count. Single-tap navigates to the tab switcher
 * route (`AppDestination.Tabs`); long-press opens a fresh home tab inline
 * without showing the switcher. Both gestures attach to the same target via
 * [combinedClickable] (Compose `foundation` API, gated by
 * `@OptIn(ExperimentalFoundationApi::class)`).
 *
 * Always-visible (FR-018, clarified Q1): the bar persists for the lifetime of
 * `BrowserScreen` — no scroll-driven hide, no gesture trigger. Material 3
 * [BottomAppBar] handles tonal elevation, content padding, and gesture-nav
 * window-insets for free.
 *
 * Reload/Stop is a single combined affordance (FR-005): renders Stop semantic
 * during [isLoading], Reload otherwise. The dispatch decision (which underlying
 * action runs) lives in the caller's [NavigationBottomBarCallbacks.onReloadOrStop]
 * lambda — this component is purely presentational about the icon swap.
 *
 * Disabled-state handling (FR-003 + FR-004): Material 3 [IconButton] applies
 * the standard disabled `LocalContentColor` alpha when `enabled = false`, which
 * meets WCAG AA 3:1 contrast on both light + dark themes.
 */
// 6 params: canGoBack, canGoForward, isLoading, tabCount, callbacks, modifier
// — exactly at detekt's `LongParameterList.functionThreshold = 6` ceiling.
// Spec 008's `BrowserWebView` set the precedent for this `@Suppress`. Adding
// a 7th param would require re-bundling per Spec 008's nested-data-class
// pattern; keeping at 6 is intentional.
@Suppress("LongParameterList")
@Composable
fun NavigationBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    tabCount: Int,
    callbacks: NavigationBottomBarCallbacks,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        IconButton(
            onClick = callbacks.onBack,
            enabled = canGoBack,
            modifier = Modifier.testTag(TEST_TAG_NAV_BACK),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.browser_action_back),
            )
        }
        IconButton(
            onClick = callbacks.onForward,
            enabled = canGoForward,
            modifier = Modifier.testTag(TEST_TAG_NAV_FORWARD),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.browser_action_forward),
            )
        }
        ReloadOrStopButton(isLoading = isLoading, onClick = callbacks.onReloadOrStop)
        IconButton(
            onClick = callbacks.onHome,
            modifier = Modifier.testTag(TEST_TAG_NAV_HOME),
        ) {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = stringResource(R.string.browser_action_home),
            )
        }
        TabsSwitcherButton(
            tabCount = tabCount,
            onClick = callbacks.onTabsSwitcherClick,
            onLongClick = callbacks.onTabsSwitcherLongClick,
        )
    }
}

@Composable
private fun ReloadOrStopButton(isLoading: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(TEST_TAG_NAV_RELOAD_STOP),
    ) {
        // Single button slot — icon + content description swap atomically
        // with [isLoading]. Compose recomposition is sub-frame, so SC-006's
        // 100 ms wrong-display lag bound is met by construction.
        if (isLoading) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.browser_action_stop),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.browser_action_reload),
            )
        }
    }
}

/**
 * Spec 011 — 5th button. [combinedClickable] supports both single-tap
 * (navigate to switcher route) and long-press (open a fresh home tab inline)
 * on the same target via separate `onClickLabel` / `onLongClickLabel`
 * a11y descriptions. [BadgedBox] surfaces the live tab count.
 */
@Composable
private fun TabsSwitcherButton(
    tabCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    BadgedBox(
        badge = {
            if (tabCount > 0) {
                Badge { Text(text = tabCount.toString()) }
            }
        },
        modifier = Modifier.testTag(TEST_TAG_NAV_TABS_SWITCHER),
    ) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.browser_action_tabs_switcher),
                    onLongClickLabel = stringResource(R.string.browser_action_new_tab),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
            )
        }
    }
}

// File-top test-tag constants per Constitution §III No-Hardcode Rule and the
// project pattern from `BrowserLoadingIndicator.kt:40` + `BrowserErrorState.kt:69`
// — exposed for instrumented tests; production references via the same const.
const val TEST_TAG_NAV_BACK: String = "nav_back"
const val TEST_TAG_NAV_FORWARD: String = "nav_forward"
const val TEST_TAG_NAV_RELOAD_STOP: String = "nav_reload_stop"
const val TEST_TAG_NAV_HOME: String = "nav_home"
const val TEST_TAG_NAV_TABS_SWITCHER: String = "nav_tabs_switcher"
