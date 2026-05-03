package com.raumanian.thirtysix.browser.presentation.browser

import android.graphics.Bitmap

/**
 * Spec 009 — URL / session-history state callbacks fired by `BrowserWebView`.
 *
 * Extracted from [BrowserWebViewCallbacks] (Spec 008 had it at exactly 6
 * fields = the detekt `LongParameterList.functionThreshold = 6` ceiling). The
 * `onUrlChange` callback Spec 009 introduces would push the bundle to 7
 * fields, so the responsibility cluster is split:
 *
 *  - [BrowserWebViewCallbacks] keeps the 4 load-lifecycle callbacks.
 *  - [BrowserNavigationCallbacks] (this class) holds the URL / history /
 *    title / icon state callbacks. Spec 008 added `canGoBack/canGoForward`;
 *    Spec 011 (M1) added [onTitleChange]; Spec 011 favicon amendment
 *    (2026-05-03) adds [onIconReceived]. Bundle is now 5 fields — still
 *    under detekt's `LongParameterList.functionThreshold = 6`.
 *
 * Same `internal` visibility, same lambda-bundle pattern, same precedent as
 * Spec 008's [com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBarCallbacks]
 * extraction.
 */
internal data class BrowserNavigationCallbacks(
    val onUrlChange: (String) -> Unit,
    val onCanGoBackChange: (Boolean) -> Unit,
    val onCanGoForwardChange: (Boolean) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onIconReceived: (String, Bitmap) -> Unit,
    /**
     * Spec 011 Q4 amendment — fired ~200ms after `onPageFinished` with the
     * captured + downscaled WebView bitmap. The handler is responsible for
     * persisting it to [com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache]
     * keyed by the active tab id (NOT URL — URL-keyed would leak files for
     * unbounded URL count; tab-id-keyed is bounded by `MAX_TABS`).
     */
    val onScreenshotReady: (Bitmap) -> Unit,
)
