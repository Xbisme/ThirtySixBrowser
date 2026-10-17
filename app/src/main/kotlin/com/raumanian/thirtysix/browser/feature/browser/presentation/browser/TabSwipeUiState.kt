package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Immutable
import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults

/** The tabs a swipe on the address bar can reach, and whether the gesture is on. */
data class TabSwipeUiState(
    val previous: TabPreview? = null,
    val next: TabPreview? = null,
    val enabled: Boolean = AppDefaults.TAB_SWIPE_ENABLED,
)

/** What the incoming tab shows while it slides in. An incognito tab carries no title or host. */
@Immutable
data class TabPreview(
    val tabId: Long,
    val isIncognito: Boolean,
    val isHome: Boolean,
    val host: String,
    val title: String,
)
