package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** Read once when a tab's WebView is built, so it starts in the right page mode. */
fun interface DesktopModeLookup {
    fun isDesktop(tabId: Long): Boolean
}
