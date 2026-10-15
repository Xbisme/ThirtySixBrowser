package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.LongPressActions
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PopupTabs
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.SitePermissionGate
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.PageDashboardState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionHooks
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsUiState
import kotlinx.coroutines.flow.StateFlow

/** What the browser screen reads besides its state: flows its parts collect, and objects the WebView uses. */
@Suppress("LongParameterList")
class BrowserScreenSources(
    val findInPage: StateFlow<FindInPageState>,
    val suggestions: StateFlow<AddressSuggestionsUiState>,
    val hasActiveDownloads: StateFlow<Boolean>,
    val protectionStatus: StateFlow<PageProtectionStatus>,
    val protectionDashboard: StateFlow<PageDashboardState?>,
    val protectionHooks: ProtectionHooks,
    val desktopMode: DesktopModeLookup,
    val popups: PopupTabs,
    val longPressActions: LongPressActions,
    val permissionGate: SitePermissionGate,
)

/** Where the browser screen sends input: one entry point per state holder. */
class BrowserScreenActions(
    val onAction: (BrowserAction) -> Unit,
    val onFindAction: (FindInPageAction) -> Unit,
    val onDownloadAction: (DownloadRequestAction) -> Unit,
    val onPageActionsAction: (PageActionsAction) -> Unit,
    val tools: BrowserToolActions,
)

/** The holders of the screen's tools: the hub, address suggestions and site protection. */
class BrowserToolActions(
    val onHubAction: (HubAction) -> Unit,
    val onSuggestionsAction: (AddressSuggestionsAction) -> Unit,
    val onProtectionAction: (ProtectionAction) -> Unit,
)
