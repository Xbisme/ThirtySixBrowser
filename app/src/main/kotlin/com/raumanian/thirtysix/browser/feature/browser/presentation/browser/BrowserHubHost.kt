@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.HubCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.HubPageToolsState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.HubSheet
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.PrivacyToolsHosts
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionAction

/** The hub sheet and what it opens: the privacy dashboard, the fire overlay and quick settings. */
@Composable
internal fun BrowserHubHost(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    ui: BrowserScreenUi,
) {
    if (ui.hubOpen) BrowserHubSheet(state, sources, actions, hosts, ui)
    PrivacyToolsHosts(
        dashboardOpen = ui.dashboardOpen,
        isHomeContent = state.page.isHomeContent,
        onDashboardClose = { ui.dashboardOpen = false },
        dashboardFlow = sources.protectionDashboard,
        onProtectionAction = actions.tools.onProtectionAction,
    )
    hosts.slots.fireOverlay()
    if (ui.quickSettingsUsed) {
        hosts.slots.quickSettings(ui.quickSettingRequest, { ui.quickSettingRequest = null }, hosts.snackbarHostState)
    }
}

@Composable
private fun BrowserHubSheet(
    state: BrowserScreenState,
    sources: BrowserScreenSources,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    ui: BrowserScreenUi,
) {
    val page = state.page
    val destinations = hosts.slots.destinations
    val onPageTool = rememberHubPageTools(state, actions, hosts, ui)
    val available = remember(page.isHomeContent, page.loadingState) {
        PageToolsAvailability.of(page.isHomeContent, page.loadingState)
    }
    HubSheet(
        canGoBack = page.canGoBack,
        canGoForward = page.canGoForward,
        onDismiss = { ui.hubOpen = false },
        hasActiveDownloadsFlow = sources.hasActiveDownloads,
        callbacks = HubCallbacks(
            onSettings = destinations.openSettings,
            onBack = { hosts.webViewActions.goBack() },
            onForward = { hosts.webViewActions.goForward() },
            onDownloadsInProgress = destinations.openDownloads,
            onPrivacy = destinations.openSettings,
            onNewIncognitoTab = { actions.tools.onHubAction(HubAction.NewIncognitoTab) },
            onDownloads = destinations.openDownloads,
            onHistory = destinations.openHistory,
            onBookmarks = destinations.openBookmarks,
            onQuickSetting = ui::openQuickSetting,
            onPageTool = onPageTool,
            onProtectionSwitched = { on -> actions.tools.onProtectionAction(ProtectionAction.ProtectionSwitched(on)) },
            onFire = hosts.slots.fireButton.onClick,
            onDashboard = { ui.dashboardOpen = true },
        ),
        pageTools = HubPageToolsState(
            available = available,
            isDesktopMode = page.isDesktopMode,
            textSizePercent = state.textZoomPercent,
        ),
        // Nothing about protection on Home or the incognito start page.
        protectionFlow = if (page.isHomeContent) null else sources.protectionStatus,
    )
}

@Composable
private fun rememberHubPageTools(
    state: BrowserScreenState,
    actions: BrowserScreenActions,
    hosts: BrowserScreenHosts,
    ui: BrowserScreenUi,
): (PageTool) -> Unit {
    val current by rememberUpdatedState(state)
    return rememberPageToolHandler(
        onFindAction = actions.onFindAction,
        chrome = ui.chrome,
        actions = PageToolActions(
            share = rememberShareAction(
                currentUrl = { current.page.currentUrl },
                title = { current.activeTab?.title },
            ),
            toggleDesktopSite = { actions.onPageActionsAction(PageActionsAction.ToggleDesktopMode) },
            // Offered only once the page has loaded.
            print = {
                hosts.webViewActions.print(PrintJobName.of(current.activeTab?.title, current.page.currentUrl))
            },
            openTextSize = { ui.openQuickSetting(QuickSetting.TextSize) },
        ),
    )
}
