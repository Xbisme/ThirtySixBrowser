package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.graphics.Bitmap

sealed interface BrowserAction {
    /** What the page's WebView reports. */
    sealed interface Page : BrowserAction {
        data class LoadStarted(val url: String) : Page

        data class ProgressChanged(val newProgress: Int) : Page

        data class LoadFinished(val url: String) : Page

        data class LoadFailed(val reason: ErrorReason) : Page

        data object LoadStopped : Page

        data class UrlChanged(val url: String) : Page

        data class TitleReceived(val title: String) : Page

        data class IconReceived(val url: String, val icon: Bitmap) : Page

        data class ScreenshotReady(val bitmap: Bitmap) : Page

        data class CanGoBackChanged(val canGoBack: Boolean) : Page

        data class CanGoForwardChanged(val canGoForward: Boolean) : Page
    }

    sealed interface AddressBar : BrowserAction {
        data class TextChange(val text: String) : AddressBar

        data class FocusChange(val focused: Boolean) : AddressBar

        data object Submit : AddressBar

        data object Clear : AddressBar
    }

    sealed interface Home : BrowserAction {
        data object Pressed : Home

        data object OverlayDismissed : Home

        data class Submit(val input: String) : Home

        data object NavigateFrom : Home
    }

    sealed interface Download : BrowserAction {
        data class Requested(
            val url: String,
            val userAgent: String,
            val contentDisposition: String?,
            val mimeType: String?,
        ) : Download

        data class StoragePermissionDenied(val permanently: Boolean) : Download
    }

    data object StarTapped : BrowserAction

    data object ToggleDesktopMode : BrowserAction

    data object BackWithoutHistory : BrowserAction

    data object LongPressNewTab : BrowserAction
}
