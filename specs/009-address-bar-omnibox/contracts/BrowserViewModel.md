# Contract — `BrowserViewModel` Public Surface (Spec 009 delta)

**Feature**: Address Bar / Omnibox (Spec 009)
**Date**: 2026-05-02
**Existing surface baseline**: Spec 007 + Spec 008 contracts already implemented.

This document specifies the **delta** Spec 009 adds to the public surface of `BrowserViewModel`. Methods and state fields not listed here remain as Spec 008 left them.

## State exposure (unchanged signature)

```kotlin
val uiState: StateFlow<BrowserUiState>
```

The `BrowserUiState` shape gains two new fields per [data-model.md Entity 1](../data-model.md#entity-1--browseruistate-existing--spec-007008--extended-here):

| Field | Type | Default |
|---|---|---|
| `addressBarText` | `String` | `""` |
| `isAddressBarFocused` | `Boolean` | `false` |

## New methods (5 total)

All five methods follow Spec 008's pattern: synchronous, non-suspending, internal-state-only, no I/O. Exception: `onAddressBarSubmit` invokes the imperative `WebViewActionsHandle.loadUrl(...)` for URL/query submission — the same pattern Spec 008 established for `loadHome` etc.

### 1. `fun onAddressBarTextChange(newText: String)`

Update the `addressBarText` field of `BrowserUiState` to the user's in-progress text.

- **Precondition**: none. Called on every keystroke from the `OutlinedTextField` `onValueChange` callback.
- **Postcondition**: `_uiState.update { it.copy(addressBarText = newText) }`.
- **Side effects**: none. No classification, no submit, no I/O.
- **Returns**: `Unit`.
- **Test coverage**: `BrowserViewModelTest.onAddressBarTextChange_updatesState`.

### 2. `fun onAddressBarFocusChange(focused: Boolean)`

Update the `isAddressBarFocused` field. When focus is acquired (`false → true`), the Composable additionally populates `addressBarText` from `currentUrl` — but that copy operation happens in the Composable's `LaunchedEffect`, NOT in this method (the method only stores the focused flag).

- **Precondition**: none.
- **Postcondition**: `_uiState.update { it.copy(isAddressBarFocused = focused) }`.
- **Side effects**: none.
- **Returns**: `Unit`.
- **Test coverage**: `BrowserViewModelTest.onAddressBarFocusChange_togglesFlag`.

### 3. `fun onAddressBarSubmit(): Boolean`

Classify the current `addressBarText`, build the target URL if needed, fire `WebViewActionsHandle.loadUrl(target)`, and return whether a submit actually fired.

- **Precondition**: `WebViewActionsHandle.loadUrl` must be wired by the time the user can submit (i.e., after `BrowserWebView.factory` runs). The default no-op lambda makes pre-attach calls safe.
- **Postcondition**: depends on classification of trimmed `addressBarText`:
  - `Empty` → no state change. Returns `false`.
  - `Url(target)` → `actionsHandle.loadUrl(target)` is called. Returns `true`.
  - `Query(text)` → encode via `URLEncoder.encode(text, "UTF-8")`, format with `String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded)`, call `actionsHandle.loadUrl(searchUrl)`. Returns `true`.
- **Side effects**: at most one `actionsHandle.loadUrl(...)` invocation. The Composable, on receiving `true`, additionally clears focus and dismisses the soft keyboard ([research.md R5](../research.md#r5--auto-unfocus--soft-keyboard-dismiss-on-submit-fr-013a)). The `currentUrl` field is NOT updated synchronously here — it updates via the WebView's `onUrlChange` callback once the load actually starts (Q1 clarification: bar shows submitted target → mirrors via callback).
- **Returns**: `Boolean` — `true` iff a non-empty submit was made.
- **Test coverage**: `BrowserViewModelTest.onAddressBarSubmit_emptyReturnsFalse`, `_urlInputCallsLoadUrlWithScheme`, `_queryInputCallsLoadUrlWithGoogleTemplate`, `_uppercaseSchemeIsPreserved`, `_whitespaceOnlyReturnsFalse`.

### 4. `fun onAddressBarClear()`

Empty the `addressBarText` while preserving focus state. Invoked by the Composable when the user taps the trailing clear button (FR-021).

- **Precondition**: none.
- **Postcondition**: `_uiState.update { it.copy(addressBarText = "") }`.
- **Side effects**: none. Focus and keyboard remain as they were (FR-021 — focus retained, keyboard open).
- **Returns**: `Unit`.
- **Test coverage**: `BrowserViewModelTest.onAddressBarClear_emptiesText_preservesFocus`.

### 5. `fun onUrlChanged(url: String)`

Update the `currentUrl` field of `BrowserUiState` whenever the WebView reports a URL change (live mirror per FR-019 / FR-019a / FR-019b). Wired into the new `BrowserNavigationCallbacks.onUrlChange` from [data-model.md Entity 3](../data-model.md#entity-3--callback-bundle-refactor-browserwebviewcallbacks--new-browsernavigationcallbacks).

- **Precondition**: `url` is non-null; the WebView guarantees this for `onPageStarted` and `doUpdateVisitedHistory`.
- **Postcondition**: `_uiState.update { it.copy(currentUrl = url) }`.
- **Side effects**: none on `addressBarText` or `isAddressBarFocused`. Address bar display logic in the Composable consumes `currentUrl` only when `isAddressBarFocused == false`.
- **Returns**: `Unit`.
- **Test coverage**: `BrowserViewModelTest.onUrlChanged_updatesCurrentUrl`, `_doesNotAffectAddressBarTextOrFocus`.

### Note on `onLoadStarted` (existing — Spec 007)

Spec 007's `onLoadStarted(url: String)` currently also mutates `currentUrl` as a side effect. Spec 009 narrows that responsibility — `onLoadStarted` continues to drive the loading-state machine (`LoadingState.Loading(0f)`) but no longer touches `currentUrl`. The dedicated `onUrlChanged` is the single source of truth for `currentUrl` mutation.

This is a refactor, not a behavior change: `onPageStarted` now fires both `onLoadStarted` and `onUrlChanged`, so the net mutation set is identical for the spec 007 happy path. No existing test should break; `BrowserViewModelTest` gets one new pair of tests asserting the responsibility split.

## Methods unchanged from Spec 008

These remain as Spec 008 specified them. Listed for completeness only:

- `fun onBackClick()`
- `fun onForwardClick()`
- `fun onReloadOrStopClick()`
- `fun onHomeClick()`
- `fun onCanGoBackChanged(canGoBack: Boolean)`
- `fun onCanGoForwardChanged(canGoForward: Boolean)`
- `fun onLoadStopped()`
- `fun homeUrl: String` (getter)
- (Plus the Spec 007 baseline: `onLoadStarted`, `onProgressChanged`, `onLoadFinished`, `onLoadFailed`.)

## Constraints

- **No suspend / Flow on this surface.** All five new methods are synchronous (consistent with Spec 008's pattern).
- **No `WebView` reference inside the ViewModel.** All imperative WebView calls go through `WebViewActionsHandle` (Constitution §IV). The handle is captured in the Composable closure; the ViewModel only holds the handle reference (or a navigation lambda passed at call site, depending on final wiring decided at task-generation time).
- **No I/O.** No coroutines, no repository calls, no network. Spec 010 will introduce a `SearchEngineRepository` and at that point `onAddressBarSubmit` may become `suspend` or use `viewModelScope.launch` to await an injected URL builder. Spec 009's contract is the simpler synchronous form.
- **Thread**: all methods MUST be called from the main thread (Compose convention). The ViewModel does not switch dispatchers.

## Compatibility

- All new fields on `BrowserUiState` have `Boolean = false` / `String = ""` defaults — existing `BrowserUiState(currentUrl, loadingState)` call sites in `BrowserScreenInstrumentedTest` and `BrowserScreenOfflineErrorTest` continue to compile.
- The split of `BrowserWebViewCallbacks` into two data classes is a source-incompatible refactor (the Composable parameter list changes). Affects exactly one production call site (`BrowserScreen.kt`'s `BrowserWebView(...)` invocation) and the existing tests that construct it. Same magnitude of refactor that Spec 008 did with `NavigationBottomBarCallbacks`.

## Summary

| Method | New / Modified | Returns | Side effect |
|---|---|---|---|
| `onAddressBarTextChange(String)` | New | `Unit` | State update only |
| `onAddressBarFocusChange(Boolean)` | New | `Unit` | State update only |
| `onAddressBarSubmit()` | New | `Boolean` | At most one `loadUrl(...)` call |
| `onAddressBarClear()` | New | `Unit` | State update only |
| `onUrlChanged(String)` | New | `Unit` | State update only |
| `onLoadStarted(String)` | Modified (responsibility narrowed) | `Unit` | No longer touches `currentUrl` |
