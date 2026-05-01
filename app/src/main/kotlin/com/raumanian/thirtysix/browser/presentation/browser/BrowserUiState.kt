package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Immutable UI snapshot for the Browser screen.
 *
 * Constructed inside [BrowserViewModel] from the Hilt-injected
 * `@Named("default_home_url")` String — production binding lives in
 * [com.raumanian.thirtysix.browser.di.UrlConfigModule]; instrumented tests swap
 * it via `@TestInstallIn` to drive the offline-error scenario (T036a + T036).
 *
 * No `DEFAULT` companion exists by design: different injected URLs produce
 * different initial states, and a hard-coded const default would obscure that.
 */
data class BrowserUiState(
    val currentUrl: String,
    val loadingState: LoadingState,
)
