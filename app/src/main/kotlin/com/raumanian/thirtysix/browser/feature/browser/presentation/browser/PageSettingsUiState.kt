package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults

data class PageSettingsUiState(
    val textZoomPercent: Int = AppDefaults.TEXT_SIZE.percent,
    val swipeNavigationEnabled: Boolean = AppDefaults.SWIPE_NAVIGATION_ENABLED,
)
