# Contract — `BrowserScreen` + `BrowserViewModel`

**Date**: 2026-05-01
**Branch**: `007-webview-compose-wrapper`

Spec 007 has no inter-module / external API surface (no REST endpoints, no public library, no IPC). The only contracts that matter are the **public Composable signature** of `BrowserScreen` (consumed by `AppNavGraph`) and the **state contract** of `BrowserViewModel` (consumed by `BrowserScreen`). Both are recorded here so reviewers and downstream specs (008–016) can rely on a stable surface.

---

## Contract 1 — `BrowserScreen` Composable

```kotlin
package com.raumanian.thirtysix.browser.presentation.browser

@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
)
```

**Caller**: `AppNavGraph` already wires the `AppDestination.Browser` route to a placeholder Composable (Spec 002). That placeholder is replaced by `BrowserScreen()` in this spec. No route argument is added (the URL is sourced from `UrlConstants.DEFAULT_HOME_URL` for v1; URL-as-argument lands in Spec 009).

**Side effects observable to the caller**:

- Issues a network request to `UrlConstants.DEFAULT_HOME_URL` on first composition.
- Persists cookies via the system `CookieManager` (no caller intervention; Constitution §I privacy posture — local only).
- Pauses background network/JS when the host Activity stops; resumes when it starts.
- Releases all native WebView resources on disposal — caller does not need to call any cleanup.

**Forbidden behaviors** (verified by review):

- MUST NOT call `WebView.addJavascriptInterface(...)` anywhere in the call tree (Constitution §I, FR-006).
- MUST NOT request runtime permissions (FR-017 — silent deny only).
- MUST NOT log URLs, search queries, or page content (Constitution §I — no telemetry).
- MUST NOT bypass `WebViewClient.onReceivedSslError(handler.cancel())` — no `proceed()` ever (Constitution §I, research R6).

---

## Contract 2 — `BrowserViewModel` state surface

```kotlin
package com.raumanian.thirtysix.browser.presentation.browser

@HiltViewModel
class BrowserViewModel @Inject constructor(
    // The default URL is Hilt-injected (production binding in UrlConfigModule;
    // instrumented test override in TestUrlConfigModule swaps in an invalid host
    // to drive the offline-error path deterministically — see tasks T014a, T036a).
    @Named("default_home_url") private val defaultHomeUrl: String,
) : ViewModel() {

    private val _uiState: MutableStateFlow<BrowserUiState> =
        MutableStateFlow(BrowserUiState(currentUrl = defaultHomeUrl, loadingState = LoadingState.Idle))

    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    /** Called from BrowserWebViewClient.onPageStarted. */
    fun onLoadStarted(url: String)

    /** Called from BrowserChromeClient.onProgressChanged. */
    fun onProgressChanged(newProgress: Int)  // 0..100; mapped internally to Float 0..1f

    /** Called from BrowserWebViewClient.onPageFinished OR onProgressChanged(100). Idempotent. */
    fun onLoadFinished(url: String)

    /** Called from BrowserWebViewClient.onReceivedError / onReceivedHttpError / onReceivedSslError.
     *  ADDED in US3 phase (tasks T028 + T029 + T030); not present in US1's BrowserViewModel.kt. */
    fun onLoadFailed(reason: ErrorReason)
}
```

### State transition contract

| Transition | Inbound event | Resulting `loadingState` |
|------------|---------------|--------------------------|
| `Idle → Loading(0f)` | `onLoadStarted(url)` first time | `Loading(progress = 0f)`; also updates `currentUrl = url` |
| `Loading(p) → Loading(p')` | `onProgressChanged(newProgress)` where 0 ≤ newProgress < 100 | `Loading(progress = newProgress / 100f)` |
| `Loading(*) → Loaded` | `onLoadFinished(url)` OR `onProgressChanged(100)` | `Loaded`; also updates `currentUrl = url` (in case of redirect) |
| `Loading | Loaded → Failed(reason)` | `onLoadFailed(reason)` (must be `request.isForMainFrame == true`) | `Failed(reason)` |
| `Failed | Loaded → Loading(0f)` | `onLoadStarted(url)` (e.g., reload after Spec 008 ships) | `Loading(progress = 0f)`; updates `currentUrl = url` |

**Idempotency requirements**:

- `onLoadFinished` may fire twice (once from `onProgressChanged(100)`, once from `onPageFinished`). The second call MUST be a no-op (already in `Loaded` state).
- `onLoadFailed` may fire after `onLoadFinished` if a sub-resource SSL error escalates — but FR/R6 specify we ignore sub-resource errors, so this is moot. Still, `onLoadFailed` from sub-frame events MUST be filtered before reaching the ViewModel (filtering happens in `BrowserWebViewClient`).

**Threading**:

- All four entry methods are called from the WebView's Looper thread (UI/main). They MUST NOT do blocking work; their bodies are simply `_uiState.update { ... }` — main-thread-safe.

**Test contract** (unit test `BrowserViewModelTest`):

| Scenario | Setup | Action | Assertion |
|----------|-------|--------|-----------|
| Initial state | new VM constructed with injected URL `"https://example.com"` | — | `uiState.value.currentUrl == "https://example.com"`, `uiState.value.loadingState == Idle` |
| Load happy path | new VM | `onLoadStarted("https://example.com")` → `onProgressChanged(50)` → `onProgressChanged(100)` | Final `loadingState == Loaded`, `currentUrl == "https://example.com"` |
| Load via onPageFinished | new VM | `onLoadStarted(...)` → `onLoadFinished("https://example.com")` (no progress 100 ever fires) | Final `loadingState == Loaded` |
| Network failure | new VM | `onLoadStarted(...)` → `onLoadFailed(NetworkUnavailable)` | Final `loadingState == Failed(NetworkUnavailable)` |
| Idempotent finish | mid-load | `onLoadFinished(...)` twice | Single emission of `Loaded`; second call no-op |
| Progress clamp | mid-load | `onProgressChanged(150)` (defensive) | progress stored as `1f` (clamped); transition to `Loaded` |

---

## Contract 3 — Resources

`BrowserScreen` depends on these `R.string` keys existing in **all 8 locales**:

| Key | EN baseline (other locales translated) |
|-----|----------------------------------------|
| `browser_loading_a11y` | "Loading page" |
| `browser_error_title` | "Page didn't load" |
| `browser_error_offline_hint` | "Check your internet connection and try again." |
| `browser_error_generic` | "Something went wrong. Try opening the page later." |

**Lint enforcement** (Spec 004): `MissingTranslation = error`. The build fails if any locale is missing any key.

---

## Contract 4 — Constants

`BrowserScreen` depends on:

| Constant | Type | Location | Value |
|----------|------|----------|-------|
| `UrlConstants.DEFAULT_HOME_URL` | `const val String` | `core/constants/UrlConstants.kt` (NEW or extended) | `"https://example.com"` |

No new entries in `BrowserLimits`, `AnimationDurations`, `NetworkTimeouts`, or `StorageKeys` are introduced.

---

## Contract 5 — Build / CI

| Artifact | Constraint |
|----------|------------|
| Manifest permissions | EXACTLY 3 — `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`. No additions. |
| `addJavascriptInterface` call sites | ZERO — verified by `grep -r "addJavascriptInterface" app/src/main/` returning no matches. |
| Hardcoded URL literals in `presentation/`/`domain/` | ZERO — verified by `grep -REn '"https?://' app/src/main/kotlin/com/raumanian/thirtysix/browser/{presentation,domain}/` returning no matches. |
| 16 KB native-lib alignment | All entries `align=0x4000`. New deps add zero `.so`. |
| Instrumented test pass | `BrowserScreenInstrumentedTest` passes 100% in CI's `instrumented-test` job (re-enabled in this branch). |
