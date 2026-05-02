# Phase 1 — Data Model: Address Bar / Omnibox

**Feature**: Address Bar / Omnibox (Spec 009)
**Date**: 2026-05-02

Spec 009 adds **no new persistent data**. There is no Room entity, no DataStore key, no domain model promotion, no repository. All new state is transient UI state held inside the existing `BrowserViewModel` and observed by Composables.

This document describes:

1. The two new fields added to the existing `BrowserUiState` shape.
2. The new sealed `AddressBarSubmitResult` shape produced by the input classifier.
3. The callback-bundle refactor required to preserve detekt's `LongParameterList` threshold while adding URL-change reactivity.

## Entity 1 — `BrowserUiState` (existing — Spec 007/008 — extended here)

`BrowserUiState` lives in `presentation/browser/BrowserUiState.kt` and is exposed by `BrowserViewModel` as `StateFlow<BrowserUiState>`. Spec 009 adds two new fields, both transient UI state, both with safe defaults so existing call sites continue to compile.

### Fields after Spec 009

| Field | Type | Source spec | Purpose | Default |
|---|---|---|---|---|
| `currentUrl` | `String` | 007 | URL the WebView is currently on (mirrored from `onPageStarted` + `doUpdateVisitedHistory`). Per Q1 / Q2 clarifications, updated immediately on submit and on every URL transition during a redirect chain. | (no default; constructor-injected from `@Named("default_home_url")`) |
| `loadingState` | `LoadingState` | 007 | Sealed `Idle / Loading(progress) / Loaded / Failed(reason)`. | (no default) |
| `canGoBack` | `Boolean` | 008 | WebView session-history back availability. | `false` |
| `canGoForward` | `Boolean` | 008 | WebView session-history forward availability. | `false` |
| **`addressBarText`** | **`String`** | **009 (new)** | The text the user is currently editing in the address bar; or `""` when the bar is unfocused. Driven by `onAddressBarTextChange`. Survives rotation via the ViewModel scope. | `""` |
| **`isAddressBarFocused`** | **`Boolean`** | **009 (new)** | Whether the address bar currently holds focus. Drives the hostname-vs-full-URL display switch (FR-014/15/18) and the in-screen `BackHandler(enabled = isAddressBarFocused)` (FR-026, R6). | `false` |

### Validation rules

- `addressBarText` MUST be the raw user input — no trim, no scheme normalization, no encoding. Trimming and classification happen only at submit time. This preserves the principle that the user sees exactly what they typed and the field is not "smart".
- `isAddressBarFocused` MUST be a faithful mirror of the `OutlinedTextField`'s `interactionSource` focused state. The Composable owns the source of truth (Compose `FocusManager`); the ViewModel field is a derived projection updated via `onAddressBarFocusChange(Boolean)`.

### State transitions

```
                         user taps bar
                              │
             ┌────────────────▼────────────────┐
             │  isAddressBarFocused = true     │
             │  addressBarText = currentUrl    │   ← LaunchedEffect populates
             │  (selection = full text)        │
             └────────────────┬────────────────┘
                              │
                  ┌───────────┴───────────────┐
                  │                            │
            user types                  user submits
                  │                            │
                  ▼                            ▼
   ┌───────────────────────────┐  ┌─────────────────────────────┐
   │ addressBarText updates    │  │ classifier(addressBarText): │
   │ on every keystroke        │  │  Empty   → no-op             │
   │ (FR-005, FR-008 deferred  │  │  Url(u)  → load(u)           │
   │  to submit)               │  │  Query(q)→ load(searchUrl(q))│
   └───────────────────────────┘  └─────────────┬───────────────┘
                                                │
                                       on non-empty submit
                                                │
                                                ▼
                                ┌──────────────────────────────────┐
                                │ isAddressBarFocused = false      │
                                │ addressBarText = ""              │
                                │ keyboard dismissed               │
                                │ WebView.loadUrl(target) fired    │
                                │ currentUrl updates via onPage-   │
                                │   Started callback (R7)          │
                                └──────────────────────────────────┘
                                                │
                            user taps elsewhere (lose focus)
                                                │
                                                ▼
                                ┌──────────────────────────────────┐
                                │ isAddressBarFocused = false      │
                                │ addressBarText = ""              │
                                │ display falls back to hostname-  │
                                │   only via Uri.parse(currentUrl) │
                                └──────────────────────────────────┘
```

The state transitions are linear and idempotent. There is no race between focus changes and `currentUrl` updates because both are routed through the same `MutableStateFlow.update { }`.

## Entity 2 — `AddressBarSubmitResult` (new sealed shape)

A pure-Kotlin sealed type produced by `AddressBarInputClassifier.classify(raw: String): AddressBarSubmitResult`. Lives in `presentation/browser/AddressBarInputClassifier.kt` next to the classifier function. No Android imports.

### Variants

| Variant | Payload | Triggered by | ViewModel action |
|---|---|---|---|
| `Empty` | (none) | Trimmed input is `""`. | No-op (FR-012). Returns `false` from `onAddressBarSubmit()` so the Composable does NOT dismiss focus/keyboard (FR-013a). |
| `Url(target: String)` | The fully-resolved URL (scheme prepended if missing per FR-009). | Trimmed input matched `UrlPatterns.URL_HEURISTIC_REGEX` (contains `://` or `.`). | Calls `WebViewActionsHandle.loadUrl(target)`. Returns `true`. |
| `Query(text: String)` | The trimmed query text (NOT yet URL-encoded; encoding + template substitution happen in the ViewModel so Spec 010's `SearchEngineRepository` refactor only touches the ViewModel, not the classifier). | Trimmed input did not match the URL heuristic. | ViewModel encodes via `URLEncoder.encode(text, "UTF-8")`, substitutes into `UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE`, calls `WebViewActionsHandle.loadUrl(searchUrl)`. Returns `true`. |

### Why a sealed type and not a String

- Pattern-matching exhaustiveness in the ViewModel — adding a fourth case (e.g., `Bookmark` lookup in a future spec) becomes a compile-time error if any consumer forgets to handle it.
- Clean unit-test surface — `AddressBarInputClassifierTest` asserts `classify(input) == Url("https://example.com")` etc. without leaking URL-construction concerns into the classifier.
- Spec 010 refactor lands cleanly: the `Query` variant is exactly the contract the future `SearchEngineRepository` consumes (`searchUrlFor(text: String): String`).

### No invalid states

- `Url.target` is non-empty by construction (the classifier prepends `https://` if scheme was missing; empty input is `Empty` not `Url("")`).
- `Query.text` is non-empty by construction (trim removes whitespace; whitespace-only is `Empty`).

## Entity 3 — Callback bundle refactor (`BrowserWebViewCallbacks` + new `BrowserNavigationCallbacks`)

### Why this refactor is required

Spec 008 left `BrowserWebViewCallbacks` at **exactly 6 fields** — at the detekt `LongParameterList.functionThreshold = 6` boundary. Adding a 7th callback (the new `onUrlChange` Spec 009 needs for FR-019/19a/19b live URL mirroring) would trigger a violation.

Spec 008 already established the precedent for splitting bundles: `NavigationBottomBarCallbacks` was extracted from `NavigationBottomBar` for the same reason. Spec 009 extends that pattern.

### Refactor

**Existing** (Spec 007 + 008) — `BrowserWebViewCallbacks` (6 fields):

```kotlin
internal data class BrowserWebViewCallbacks(
    val onLoadStarted: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onLoadFinished: (String) -> Unit,
    val onLoadFailed: (ErrorReason) -> Unit,
    val onCanGoBackChange: (Boolean) -> Unit,
    val onCanGoForwardChange: (Boolean) -> Unit,
)
```

**After Spec 009** — split into two cohesive bundles:

```kotlin
// presentation/browser/BrowserWebViewCallbacks.kt
internal data class BrowserWebViewCallbacks(
    val onLoadStarted: (String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
    val onLoadFinished: (String) -> Unit,
    val onLoadFailed: (ErrorReason) -> Unit,
)
// 4 fields — load-lifecycle responsibility cluster

// presentation/browser/BrowserNavigationCallbacks.kt  (NEW)
internal data class BrowserNavigationCallbacks(
    val onUrlChange: (String) -> Unit,
    val onCanGoBackChange: (Boolean) -> Unit,
    val onCanGoForwardChange: (Boolean) -> Unit,
)
// 3 fields — URL / session-history state cluster
```

Both are data classes, both `internal`, both live next to `BrowserWebView.kt`. The `BrowserWebView` Composable receives both bundles as separate parameters (parameter count remains under threshold).

### Wiring inside `BrowserWebView.kt`

- `WebViewClient.onPageStarted(view, url, favicon)` → fires `loadCallbacks.onLoadStarted(url)` AND `navigationCallbacks.onUrlChange(url)` (R7 — first hop of any redirect chain).
- `WebChromeClient.onProgressChanged(view, newProgress)` → fires `loadCallbacks.onProgressChanged(newProgress)`.
- `WebViewClient.onPageFinished(view, url)` → fires `loadCallbacks.onLoadFinished(url)`.
- `WebViewClient.onReceivedError / onReceivedHttpError / onReceivedSslError` → fires `loadCallbacks.onLoadFailed(reason)`.
- `WebViewClient.doUpdateVisitedHistory(view, url, isReload)` → fires `navigationCallbacks.onUrlChange(url)` AND `navigationCallbacks.onCanGoBackChange(view.canGoBack())` AND `navigationCallbacks.onCanGoForwardChange(view.canGoForward())` (extends Spec 008's wiring).

### ViewModel-side handler additions

`BrowserViewModel` gains:

- `fun onUrlChanged(url: String)` — `_uiState.update { it.copy(currentUrl = url) }`. Replaces the implicit `currentUrl` mutation that Spec 007 had inside `onLoadStarted` (which gets refactored to leave `currentUrl` to `onUrlChanged`'s exclusive responsibility — reduces coupling).

(See `contracts/BrowserViewModel.md` for the full surface delta.)

## Entity 4 — `WebViewActionsHandle` (existing — Spec 008 — extended here)

The existing `WebViewActionsHandle` (Spec 008) has 5 imperative lambdas: `goBack`, `goForward`, `reload`, `stopLoading`, `loadHome`. Spec 009 adds **one** new lambda:

```kotlin
internal class WebViewActionsHandle {
    var goBack: () -> Unit = {}
    var goForward: () -> Unit = {}
    var reload: () -> Unit = {}
    var stopLoading: () -> Unit = {}
    var loadHome: () -> Unit = {}
    var loadUrl: (String) -> Unit = {}    // NEW — Spec 009
}
```

`loadUrl` is wired inside `BrowserWebView.factory { webView -> handle.loadUrl = { url -> webView.loadUrl(url) } }` — same pattern as the existing 5 lambdas. Default no-op so it's safe to invoke before WebView attach. Used by `BrowserViewModel.onAddressBarSubmit()`.

The handle is `internal` (Spec 008 design) and stays out of the ViewModel — the Composable closure captures it and forwards calls. Constitution §IV satisfied.

## Persistence

**None.** No Room entity, no DataStore key, no `SavedStateHandle` plumbing. All state is ViewModel-scoped, which means:

- Survives configuration change (rotation, theme switch). ✅ FR-027.
- Does NOT survive process death — but the spec does not require it. Cold-start gives the user a fresh address bar showing only the home page hostname (the home URL is restored from `UrlConfigModule` injection on ViewModel re-creation).

If a future spec wants to persist "last typed but never submitted text" across process death, it can do so via `SavedStateHandle` without touching the data model defined here.

## Migration

**None.** No DB schema change. No DataStore schema change. APK upgrades from Spec 008 → Spec 009 require zero runtime migration code.

## Summary

- **0 new persistent entities.**
- **2 new transient state fields** on existing `BrowserUiState` (`addressBarText`, `isAddressBarFocused`).
- **1 new sealed type** (`AddressBarSubmitResult` with 3 variants).
- **1 new internal data class** (`BrowserNavigationCallbacks`) extracted from the existing `BrowserWebViewCallbacks` to satisfy detekt while adding the URL-change callback.
- **1 new lambda** on existing `WebViewActionsHandle` (`loadUrl`).
- **0 new domain models, 0 new repositories, 0 new use cases** — incremental scope per user preference; Spec 010 will introduce a `SearchEngineRepository` when query handling becomes user-configurable.
