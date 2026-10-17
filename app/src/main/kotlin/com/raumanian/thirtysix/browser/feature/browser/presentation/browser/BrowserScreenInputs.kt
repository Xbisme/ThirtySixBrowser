package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.material3.SnackbarHostState
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.LongPressActions
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PopupTabs
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.SitePermissionGate
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.PageDashboardState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.ProtectionHooks
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions.AddressSuggestionsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The screen's snapshot: the page and its chrome, the tab strip's count and the app-wide text size. */
data class BrowserScreenState(
    val page: BrowserUiState,
    val tabCount: Int,
    val activeTab: Tab?,
    val textZoomPercent: Int,
    val loadedTabId: Long? = null,
    val swipeNavigationEnabled: Boolean = false,
    val tabSwipe: TabSwipeUiState = TabSwipeUiState(),
) {
    /** The active tab's saved back and forward has been read, so its WebView may be built. */
    val isTabReady: Boolean
        get() = activeTab == null || activeTab.id == loadedTabId
}

/** What the browser screen reads besides its state: flows its parts collect, and objects the WebView uses. */
data class BrowserScreenSources(
    val findInPage: StateFlow<FindInPageState>,
    val suggestions: StateFlow<AddressSuggestionsUiState>,
    val hasActiveDownloads: StateFlow<Boolean>,
    val protectionStatus: StateFlow<PageProtectionStatus>,
    val protectionDashboard: StateFlow<PageDashboardState?>,
    val protectionHooks: ProtectionHooks,
    val desktopMode: DesktopModeLookup,
    val savedState: SavedTabStateLookup,
    val popups: PopupTabs,
    val longPressActions: LongPressActions,
    val permissionGate: SitePermissionGate,
    val tabScreenshots: TabScreenshots = TabScreenshots { null },
    val screenshotVersion: StateFlow<Long> = NO_SCREENSHOTS,
)

private val NO_SCREENSHOTS: StateFlow<Long> = MutableStateFlow(0L)

/** Where the browser screen sends input: one entry point per state holder. */
data class BrowserScreenActions(
    val onPageAction: (PageAction) -> Unit,
    val onAddressBarAction: (AddressBarAction) -> Unit,
    val onFindAction: (FindInPageAction) -> Unit,
    val onDownloadAction: (DownloadRequestAction) -> Unit,
    val onPageActionsAction: (PageActionsAction) -> Unit,
    val onTabSessionAction: (TabSessionAction) -> Unit,
    val tools: BrowserToolActions,
)

/** The holders of the screen's tools: the hub, address suggestions and site protection. */
data class BrowserToolActions(
    val onHubAction: (HubAction) -> Unit,
    val onSuggestionsAction: (AddressSuggestionsAction) -> Unit,
    val onProtectionAction: (ProtectionAction) -> Unit,
    val onTabSwipeAction: (TabSwipeAction) -> Unit = {},
)

/** Made once by the route and shared by the screen's parts. */
internal class BrowserScreenHosts(
    val webViewActions: WebViewActionsHandle,
    val snackbarHostState: SnackbarHostState,
    val slots: BrowserSlots,
    /** Read only in the progress line's draw phase, so a tick recomposes nothing. */
    val progress: () -> Float,
)
