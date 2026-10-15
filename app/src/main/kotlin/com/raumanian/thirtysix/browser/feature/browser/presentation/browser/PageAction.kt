package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.graphics.Bitmap

/** What the page's WebView reports. */
sealed interface PageAction {
    data class LoadStarted(val url: String) : PageAction

    data class ProgressChanged(val newProgress: Int) : PageAction

    data class LoadFinished(val url: String) : PageAction

    data class LoadFailed(val reason: ErrorReason) : PageAction

    data object LoadStopped : PageAction

    data class UrlChanged(val url: String) : PageAction

    data class TitleReceived(val title: String) : PageAction

    data class IconReceived(val url: String, val icon: Bitmap) : PageAction

    data class ScreenshotReady(val bitmap: Bitmap) : PageAction

    data class CanGoBackChanged(val canGoBack: Boolean) : PageAction

    data class CanGoForwardChanged(val canGoForward: Boolean) : PageAction
}
