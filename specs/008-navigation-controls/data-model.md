# Data Model: Navigation Controls (Spec 008)

> Spec 008 introduces **no new domain entities, no new database tables, and no new persistent storage.** WebView session history is platform-managed in-memory state; cross-process-death persistence is deferred to Spec 011 (`tabs-management`).
>
> The only data-model change is an additive delta to the existing presentation-layer `BrowserUiState` from Spec 007.

## Entity 1 — `BrowserUiState` (delta over Spec 007)

**Layer**: `presentation/browser/` (NOT `domain/model/` — UiState is presentation-only per Constitution §IV).

**Existing shape (from Spec 007)**:

```kotlin
data class BrowserUiState(
    val currentUrl: String,
    val loadingState: LoadingState,
)
```

**New shape (Spec 008)**:

```kotlin
data class BrowserUiState(
    val currentUrl: String,
    val loadingState: LoadingState,
    val canGoBack: Boolean = false,      // NEW
    val canGoForward: Boolean = false,   // NEW
)
```

### Field semantics

| Field | Type | Default | Source of truth | Update trigger |
|-------|------|---------|-----------------|----------------|
| `canGoBack` | `Boolean` | `false` | `WebView.canGoBack()` | `WebViewClient.doUpdateVisitedHistory(...)` callback (re-read after every history mutation) |
| `canGoForward` | `Boolean` | `false` | `WebView.canGoForward()` | Same — re-read in same callback |

### Invariants

- **Initial state**: At first composition (before any page commits), both `canGoBack` and `canGoForward` are `false` regardless of WebView's actual platform state. This is safe because no Back/Forward action makes sense before the first page commit.
- **Single-tab assumption (v1.0)**: These flags reflect the CURRENT tab's WebView. Multi-tab semantics (e.g., "is there a previous tab to close?") are out of scope and deferred to Spec 011.
- **Failed-load behavior**: When a navigation fails (`LoadingState.Failed(...)`), the flags retain their values from before the failed attempt. The platform may or may not include the failed entry in history — spec accepts either behavior (FR-014 + edge-case bullet 2).
- **Configuration change (rotation)**: The flags are part of `BrowserUiState`, which is held in the `BrowserViewModel`'s `MutableStateFlow`. Rotation does NOT recompute the flags — they survive recreation per Compose ViewModel scoping. Verified by existing Spec 007 rotation test (T021b).

### Why these are NOT in the domain layer

- WebView session history is **platform-managed**, not app-modeled. The app does not own the data structure that represents history; `WebView.copyBackForwardList()` returns a platform-defined `WebBackForwardList`.
- Constitution §IV mandates that `domain/` be pure Kotlin with zero Android imports. A "history" domain model would either (a) duplicate platform state with stale risk, or (b) wrap `WebView` and leak Android into `domain/`. Both fail the principle.
- The presentation-tier `BrowserUiState` is the right home: the two booleans are derived from platform queries on every commit and serve only the UI's enabled/disabled affordance state.

## Entity 2 — `LoadingState` (no change)

The existing sealed class from Spec 007 is reused unchanged:

```kotlin
sealed interface LoadingState {
    data object Idle : LoadingState
    data class Loading(val progress: Float) : LoadingState
    data object Loaded : LoadingState
    data class Failed(val reason: ErrorReason) : LoadingState
}
```

The Reload/Stop affordance reads `loadingState` to decide which semantic to render:
- `Loading(...)` → render Stop
- `Idle | Loaded | Failed(...)` → render Reload

No state addition required.

## Entity 3 — `BrowserWebViewCallbacks` (delta)

**Layer**: `presentation/browser/`

**Existing (Spec 007 — 4-callback bundle to keep `BrowserWebView` parameter count under Detekt's `LongParameterList.functionThreshold = 6`)**:

```kotlin
data class BrowserWebViewCallbacks(
    val onUrlChange: (String) -> Unit,
    val onLoadingStateChange: (LoadingState) -> Unit,
    val onPageStarted: (String) -> Unit,
    val onPageFinished: (String) -> Unit,
)
```

**New (Spec 008 adds 2 fields → 6 total, still ≤ threshold)**:

```kotlin
data class BrowserWebViewCallbacks(
    val onUrlChange: (String) -> Unit,
    val onLoadingStateChange: (LoadingState) -> Unit,
    val onPageStarted: (String) -> Unit,
    val onPageFinished: (String) -> Unit,
    val onCanGoBackChange: (Boolean) -> Unit,       // NEW
    val onCanGoForwardChange: (Boolean) -> Unit,    // NEW
)
```

### Wiring

Inside `BrowserWebView` Composable's `WebViewClient`:

```kotlin
override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
    callbacks.onCanGoBackChange(view.canGoBack())
    callbacks.onCanGoForwardChange(view.canGoForward())
}
```

The two callbacks fan out to `BrowserViewModel.onCanGoBackChanged(...)` / `onCanGoForwardChanged(...)`, which `update { copy(canGoBack = ...) }` the `MutableStateFlow<BrowserUiState>`.

## Constants delta

**File**: `core/constants/AppDefaults.kt`

```kotlin
object AppDefaults {
    // ... existing constants from Spec 006 ...

    /**
     * Default home page URL. Tapping the Home affordance loads this URL in the
     * current tab. Also serves as the initial URL when BrowserScreen first
     * appears (replaces the Spec 007 placeholder "https://example.com").
     *
     * v1.0 is not user-configurable; Spec 016 (Settings) will introduce a
     * user-overridable home URL backed by DataStore.
     */
    const val HOME_URL: String = "https://www.google.com/"
}
```

This single constant replaces the inline literal in `app/.../di/UrlConfigModule.kt`'s `@Provides @Named("default_home_url")` body.

## State transition diagram (informal)

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Initial composition                                                    │
│  BrowserUiState(currentUrl="", loadingState=Idle, canGoBack=false,      │
│                 canGoForward=false)                                      │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     │  user taps Home OR initial-load triggered
                                     ▼
                  loadUrl(AppDefaults.HOME_URL)
                                     │
                                     │  WebView starts loading
                                     ▼
                  loadingState = Loading(progress)
                                     │
                                     │  WebViewClient.doUpdateVisitedHistory fires
                                     ▼
        canGoBack ← view.canGoBack()  /  canGoForward ← view.canGoForward()
                                     │
                                     │  Loaded
                                     ▼
                       loadingState = Loaded
                                     │
       ┌─────────────────────────────┴──────────────────────────────┐
       │                                                            │
       │  User taps a link inside page                              │  User taps Reload
       ▼                                                            ▼
   New page commits                                          loadingState = Loading
   doUpdateVisitedHistory fires                              (no history change)
   canGoBack ← true (typically)
       │
       │  User taps Back affordance OR system back gesture (canGoBack=true)
       ▼
   webView.goBack()
   doUpdateVisitedHistory fires for the previous page
   canGoBack/canGoForward recomputed
```

## Out of scope (deferred)

- Persisting WebView session history across process death (Spec 011).
- Multi-tab back stack semantics (Spec 011 / Spec 012).
- Long-press history dropdown showing all entries (deferred indefinitely).
- Per-history-entry favicon storage (Spec 015 territory if/when bookmarks introduce favicons).
- Hard-reload (cache-bypass) variant (deferred indefinitely).
