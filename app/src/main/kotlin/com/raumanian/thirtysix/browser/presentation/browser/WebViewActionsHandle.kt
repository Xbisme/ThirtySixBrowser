package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Spec 008 — Compose-side imperative handle to the live `android.webkit.WebView`.
 *
 * Populated inside `BrowserWebView.factory { }` (each lambda field becomes a
 * thin wrapper around the `WebView` instance method). Consumed by:
 *  - `NavigationBottomBar` click handlers (Back / Forward / Reload-or-Stop / Home)
 *  - `PredictiveBackHandler` in `BrowserScreen` (system back gesture commit)
 *
 * Why a separate handle and not a ViewModel method? The ViewModel must NOT hold
 * a reference to `WebView` (Constitution §IV — `domain/` and `presentation/`
 * VMs are framework-light; `WebView` is a heavy Android UI primitive). The
 * handle lives in the Composable scope, captured into the `factory` closure,
 * and read from click handlers — `BrowserViewModel` only sees state-update
 * events flowing IN from the WebView callbacks, never imperative WebView calls.
 *
 * Default lambdas are no-op so the handle is safe to invoke before WebView
 * initialization (initial composition, before `factory` runs) and after the
 * WebView is destroyed.
 *
 * Mutability: each field is a `var` of `() -> Unit`. Reassignment happens
 * exactly once per WebView instantiation (inside `factory`); the lambdas
 * themselves are pure and reference the closure-captured `WebView` instance.
 */
internal class WebViewActionsHandle {
    var goBack: () -> Unit = {}
    var goForward: () -> Unit = {}
    var reload: () -> Unit = {}
    var stopLoading: () -> Unit = {}
    var loadHome: () -> Unit = {}
}
