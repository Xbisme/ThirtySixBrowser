package com.raumanian.thirtysix.browser.presentation.browser.components

/**
 * Spec 009 — bundle of callbacks the [AddressBar] Composable forwards to its
 * host. Split into its own file (mirrors [NavigationBottomBarCallbacks] from
 * Spec 008) to satisfy detekt `MatchingDeclarationName` and to keep the
 * `AddressBar` parameter count under `LongParameterList.functionThreshold = 6`.
 *
 * The host wires `onSubmit` to a lambda that:
 *  1. invokes `BrowserViewModel.onAddressBarSubmit { url -> ... }`,
 *  2. inside the URL-consumer lambda passed to the ViewModel: dismisses the
 *     keyboard, clears focus, then calls `WebViewActionsHandle.loadUrl(url)` —
 *     in that order, satisfying FR-013a "dismiss before navigation begins".
 *
 * The Composable is unaware of `WebViewActionsHandle` or the IME services;
 * it just fires `onSubmit()` on IME action and lets the host orchestrate the
 * chain.
 */
internal data class AddressBarCallbacks(
    val onTextChange: (String) -> Unit,
    val onFocusChange: (Boolean) -> Unit,
    val onSubmit: () -> Unit,
    val onClear: () -> Unit,
)
