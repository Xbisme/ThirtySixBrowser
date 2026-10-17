package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.FindInPageCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageSession

/** What the browser screen keeps for itself: focus targets, bar heights, the bars' scroll and the open sheets. */
@Stable
internal class BrowserScreenUi(dashboardOpen: MutableState<Boolean>) {
    val addressFocus = FocusRequester()
    val homeSearchFocus = FocusRequester()
    val chrome = ChromeScrollState(maxOffsetPx = 0f)
    val swipe = SwipeNavigationState()
    val tabSwipe = TabSwipeState()

    var topBarHeightPx by mutableIntStateOf(0)
        private set
    var bottomBarHeightPx by mutableIntStateOf(0)
    var hubOpen by mutableStateOf(false)
    var dashboardOpen by dashboardOpen
    var quickSettingRequest by mutableStateOf<QuickSetting?>(null)

    // The quick-settings host stays out of composition until first used.
    var quickSettingsUsed by mutableStateOf(false)
        private set

    /** The bars slide away by exactly the top bar's height, whichever bar is showing. */
    fun onTopBarHeight(px: Int) {
        topBarHeightPx = px
        chrome.maxOffsetPx = px.toFloat()
    }

    fun openQuickSetting(setting: QuickSetting) {
        quickSettingsUsed = true
        quickSettingRequest = setting
    }
}

@Composable
internal fun rememberBrowserScreenUi(): BrowserScreenUi {
    val dashboardOpen = rememberSaveable { mutableStateOf(false) }
    return remember(dashboardOpen) { BrowserScreenUi(dashboardOpen) }
}

/** What the screen's parts share besides the route's inputs. */
internal data class BrowserFrame(
    val ui: BrowserScreenUi,
    val findOpen: Boolean,
    val findCallbacks: FindInPageCallbacks,
    val pageSession: PageSession,
)
