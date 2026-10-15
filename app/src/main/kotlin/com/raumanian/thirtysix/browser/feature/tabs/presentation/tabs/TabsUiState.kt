package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import androidx.compose.runtime.Immutable
import com.raumanian.thirtysix.browser.core.domain.model.Tab
/** Tab switcher state; [tabs] is ordered by `lastActiveAt` descending, then `id`. */
@Immutable
data class TabsUiState(
    val tabs: List<Tab> = emptyList(),
    val activeTabId: Long? = null,
    val isCloseAllDialogVisible: Boolean = false,
    /** The Incognito chip and close-all-incognito are hidden while this is 0. */
    val incognitoTabCount: Int = 0,
    val isCloseAllIncognitoDialogVisible: Boolean = false,
    /**
     * Which kind of tab the switcher shows. Starts at the active tab's kind; forced to
     * [TabsMode.Open] when there are no incognito tabs (the Incognito chip is then hidden).
     */
    val mode: TabsMode = TabsMode.Open,
    /** [tabs] filtered to [mode]; the grid renders only these. */
    val visibleTabs: List<Tab> = emptyList(),
) {
    companion object {
        val EMPTY: TabsUiState = TabsUiState()
    }
}

/** The two switcher modes, chosen by the Open / Incognito chips. */
enum class TabsMode { Open, Incognito }
