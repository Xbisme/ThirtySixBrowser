package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/**
 * Imperative calls into the live WebView, wired by its factory, so the view model never holds a
 * WebView. Every action is a no-op until a WebView exists and after it is gone.
 */
internal class WebViewActionsHandle {
    var goBack: () -> Unit = {}
    var goForward: () -> Unit = {}
    var reload: () -> Unit = {}
    var stopLoading: () -> Unit = {}

    var loadUrl: (String) -> Unit = {}

    var findAll: (String) -> Unit = {}

    var findNext: (forward: Boolean) -> Unit = {}

    var clearMatches: () -> Unit = {}

    /** Switches the page mode and reloads the tab's address. */
    var setDesktopMode: (Boolean) -> Unit = {}

    var print: (jobName: String) -> Unit = {}

    /** Called when the renderer is gone: that WebView must not be used again until a replacement re-wires this. */
    fun detach() {
        goBack = {}
        goForward = {}
        reload = {}
        stopLoading = {}
        loadUrl = {}
        findAll = {}
        findNext = {}
        clearMatches = {}
        setDesktopMode = {}
        print = {}
    }
}
