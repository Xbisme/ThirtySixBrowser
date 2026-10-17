package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** Read once when a tab's WebView is built, so it can restore that tab's back and forward. */
fun interface SavedTabStateLookup {
    fun savedState(tabId: Long): ByteArray?
}
