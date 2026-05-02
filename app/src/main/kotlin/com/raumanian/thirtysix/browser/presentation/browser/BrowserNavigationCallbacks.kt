package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Spec 009 — URL / session-history state callbacks fired by `BrowserWebView`.
 *
 * Extracted from [BrowserWebViewCallbacks] (Spec 008 had it at exactly 6
 * fields = the detekt `LongParameterList.functionThreshold = 6` ceiling). The
 * `onUrlChange` callback Spec 009 introduces would push the bundle to 7
 * fields, so the responsibility cluster is split:
 *
 *  - [BrowserWebViewCallbacks] keeps the 4 load-lifecycle callbacks.
 *  - [BrowserNavigationCallbacks] (this class) holds the 3 URL/history-state
 *    callbacks: [onUrlChange] (FR-019/19a/19b live mirror), and the two
 *    session-history flags Spec 008 introduced.
 *
 * Same `internal` visibility, same lambda-bundle pattern, same precedent as
 * Spec 008's [com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBarCallbacks]
 * extraction.
 */
internal data class BrowserNavigationCallbacks(
    val onUrlChange: (String) -> Unit,
    val onCanGoBackChange: (Boolean) -> Unit,
    val onCanGoForwardChange: (Boolean) -> Unit,
)
