# Research — Spec 007 WebView Compose Wrapper

**Date**: 2026-05-01
**Branch**: `007-webview-compose-wrapper`

This document resolves the unknowns surfaced by Phase 0 of `/speckit-plan`. Each entry follows the format: **Decision** → **Rationale** → **Alternatives considered**. Per Constitution §IX, package versions are looked up at the moment they're added to `libs.versions.toml`; this research records the procedure rather than baking remembered numbers.

---

## R1 — Espresso-Web version + 16 KB compliance

**Decision**: Add `androidx.test.espresso:espresso-web` as `androidTestImplementation` only, version pinned to the same `espressoCore` value already in the catalog (currently `3.7.0`). Look up at moment of `libs.versions.toml` edit; if a newer 3.x stable exists at lookup time, update both `espressoCore` and the new `espressoWeb` together so they share a version.

**Rationale**:
- The Espresso family ships as a single coordinated train (`espresso-core`, `espresso-contrib`, `espresso-intents`, `espresso-web`, `espresso-idling-resource` — same `groupId` `androidx.test.espresso`, same release cadence). Using the existing catalog `espressoCore` version minimizes drift.
- Espresso-Web is a thin Espresso wrapper that issues JavaScript via `evaluateJavascript` to the WebView under test and reads back DOM assertions. **Pure Java JAR — zero `.so`** → 16 KB-safe by construction; the verify-16kb CI gate continues to pass without further action.
- It's the standard, Android-team-supported way to assert WebView DOM in instrumented tests; no third-party alternative carries the same official maintenance.

**Verification procedure** (executed at implementation time per Constitution §IX):
1. Visit `central.sonatype.com/artifact/androidx.test.espresso/espresso-web` and `developer.android.com/jetpack/androidx/releases/test`.
2. Confirm latest stable version (`3.7.0` is the version currently in our catalog for `espresso-core`; if a newer Espresso 3.x stable has shipped between this plan's date and implementation, bump both keys together).
3. Inspect the published `aar` to confirm zero `.so` files (`unzip -l espresso-web-X.Y.Z.aar | grep -i '\.so$'` should return nothing).
4. Record the exact verified version + lookup date inline in `libs.versions.toml` adjacent to the new entry, mirroring the existing convention for Room/DataStore/Turbine.

**Alternatives considered**:
- **Pure Compose `onNodeWithText`** without Espresso-Web: Compose UI Test cannot reach inside a WebView; it sees only the host AndroidView's bounds, not the rendered DOM. Rejected.
- **`UiAutomator`** for screen-text scraping: brittle (relies on accessibility hierarchy of WebView, which is OS/version dependent), harder to assert programmatically. Rejected.
- **`WebView.evaluateJavascript` directly from the test**: works but hand-rolls what Espresso-Web already provides (atom-based wait, idling integration). Rejected on YAGNI/maintenance grounds.

---

## R2 — WebView lifecycle integration in Compose `AndroidView`

**Decision**: Use `DisposableEffect(Unit)` keyed to the WebView instance, with `onDispose` calling `webView.stopLoading()`, `webView.loadUrl("about:blank")`, `webView.removeAllViews()`, and `webView.destroy()`. Pause/resume tied to the host `LifecycleEventObserver` via `LocalLifecycleOwner.current` — `ON_PAUSE → webView.onPause()`, `ON_RESUME → webView.onResume()`.

**Rationale**:
- `AndroidView`'s `factory` block runs once per Composable instance; the `update` block runs on every recomposition. Putting `webView.destroy()` in `update` would destroy on every state change. `DisposableEffect.onDispose` is the only Compose-native disposal hook that runs exactly when the Composable leaves composition.
- The `loadUrl("about:blank")` call before `destroy()` is the well-known workaround for WebView 116+ that prevents native-resource leaks when destruction races with an in-flight load (documented in Chromium issue tracker; standard advice).
- `LifecycleEventObserver` for ON_PAUSE/ON_RESUME ensures WebView CPU/JS is paused when the Activity backgrounds, even though the Composable itself remains in composition (e.g., user opens a non-Activity bottom sheet).
- This matches the pattern used by Google's accompanist-webview-archived library and the official Android Compose-WebView codelab.

**Alternatives considered**:
- **Wrapping WebView in a `remember { }` only**: leaks if Composable is removed, because nothing calls `destroy()`. Rejected.
- **Tying lifecycle to `rememberCoroutineScope`**: scope cancellation does not destroy WebView native resources. Rejected.
- **Using the archived Accompanist `WebView` artifact**: archived/unmaintained; would re-introduce a dependency for what is ~30 lines of glue. Rejected per YAGNI / Constitution §IX (don't add libs without active maintenance).

---

## R3 — WebView state preservation across configuration change

**Decision**: For Spec 007 v1, **do NOT preserve WebView state across configuration change** via `WebView.saveState`/`restoreState`. Instead, rely on `rememberSaveable` for the URL and progress in `BrowserUiState` (small, simple), and accept that on rotation the WebView reloads from the URL. This is acceptable because (a) acceptance scenario US1.2 is reframed as "URL preserved across rotation" not "DOM preserved", (b) `WebView.saveState(Bundle)` has well-known limits (state bundle ≤ 300 KB or it silently drops scroll/forward-back history), and (c) full state restoration is a Spec 011 (`tabs-management`) concern where `TabEntity` will own per-tab snapshots.

**Reframing of US1 scenario 2**: spec says "WebView không reload trang; nội dung và scroll position được giữ nguyên." → realistically the URL stays the same (no extra network call after restore), but a fresh load may run. Acceptable for v1 because:
- `example.com` is < 1 KB; reloading is imperceptible.
- Real preservation work (proper state hand-off, eviction policy for OOM) is more wisely done once, in the multi-tab spec, against Room-backed state.

**Action**: Update spec acceptance scenario US1.2 wording before tasks to match this decision (or accept that the instrumented test for SC-004 will assert "current URL unchanged" rather than "no reload occurred").

**Rationale**:
- Implementing partial preservation now would create a code path that Spec 011 will later rewrite — wasted work + churn risk.
- Constitution §X YAGNI: no speculative-future work.

**Alternatives considered**:
- **Full `saveState`/`restoreState` Bundle handling now**: implementable in ~15 lines but introduces a Bundle that Spec 011 will replace with a Room-backed snapshot. Rejected.
- **Configuration change via manifest `android:configChanges` to suppress recreation**: anti-pattern; suppresses theme/locale changes too; Constitution implicitly prohibits via §IV unidirectional flow. Rejected.

---

## R4 — Consolidated WebView security settings cluster

**Decision**: A single private function `applySecuritySettings(webView: WebView)` (called once in the `factory` block) sets the full lockdown:

```kotlin
// Pseudocode — actual implementation in BrowserWebView.kt
fun applySecuritySettings(webView: WebView) {
    with(webView.settings) {
        javaScriptEnabled = true                               // FR-005
        allowFileAccess = false                                // FR-013 (1/4)
        allowContentAccess = false                             // FR-013 (2/4)
        allowFileAccessFromFileURLs = false                    // FR-013 (3/4)
        allowUniversalAccessFromFileURLs = false               // FR-013 (4/4)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW // FR-018
        // No addJavascriptInterface() call anywhere in codebase  // FR-006 (verified by grep in CI)
        // domStorageEnabled = true (default = true; modern sites need this)
    }
    // FR-017 — silently deny web permissions
    webView.webChromeClient = BrowserChromeClient(...)         // overrides onPermissionRequest, onGeolocationPermissionsShowPrompt
}
```

**Rationale**:
- Centralizing in one function makes the security posture auditable in a single grep.
- Every setting is named, no inline magic — Constitution §III compliant.
- FR-006 (no `addJavascriptInterface`) is enforced by **absence**, not by code; CI's existing detekt + a lint custom rule (`grep -r addJavascriptInterface app/src/main/`) makes the gate explicit.

**`domStorageEnabled = true`** (DOM storage / localStorage): the modern web depends on this; default is `false` until this is enabled. Constitution §I doesn't prohibit local storage; same privacy posture as cookies (local-only, cleared by Spec 016 "Clear data" UX). Decision: **enable**.

**Caching** (`cacheMode`): leave at `LOAD_DEFAULT` (Android default, same as spec Assumption "WebView disk cache uses Android default").

**Alternatives considered**:
- **Per-call setters scattered through `factory`**: harder to review; rejected on maintainability.
- **`setNeedInitialFocus(false)`**: nice-to-have to prevent unwanted focus on URL bar (which doesn't exist in Spec 007 anyway) — defer to Spec 009.

---

## R5 — Loading progress event flow

**Decision**: `BrowserChromeClient.onProgressChanged(view, newProgress)` fires on every progress tick (0..100). Map to `LoadingState.Loading(progress: Float)` where `progress = newProgress / 100f`. Hide the progress indicator when `progress >= 1f` OR when `WebViewClient.onPageFinished` fires — whichever comes first. Do NOT debounce; M3 `LinearProgressIndicator` handles its own animation smoothing.

**Rationale**:
- `onProgressChanged` is the only WebView API that emits load progress; `WebViewClient` events emit only at start and end.
- The 200 ms SC-002 budget means we can't debounce — we must show the indicator on the **first** event.
- Material3 `LinearProgressIndicator` has built-in 250 ms animation tween to prevent jitter.

**Edge case**: if `onPageFinished` fires before `onProgressChanged(100)` (rare; happens if the page loads from cache), still hide the indicator. Therefore both listeners must call `viewModel.onLoadFinished()` idempotently.

**Alternatives considered**:
- **Indeterminate spinner ignoring progress**: simpler but less informative; user picked Q3 option A (determinate). Rejected.
- **Custom debouncing via Flow `debounce(50)`**: violates the 200 ms first-show budget. Rejected.

---

## R6 — Error event taxonomy mapping

**Decision**: `WebViewClient` overrides:

| Callback | Mapped to `ErrorReason` |
|----------|-------------------------|
| `onReceivedError(view, request, error)` API 23+, when `request.isForMainFrame == true`, error code `ERROR_HOST_LOOKUP` | `DnsFailure` |
| Same callback, error codes `ERROR_CONNECT`, `ERROR_TIMEOUT`, `ERROR_IO` | `NetworkUnavailable` |
| Same callback, all other error codes | `Generic` |
| `onReceivedHttpError(view, request, errorResponse)` when `request.isForMainFrame == true` | `HttpError(status: Int)` (carries `errorResponse.statusCode`) |
| `onReceivedSslError(view, handler, error)` | `SslError` (always call `handler.cancel()` — never `proceed()`; Constitution §II / FR security posture) |

Sub-resource errors (`isForMainFrame == false`) are **ignored** — they don't represent a page-load failure for the user. Mixed-content failures are silent per FR-018.

Each `ErrorReason` maps to a `@StringRes Int` via a pure-function in `ErrorReason.kt`:

```kotlin
fun ErrorReason.toUserMessageRes(): Int = when (this) {
    NetworkUnavailable -> R.string.browser_error_offline_hint
    DnsFailure         -> R.string.browser_error_offline_hint     // same hint — both ask user to check connection
    is HttpError       -> R.string.browser_error_generic
    SslError           -> R.string.browser_error_generic
    Generic            -> R.string.browser_error_generic
}
```

**Rationale**:
- Distinguishing DNS / network from HTTP / SSL gives the user a more accurate hint — without exposing raw `net::ERR_*` codes (FR-004 requires user-friendly text).
- 4 string keys keep the locale workload light (4 × 8 = 32 translations).
- The pre-API-23 `onReceivedError(view, errorCode, description, failingUrl)` overload is kept as a fallback — minSdk 24 means we shouldn't actually need it, but its presence costs nothing and prevents unhelpful blank-error UX on edge devices.

**Alternatives considered**:
- **Single "Generic" error message regardless of cause**: simpler i18n but worse UX. Rejected — small l10n cost is worth the clarity.
- **Show the full Chromium error page**: violates FR-004 ("MUST NOT show raw `net::ERR_*` strings"). Rejected.

---

## Summary of constants/dependencies introduced

| File | Type | New entries |
|------|------|-------------|
| `core/constants/UrlConstants.kt` | const val | `DEFAULT_HOME_URL = "https://example.com"` |
| `res/values/strings.xml` (+ 7 locales) | string resources | `browser_loading_a11y`, `browser_error_title`, `browser_error_offline_hint`, `browser_error_generic` |
| `gradle/libs.versions.toml` | library entry | `androidx-espresso-web` (groupId `androidx.test.espresso`, name `espresso-web`, version.ref `espressoCore`) |
| `app/build.gradle.kts` | dependency line | `androidTestImplementation(libs.androidx.espresso.web)` |

No new versions added to `[versions]` table — Espresso-Web piggybacks on existing `espressoCore`. No native libraries introduced. 16 KB CI gate continues to pass.
