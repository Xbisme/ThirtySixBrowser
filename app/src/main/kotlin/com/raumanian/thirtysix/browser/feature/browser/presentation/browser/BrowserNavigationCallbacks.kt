package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.graphics.Bitmap

/** URL, session-history, title, icon and scroll callbacks fired by `BrowserWebView`. */
internal data class BrowserNavigationCallbacks(
    val onUrlChange: (String) -> Unit,
    val onCanGoBackChange: (Boolean) -> Unit,
    val onCanGoForwardChange: (Boolean) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onIconReceived: (String, Bitmap) -> Unit,
    val onScreenshotReady: (Bitmap) -> Unit,
    /** Positive when the page moved up (user scrolled down). */
    val onScrollDelta: (Float) -> Unit = {},
)
